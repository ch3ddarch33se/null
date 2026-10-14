package com.ch3ddarch33se.thenull.worldgen;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Valid, deterministic multiface lichen before vanilla's LIGHT stage. The
 * one-block cave halo supplies neighbour support without cross-chunk writes.
 */
public final class CaveLighting {
    private static final BlockState AIR=Blocks.AIR.defaultBlockState();
    private static final Direction[] FACES={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private static final BlockState[] STATES=new BlockState[64];
    static {
        STATES[0]=AIR;
        for(int mask=1;mask<64;mask++) {
            var state=Blocks.GLOW_LICHEN.defaultBlockState();
            for(int i=0;i<6;i++)state=state.setValue(MultifaceBlock.getFaceProperty(FACES[i]),(mask&(1<<i))!=0);
            STATES[mask]=state;
        }
    }
    private final long seed;
    private final CaveSettings settings;
    private final Noise patches;
    public CaveLighting(long seed,CaveSettings settings) {
        this.seed=seed;this.settings=settings;
        patches=NormalNoise.builder().setBaseOctave(-3).setOctaveCount(2).setBaseAmplitude(.8).build()
            .create(new XoroshiroRandomSource(SeedHash.hash(seed,7153,7153,751)));
    }
    public BlockState state(boolean[] air,int index,int nx,int nz,int x,int y,int z) {
        if(y<CavernCarver.FLOOR||y>CavernCarver.CEILING)return AIR;
        double chance=SeedHash.unit(SeedHash.hash(seed,x,z,y+7501));
        if(chance>Math.max(settings.wallLichen(),settings.floorLichen())*1.7)return AIR;
        int plane=nx*nz,faces=0;
        if(!air[index-plane])faces|=1;
        if(!air[index+plane])faces|=2;
        if(!air[index-nx])faces|=4;
        if(!air[index+nx])faces|=8;
        if(!air[index-1])faces|=16;
        if(!air[index+1])faces|=32;
        if(faces==0)return AIR;
        double cluster=Math.clamp(patches.get(x,y,z)*3+.35,0,1);
        double probability=(faces&1)!=0 ? settings.floorLichen() : settings.wallLichen();
        return chance<probability*(.35+1.3*cluster) ? STATES[faces] : AIR;
    }
}
