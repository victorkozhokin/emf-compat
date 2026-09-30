"""Export only Atlas's region into an independent world; never copy old test rigs."""
import io
import json
import shutil
import struct
import zlib
from pathlib import Path
import nbtlib as n

src = Path('run/mctest/Test/saves/ParCool Test')
out = Path('build/atlas/EMF ATLAS - Animation Campus')
out.mkdir(parents=True, exist_ok=True)
for folder in ['region','entities','poi']:
    (out/folder).mkdir(exist_ok=True)
    p = src/folder/'r.4.4.mca'
    if p.exists(): shutil.copy2(p,out/folder/p.name)
shutil.copytree(src/'datapacks/emf_atlas',out/'datapacks/emf_atlas',dirs_exist_ok=True)
f=n.load(src/'level.dat');d=f['Data']
d['LevelName']=n.String('EMF ATLAS | Animation Campus')
d['SpawnX']=n.Int(2144);d['SpawnY']=n.Int(151);d['SpawnZ']=n.Int(2237);d['SpawnAngle']=n.Float(180)
d['GameType']=n.Int(1);d['allowCommands']=n.Byte(1);d['Time']=n.Long(6000);d['DayTime']=n.Long(6000)
d['GameRules']['spawnChunkRadius']=n.String('2')
d['WorldGenSettings']['generate_features']=n.Byte(0)
d['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['settings']['structure_overrides']=n.List[n.String]([])
# The saved owner joins at the hub; other players use world spawn.
p=d.get('Player')
if p is not None:
    p['Pos']=n.List[n.Double]([2144.5,151,2237.5]);p['Rotation']=n.List[n.Float]([180,0]);p['Motion']=n.List[n.Double]([0,0,0])
    p['playerGameType']=n.Int(1);p['Health']=n.Float(20);p['foodLevel']=n.Int(20);p['FallDistance']=n.Float(0)
    p['abilities']['flying']=n.Byte(0);p['abilities']['mayfly']=n.Byte(1);p['abilities']['invulnerable']=n.Byte(1)
    p['Inventory']=n.List[n.Compound]([])
    for key in ['RootVehicle','ShoulderEntityLeft','ShoulderEntityRight','active_effects','ActiveEffects']:
        p.pop(key,None)
f.save(out/'level.dat')
# Read region directly to check persisted inventories and key fixtures, not just command delivery.
def chunks(path):
    raw=path.read_bytes()
    for i in range(1024):
        offset=int.from_bytes(raw[i*4:i*4+3],'big')*4096
        if not offset:continue
        size=struct.unpack('>I',raw[offset:offset+4])[0]
        if raw[offset+4]!=2:raise ValueError('Unexpected chunk compression')
        yield n.File.parse(io.BytesIO(zlib.decompress(raw[offset+5:offset+4+size])))
bs={}
for c in chunks(out/'region/r.4.4.mca'):
    for b in c.get('block_entities',[]):bs[tuple(int(b[k]) for k in ('x','y','z'))]=b
checks={
 'guide_book': 'Book' in bs[(2144,151,2235)],
 'teleport_commands': str(bs[(2114,151,2241)].get('Command','')).startswith('tp @p'),
 'valve': (2065,152,2131) in bs,
 'steering': (2089,151,2131) in bs,
 'chain_conveyor': (2065,156,2143) in bs,
 'mining_reset': str(bs[(2152,151,2184)].get('Command',''))=='function emf_atlas:mining',
}
expected={}
for fun in ['build','detail']:
    for line in (out/f'datapacks/emf_atlas/data/emf_atlas/function/{fun}.mcfunction').read_text().splitlines():
        if line.startswith('item replace block '):
            a=line.split();pos=tuple(map(int,a[3:6]));slot=int(a[6].split('.')[1]);item=a[8].split('[')[0]
            expected[pos,slot]=item if ':' in item else 'minecraft:'+item
for (pos,slot),item in expected.items():
    actual=next((str(a['id']) for a in bs.get(pos,{}).get('Items',[]) if int(a['Slot'])==slot),None)
    checks[f'item:{pos}:{slot}']=actual==item
report={'checks':checks,'block_entities':len(bs),'inventory_slots_checked':len(expected)}
Path('build/atlas/validation.json').write_text(json.dumps(report,indent=2))
print('Checks:',sum(checks.values()),'/',len(checks),'block entities:',len(bs))
for k,v in checks.items():
    if not v:print('FAILED',k)
if not all(checks.values()):raise SystemExit(1)
print(out)
