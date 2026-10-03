package strm.emfcompat.animationadditions.blockuse;


/** Explicit tabletop geometry in block coordinates; adapters opt in, no registry or world scan. */
public record TableSurface(double minX,double maxX,double minZ,double maxZ,double height,double inset) {
    public TableSurface {
        if(maxX-minX<=2*inset || maxZ-minZ<=2*inset || inset<0)
            throw new IllegalArgumentException("Tabletop has no safe hand area");
    }
    public record Point(double x,double y,double z) {}
    /** Nearest edge, with both palms inside the tabletop even at diagonal approaches. */
    public Point contact(double playerX,double playerZ,boolean right) {
        double x=(minX+maxX)/2,z=(minZ+maxZ)/2;
        double dx=playerX-x,dz=playerZ-z;
        double length=Math.hypot(dx,dz);
        if(length<1e-6){dx=0;dz=1;length=1;}
        dx/=length;dz/=length;
        double sign=right?1:-1;
        double reach=Math.min((maxX-minX)/2-inset,(maxZ-minZ)/2-inset);
        x+=dx*reach+dz*sign*(maxX-minX)*.3125;
        z+=dz*reach-dx*sign*(maxZ-minZ)*.3125;
        return new Point(Math.max(minX+inset,Math.min(maxX-inset,x)),height,
                        Math.max(minZ+inset,Math.min(maxZ-inset,z)));
    }
}
