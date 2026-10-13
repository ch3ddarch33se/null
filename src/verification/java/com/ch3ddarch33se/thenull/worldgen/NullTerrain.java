package com.ch3ddarch33se.thenull.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/** Lower-only terrain pass. AI-assisted original implementation, Apache-2.0. */
public final class NullTerrain {
    public static final BlockState MATERIAL = Blocks.SCULK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final Strategy<BlockState> STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
    private static final boolean PROFILE = Boolean.getBoolean("thenull.benchmark");
    private static final LongAdder CHUNKS = new LongAdder(), SHAPE_NS = new LongAdder(), CARVE_NS = new LongAdder(), FILL_NS = new LongAdder();

    private NullTerrain() {}

    public static void generate(long seed, ChunkAccess chunk) {
        long started = PROFILE ? System.nanoTime() : 0;
        var shape = new TerrainShape(seed);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int[] heights = new int[256];
        int lowest = Integer.MAX_VALUE;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            heights[z * 16 + x] = shape.surface(x0 + x, z0 + z);
            lowest = Math.min(lowest, heights[z * 16 + x]);
        }
        long shaped = PROFILE ? System.nanoTime() : 0;
        boolean[] caves = new BaselineTunnelCarver(seed, new TerrainShape(seed)::surface).carve(x0, z0, 16, 16);
        long carved = PROFILE ? System.nanoTime() : 0;
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionY = -48; sectionY < 0; sectionY++) {
            int y0 = sectionY * 16;
            int index = chunk.getSectionIndex(y0);
            if (y0 >= -256 && y0 + 15 < -4) {
                sections[index] = new LevelChunkSection(new PalettedContainer<>(AIR, STRATEGY), sections[index].getBiomes());
                continue;
            }
            BlockState initial = y0 + 15 <= lowest && y0 >= -752 ? MATERIAL : AIR;
            // A uniform palette is cheap for solid rock and the large air gap.
            var states = new PalettedContainer<>(initial, STRATEGY);
            states.acquire();
            try {
                for (int y = 0; y < 16; y++) {
                    int worldY = y0 + y;
                    for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        BlockState desired;
                        if (worldY >= -4 || worldY < TunnelCarver.MIN_Y) desired = BEDROCK;
                        else if (worldY > heights[z * 16 + x]) desired = AIR;
                        else if (caves[((worldY - TunnelCarver.MIN_Y) * 16 + z) * 16 + x]) desired = AIR;
                        else desired = MATERIAL;
                        if (desired != initial) states.getAndSetUnchecked(x, y, z, desired);
                    }
                }
            } finally { states.release(); }
            // Preserve the original biome container. Constructor recomputes
            // block/fluid counts; the normal LIGHT stage follows this pass.
            sections[index] = new LevelChunkSection(states, sections[index].getBiomes());
        }
        for (Heightmap.Types type : new Heightmap.Types[] {Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG}) {
            Heightmap map = chunk.getOrCreateHeightmapUnprimed(type);
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) map.update(x, -1, z, BEDROCK);
        }
        if (PROFILE) {
            SHAPE_NS.add(shaped - started); CARVE_NS.add(carved - shaped);
            FILL_NS.add(System.nanoTime() - carved); CHUNKS.increment();
        }
    }

    public static String benchmarkSummary() {
        long count = CHUNKS.sum();
        return String.format(Locale.ROOT, "chunks=%d mean_shape_ms=%.3f mean_carve_ms=%.3f mean_fill_ms=%.3f (parallel task wall times, includes warmup)",
            count, SHAPE_NS.sum() / (double) count / 1e6, CARVE_NS.sum() / (double) count / 1e6, FILL_NS.sum() / (double) count / 1e6);
    }
}
