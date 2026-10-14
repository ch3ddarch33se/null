package com.ch3ddarch33se.thenull.worldgen;

import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Connected graph-node caverns with rotated ellipsoids, asymmetric lobes,
 * regional size variation and persistent pillar columns. All queries use
 * world coordinates, with no neighbour mutation or shared mutable cache.
 */
public final class CavernCarver {
    public static final int CELL = 32, LAYERS = 12, CEILING = -369, FLOOR = -748;
    private final long seed;
    private final CaveSettings settings;
    private final Noise regions, pillars;

    public CavernCarver(long seed, CaveSettings settings) {
        this.seed = seed; this.settings = settings;
        regions = noise(seed, 6302, -8, 2);
        pillars = noise(seed, 6303, -3, 2);
    }

    private static Noise noise(long seed, int salt, int octave, int count) {
        return NormalNoise.builder().setBaseOctave(octave).setOctaveCount(count).setBaseAmplitude(.8)
            .build().create(new XoroshiroRandomSource(SeedHash.hash(seed, salt, salt, 721)));
    }

    public void carve(boolean[] air, int x0, int z0, int nx, int nz, Consumer<Room> observer) {
        boolean[] column = new boolean[nx*nz];
        for (int z=0;z<nz;z++) for(int x=0;x<nx;x++) {
            column[z*nx+x] = pillars.get((x0+x)*.7,(z0+z)*.7)*2.5 > 1.3;
        }
        int reach=(int)Math.ceil(55*1.05*settings.cavernScale()*1.18*1.4/CELL);
        for(int cz=Math.floorDiv(z0,CELL)-reach;cz<=Math.floorDiv(z0+nz-1,CELL)+reach;cz++) {
            for(int cx=Math.floorDiv(x0,CELL)-reach;cx<=Math.floorDiv(x0+nx-1,CELL)+reach;cx++) {
                double regional = Math.clamp(regions.get(cx*(double)CELL,cz*(double)CELL)*2.5,-1,1);
                for(int layer=0;layer<LAYERS;layer++) {
                    Room room=room(cx,cz,layer,regional);
                    if(!room.overlaps(x0,z0,nx,nz))continue;
                    if(observer!=null)observer.accept(room);
                    stamp(air,column,room,x0,z0,nx,nz);
                    // Secondary off-centre lobes create asymmetric chambers.
                    stamp(air,column,room.lobe(),x0,z0,nx,nz);
                }
            }
        }
    }

    private Room room(int cx,int cz,int layer,double regional) {
        Random random=new Random(SeedHash.hash(seed,cx,cz,layer+6100));
        double choice=random.nextDouble();
        int kind=choice<.25 ? 0 : choice<.985 ? 1 : 2;
        double minimum=kind==0?10:kind==1?25:60,maximum=kind==0?25:kind==1?60:120;
        double diameter=kind==0 ? random.nextDouble(10,25) : kind==1 ? random.nextDouble(25,60) : random.nextDouble(60,120);
        diameter=Math.clamp(diameter*settings.cavernScale()*(1+.15*regional),minimum,maximum);
        double rx=diameter*.5,rz=rx*random.nextDouble(.75,1.0);
        double ry=rx*(kind==2 ? random.nextDouble(.6,.95) : random.nextDouble(.6,.85));
        // Match the ecosystem tunnel nodes, attaching every chamber to a path.
        double x=cx*(double)CELL+16+14*SeedHash.signed(SeedHash.hash(seed,cx,cz,layer*7+100));
        double y=-398-30*layer+10*SeedHash.signed(SeedHash.hash(seed,cx,cz,layer*7+101));
        double z=cz*(double)CELL+16+14*SeedHash.signed(SeedHash.hash(seed,cx,cz,layer*7+102));
        double angle=random.nextDouble(Math.PI*2);
        return new Room(x,y,z,rx,ry,rz,Math.cos(angle),Math.sin(angle),kind,
            random.nextDouble(-.25,.25),random.nextDouble(-.25,.25));
    }

    private static void stamp(boolean[] air,boolean[] pillar,Room room,int x0,int z0,int nx,int nz) {
        double bound=Math.max(room.rx,room.rz)*1.15;
        int minX=Math.max(x0,(int)Math.floor(room.x-bound)),maxX=Math.min(x0+nx-1,(int)Math.ceil(room.x+bound));
        int minZ=Math.max(z0,(int)Math.floor(room.z-bound)),maxZ=Math.min(z0+nz-1,(int)Math.ceil(room.z+bound));
        int minY=Math.max(FLOOR,(int)Math.floor(room.y-room.ry*1.15)),maxY=Math.min(CEILING,(int)Math.ceil(room.y+room.ry*1.15));
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            int column=(z-z0)*nx+x-x0;
            if(pillar[column])continue;
            double dx=x+.5-room.x,dz=z+.5-room.z;
            double u=(dx*room.cos+dz*room.sin)/room.rx,v=(-dx*room.sin+dz*room.cos)/room.rz;
            double horizontal=u*u+v*v;
            if(horizontal>1.28)continue;
            for(int y=minY;y<=maxY;y++) {
                int index=(y-TunnelCarver.MIN_Y)*nx*nz+column;
                if(air[index])continue;
                double dy=(y+.5-room.y)/room.ry,metric=horizontal+dy*dy;
                // Keep the ellipsoid core continuous to its graph node; the
                // rotated lobe and sparse columns make the exposed wall shape.
                if(metric<.84)air[index]=true;
            }
        }
    }

    public record Room(double x,double y,double z,double rx,double ry,double rz,double cos,double sin,
                       int kind,double side,double lift) {
        boolean overlaps(int x0,int z0,int nx,int nz) {
            double r=Math.max(rx,rz)*1.4;
            return x+r>=x0&&x-r<x0+nx&&z+r>=z0&&z-r<z0+nz;
        }
        Room lobe() {
            return new Room(x+cos*rx*side,y+ry*lift,z+sin*rx*side,rx*.58,ry*.76,rz*.58,cos,sin,kind,0,0);
        }
    }

}
