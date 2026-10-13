package com.ch3ddarch33se.thenull.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;

/** Solid deep sections and connected caves after the native exposed-geology band.
 * Density evaluation is unnecessary below the guaranteed solid band or in the gap.
 */
public final class NativeNullTerrain {
    private static final boolean PROFILE = Boolean.getBoolean("thenull.benchmark");
    private static final java.util.concurrent.atomic.LongAdder CHUNKS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder NATIVE_NS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder FINISH_NS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder CARVE_NS = new java.util.concurrent.atomic.LongAdder();
    public static final int MIN_Y = -768, SURFACE_MIN_Y = -368, GAP_MIN_Y = -256;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final Strategy<BlockState> STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);

    private NativeNullTerrain() { }

    public static void finish(CaveSystem generator, CaveLighting lights, ChunkAccess chunk, BlockState material) {
        long start = PROFILE ? System.nanoTime() : 0;
        if (chunk.getMinY() != MIN_Y || chunk.getHeight() != 1024) {
            throw new IllegalStateException("The Null requires a fresh -768..255 world");
        }
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        // Entrances continue to guaranteed exterior air. No heightmap or density
        // evaluations are needed for the many neighbouring graph owners.
        boolean[] caves = generator.carve(x0-1, z0-1, 18, 18);
        if (PROFILE) CARVE_NS.add(System.nanoTime() - start);
        LevelChunkSection[] sections = chunk.getSections();
        for (int y0 = MIN_Y; y0 < 0; y0 += 16) {
            int index = chunk.getSectionIndex(y0);
            var previous = sections[index];
            if (y0 >= GAP_MIN_Y && y0 < -16) continue; // untouched uniform air
            boolean lower=y0<SURFACE_MIN_Y;
            // The deep cave volume is air-majority, so default it to air and
            // write only solid Sculk, bedrock and non-air lichen states.
            var states = lower
                ? new PalettedContainer<>(AIR, STRATEGY) : previous.getStates();
            states.acquire();
            try {
                for (int y = 0; y < 16; y++) {
                    int wy = y0 + y;
                    if (wy >= GAP_MIN_Y && wy < -4) continue;
                    for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        if (wy < TunnelCarver.MIN_Y || wy >= -4) {
                            states.getAndSetUnchecked(x, y, z, BEDROCK);
                        } else {
                            int caveIndex=((wy-TunnelCarver.MIN_Y)*18+z+1)*18+x+1;
                            if(caves[caveIndex]) {
                                BlockState caveState=lights.state(caves,caveIndex,18,18,x0+x,wy,z0+z);
                                if(!lower||caveState!=AIR)states.getAndSetUnchecked(x,y,z,caveState);
                            } else if(lower) {
                                states.getAndSetUnchecked(x,y,z,material);
                            }
                        }
                    }
                }
            } finally { states.release(); }
            sections[index] = new LevelChunkSection(states, previous.getBiomes());
        }
        // The separator is the highest lower block; native upper terrain can
        // subsequently update these maps normally.
        for (var type : new Heightmap.Types[] {Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG}) {
            var map = chunk.getOrCreateHeightmapUnprimed(type);
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) map.update(x, -1, z, BEDROCK);
        }
        if (PROFILE) { CHUNKS.increment(); FINISH_NS.add(System.nanoTime() - start); }
    }

    public static void recordNative(long elapsed) { if (PROFILE) NATIVE_NS.add(elapsed); }
    public static String benchmarkSummary() {
        return String.format(java.util.Locale.ROOT, "chunks=%d native_ms=%.3f finish_ms=%.3f carve_ms=%.3f (parallel task durations, includes warmup; carve included in finish)",
            CHUNKS.sum(), NATIVE_NS.sum()/(double)CHUNKS.sum()/1e6, FINISH_NS.sum()/(double)CHUNKS.sum()/1e6,
            CARVE_NS.sum()/(double)CHUNKS.sum()/1e6);
    }
}
