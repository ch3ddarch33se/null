package com.ch3ddarch33se.thenull.worldgen;

/** Half-open world bounds for a future structure's solid reservation or clearing.
 * Supply the same intersecting reservations to all chunks. No structures are
 * generated, and the protected roof/surface and bottom bedrock remain immutable.
 */
public record CaveReservation(int minX,int minY,int minZ,int maxX,int maxY,int maxZ,Mode mode) {
    public enum Mode { KEEP_SOLID, CLEAR }
    public CaveReservation {
        if(minX>=maxX||minY>=maxY||minZ>=maxZ||mode==null)throw new IllegalArgumentException("Invalid cave reservation");
    }
    void apply(boolean[] air,int x0,int z0,int nx,int nz) {
        int y0=Math.max(minY,TunnelCarver.MIN_Y),y1=Math.min(maxY,CavernCarver.CEILING+1);
        for(int y=y0;y<y1;y++)for(int z=Math.max(z0,minZ);z<Math.min(z0+nz,maxZ);z++)
            for(int x=Math.max(x0,minX);x<Math.min(x0+nx,maxX);x++)
                air[((y-TunnelCarver.MIN_Y)*nz+z-z0)*nx+x-x0]=mode==Mode.CLEAR;
    }
}
