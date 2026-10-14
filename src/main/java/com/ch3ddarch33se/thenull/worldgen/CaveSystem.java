package com.ch3ddarch33se.thenull.worldgen;

import java.util.List;
import java.util.function.Consumer;

/** Seed-bound generators. Original surface/entrances remain unchanged. Extra
 * paths and caverns operate only below the protected native surface band.
 */
public final class CaveSystem {
    private final TunnelCarver original,dense;
    private final CavernCarver caverns;
    private final CaveSettings settings;
    public CaveSystem(long seed,CaveSettings settings) {
        // Keep the proven full-depth graph so the dense network inherits its
        // deep links as well as the original entrances.
        original=new TunnelCarver(seed);dense=new TunnelCarver(seed,TunnelCarver.Profile.ECOSYSTEM);
        caverns=new CavernCarver(seed,settings);this.settings=settings;
    }
    public CaveSettings settings() { return settings; }
    public boolean[] carve(int x,int z,int nx,int nz) { return carve(x,z,nx,nz,null,null,List.of()); }
    public boolean[] carve(int x,int z,int nx,int nz,Consumer<TunnelCarver.CavePath> paths,
        Consumer<CavernCarver.Room> rooms,List<CaveReservation> reservations) {
        // Five halo blocks supply neighbour voxels for seam-safe local joins.
        int width=nx+10,length=nz+10,plane=width*length,depth=TunnelCarver.MAX_Y-TunnelCarver.MIN_Y+1;
        boolean[] raw=original.carve(x-5,z-5,width,length,paths);
        dense.carveInto(raw,x-5,z-5,width,length,paths);
        caverns.carve(raw,x-5,z-5,width,length,rooms);
        int fw=nx+8,fl=nz+8;
        boolean[] joined=new boolean[fw*fl*depth];
        for(int y=0;y<depth;y++) {
            int worldY=y+TunnelCarver.MIN_Y;
            if(worldY<CavernCarver.FLOOR||worldY>CavernCarver.CEILING) {
                for(int iz=0;iz<fl;iz++) {
                    System.arraycopy(raw,(y*length+iz+1)*width+1,joined,(y*fl+iz)*fw,fw);
                }
                continue;
            }
            for(int iz=0;iz<fl;iz++)for(int ix=0;ix<fw;ix++) {
                int source=(y*length+iz+1)*width+ix+1;
                boolean open=raw[source];
                if(!open) {
                    int h=(raw[source-1]?1:0)+(raw[source+1]?1:0)
                        +(raw[source-width]?1:0)+(raw[source+width]?1:0);
                    int v=(y>0&&raw[source-plane]?1:0)+(y+1<depth&&raw[source+plane]?1:0);
                    // Close short gaps where adjacent connected paths nearly
                    // meet; the extra reach below also joins thin rock webs.
                    open=h>0&&h+v>=2;
                    if(!open&&ix>=1&&ix+2<fw) {
                        if(raw[source-1])open=raw[source+2]||raw[source+3];
                        if(!open&&raw[source+1])open=raw[source-2]||raw[source-3];
                        if(!open&&raw[source-2])open=raw[source+2];
                    }
                    if(!open&&iz>=1&&iz+2<fl) {
                        if(raw[source-width])open=raw[source+2*width]||raw[source+3*width];
                        if(!open&&raw[source+width])open=raw[source-2*width]||raw[source-3*width];
                        if(!open&&raw[source-2*width])open=raw[source+2*width];
                    }
                    if(!open&&y>=2&&y+2<depth) {
                        if(raw[source-plane])open=raw[source+2*plane]||raw[source+3*plane];
                        if(!open&&raw[source+plane])open=raw[source-2*plane]||raw[source-3*plane];
                        if(!open&&raw[source-2*plane])open=raw[source+2*plane];
                    }
                }
                joined[(y*fl+iz)*fw+ix]=open;
            }
        }
        boolean[] air=new boolean[nx*nz*depth];
        for(int y=0;y<depth;y++)for(int iz=0;iz<nz;iz++)
            System.arraycopy(joined,(y*fl+iz+4)*fw+4,air,(y*nz+iz)*nx,nx);
        for(var reservation:reservations)reservation.apply(air,x,z,nx,nz);
        return air;
    }

}
