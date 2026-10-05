"""Publish one generated annex's chunks from a stopped sandbox into a closed Atlas world.

Requires nbtlib (uv run --offline --with nbtlib python ... SOURCE DESTINATION).
Preserves unrelated region records, original entities, player data and level.dat.
"""
import datetime
import hashlib
import io
import json
import math
from pathlib import Path
import struct
import subprocess
import sys
import zlib
import nbtlib as n

SECTION = sys.argv[3] if len(sys.argv) > 3 else "terrain"
if SECTION not in {"terrain", "wallhand"}: raise ValueError("Unknown Atlas section")

def records(path):
    raw=path.read_bytes() if path.exists() else bytes(8192)
    rows={}
    for i in range(1024):
        offset=int.from_bytes(raw[i*4:i*4+3],"big")*4096
        if not offset: continue
        size=int.from_bytes(raw[offset:offset+4],"big")
        record=raw[offset:offset+4+size]
        if len(record)!=4+size or record[4]&128: raise ValueError("Invalid/external region record")
        rows[i]=(record,raw[4096+i*4:4100+i*4])
    return rows

def encode(rows):
    header=bytearray(8192);body=bytearray();offset=2
    for i,(record,timestamp) in sorted(rows.items()):
        sectors=math.ceil(len(record)/4096)
        if sectors>255: raise ValueError("Chunk too large")
        header[i*4:i*4+4]=offset.to_bytes(3,"big")+bytes([sectors])
        header[4096+i*4:4100+i*4]=timestamp
        body+=record+bytes(sectors*4096-len(record));offset+=sectors
    return bytes(header+body)

def decode(record):
    data=record[5:]
    if record[4]==2: data=zlib.decompress(data)
    elif record[4]==1:
        import gzip
        data=gzip.decompress(data)
    elif record[4]!=3: raise ValueError("Unsupported compression")
    return n.File.parse(io.BytesIO(data))

def compressed(file):
    stream=io.BytesIO();file.write(stream)
    body=b'\x02'+zlib.compress(stream.getvalue())
    return struct.pack('>I',len(body))+body

def ours(entity): return "atlas_"+SECTION in [str(tag) for tag in entity.get("Tags",[])]

def publish(source,destination):
    if source.resolve()==destination.resolve(): raise ValueError("Separate sandbox required")
    if not (destination/'level.dat').exists(): raise ValueError("Destination world missing")
    open_files=subprocess.run(['lsof','+D',str(destination)],capture_output=True,text=True)
    if open_files.returncode==0 or open_files.stdout.strip():
        raise RuntimeError("Original Atlas is open; do not publish while Minecraft uses it")
    if open_files.returncode!=1 or open_files.stderr.strip():
        raise RuntimeError("Could not verify that original Atlas is closed: "+open_files.stderr)
    function=Path(f'datapacks/emf_atlas/data/emf_atlas/function/{SECTION}.mcfunction')
    script=(source/function).read_text()
    chunks=set()
    for line in script.splitlines():
        a=line.split()
        if not a: continue
        if a[0]=='fill':
            x,z,X,Z=map(int,[a[1],a[3],a[4],a[6]])
        elif a[0]=='setblock':
            x,z=map(int,[a[1],a[3]]);X,Z=x,z
        elif a[:2]==['summon','text_display']:
            x,z=map(lambda v:math.floor(float(v)),[a[2],a[4]]);X,Z=x,z
        else: continue
        for cx in range(min(x,X)//16,max(x,X)//16+1):
            for cz in range(min(z,Z)//16,max(z,Z)//16+1): chunks.add((cx,cz))
    updates={};entity_count=0
    for rx,rz in sorted({(x//32,z//32) for x,z in chunks}):
        indices={(x%32)+(z%32)*32 for x,z in chunks if (x//32,z//32)==(rx,rz)}
        for folder in ['region','entities','poi']:
            path=Path(folder)/f'r.{rx}.{rz}.mca'
            original=records(destination/path);incoming=records(source/path)
            for i in indices:
                if folder=='entities':
                    old=decode(original[i][0]) if i in original else None
                    new=decode(incoming[i][0]) if i in incoming else None
                    added=[e for e in new.get('Entities',[]) if ours(e)] if new is not None else []
                    if old is None and not added: continue
                    result=old if old is not None else new
                    result['Entities']=n.List[n.Compound]([e for e in result.get('Entities',[]) if not ours(e)]+added)
                    entity_count+=len(added)
                    original[i]=(compressed(result),incoming.get(i,original.get(i))[1])
                elif i in incoming: original[i]=incoming[i]
            if original: updates[path]=encode(original)
    updates[function]=script.encode()
    # Snapshot originals before any replacement and detect changes made while preparing.
    previous={p:(destination/p).read_bytes() if (destination/p).exists() else None for p in updates}
    backup=Path('build/atlas/backups')/datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    backup.mkdir(parents=True)
    for path,data in previous.items():
        if data is None: continue
        target=backup/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    for path,data in previous.items():
        current=(destination/path).read_bytes() if (destination/path).exists() else None
        if current!=data: raise RuntimeError('Original changed during preparation: '+str(path))
    for path,data in updates.items():
        target=destination/path;target.parent.mkdir(parents=True,exist_ok=True)
        staging=target.with_suffix(target.suffix+f'.{SECTION}-tmp');staging.write_bytes(data);staging.replace(target)
        if hashlib.sha256(target.read_bytes()).digest()!=hashlib.sha256(data).digest():
            raise RuntimeError('Write verification failed: '+str(target))
    report={'world':str(destination),'chunks':sorted(chunks),'labels_added':entity_count,
            'files':[str(p) for p in updates],'backup':str(backup),'untouched':['level.dat','playerdata','unrelated region records','existing entities']}
    Path(f'build/atlas/{SECTION}-publish.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))

if __name__=='__main__': publish(Path(sys.argv[1]),Path(sys.argv[2]))
