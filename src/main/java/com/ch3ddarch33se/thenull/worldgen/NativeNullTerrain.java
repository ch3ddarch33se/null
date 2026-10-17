package com.ch3ddarch33se.thenull.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;

/** Lower solid geology, connected caves, and the deterministic central chamber. */
public final class NativeNullTerrain {
    private static final boolean PROFILE = Boolean.getBoolean("thenull.benchmark");
    private static final java.util.concurrent.atomic.LongAdder CHUNKS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder NATIVE_NS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder FINISH_NS = new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder CARVE_NS = new java.util.concurrent.atomic.LongAdder();
    public static final int MIN_Y = CentralChamber.MIN_Y, WORLD_HEIGHT = 2016;
    public static final int SURFACE_MIN_Y = -368, GAP_MIN_Y = -256;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState ORB = Blocks.TINTED_GLASS.defaultBlockState();
    private static final Strategy<BlockState> STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);

    private NativeNullTerrain() { }

    public static void finish(CaveSystem generator, CaveLighting lights, ChunkAccess chunk, BlockState material) {
        long start = PROFILE ? System.nanoTime() : 0;
        if (chunk.getMinY() != MIN_Y || chunk.getHeight() != WORLD_HEIGHT) {
            throw new IllegalStateException("The Null requires a fresh -1760..255 world");
        }
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        boolean chamberInside = CentralChamber.sectionFullyInside(x0, z0);
        boolean chamberOutside = CentralChamber.sectionOutside(x0, z0);
        boolean[] caves = chamberInside
            ? generator.carveAboveCentralChamber(x0 - 1, z0 - 1, 18, 18)
            : generator.carve(x0 - 1, z0 - 1, 18, 18);
        if (PROFILE) CARVE_NS.add(System.nanoTime() - start);
        LevelChunkSection[] sections = chunk.getSections();
        for (int y0 = MIN_Y; y0 < 0; y0 += 16) {
            int index = chunk.getSectionIndex(y0);
            LevelChunkSection previous = sections[index];
            if (y0 >= GAP_MIN_Y && y0 < -16) continue; // uniform guaranteed air

            boolean fullChamberAir = chamberInside && CentralChamber.sectionIsAir(y0);
            boolean belowTunnelNetwork = y0 + 15 < TunnelCarver.MIN_Y;
            if (y0 > MIN_Y && (fullChamberAir || (chamberOutside && belowTunnelNetwork))) {
                boolean orbIntersects = fullChamberAir && CentralChamber.orbMayIntersect(x0, y0, z0);
                // Deep room sections are already empty in fresh chunks. Keep
                // their existing section and palette instead of allocating
                // another uniform AIR section for every vertical slice.
                if (fullChamberAir && previous.hasOnlyAir() && !orbIntersects) continue;
                BlockState fill = fullChamberAir ? AIR : material;
                PalettedContainer<BlockState> states = new PalettedContainer<>(fill, STRATEGY);
                states.acquire();
                try {
                    if (orbIntersects) {
                        placeOrbShell(states, x0, y0, z0);
                    }
                } finally {
                    states.release();
                }
                sections[index] = new LevelChunkSection(states, previous.getBiomes());
                continue;
            }

            // Deep sections intersecting the chamber's circular edge contain
            // only uniform Sculk columns or uniform air columns. Set the room
            // footprint by column instead of scanning all 4096 voxels.
            if (y0 > MIN_Y && !chamberInside && !chamberOutside
                && y0 + 15 < TunnelCarver.MIN_Y && CentralChamber.sectionIsAir(y0)
                && !CentralChamber.orbMayIntersect(x0, y0, z0)) {
                PalettedContainer<BlockState> states = new PalettedContainer<>(material, STRATEGY);
                states.acquire();
                try {
                    for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        if (!CentralChamber.containsColumn(x0 + x, z0 + z)) continue;
                        for (int y = 0; y < 16; y++) states.getAndSetUnchecked(x, y, z, AIR);
                    }
                } finally {
                    states.release();
                }
                sections[index] = new LevelChunkSection(states, previous.getBiomes());
                continue;
            }

            BlockState initial = y0 < SURFACE_MIN_Y ? material : previous.getStates().get(0, 0, 0);
            PalettedContainer<BlockState> states = y0 < SURFACE_MIN_Y
                ? new PalettedContainer<>(initial, STRATEGY) : previous.getStates();
            boolean orbPossible = CentralChamber.orbMayIntersect(x0, y0, z0);
            states.acquire();
            try {
                for (int y = 0; y < 16; y++) {
                    int wy = y0 + y;
                    for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        BlockState desired;
                        if (wy <= CentralChamber.BEDROCK_MAX_Y || wy >= -4) {
                            desired = BEDROCK;
                        } else if (wy >= GAP_MIN_Y && wy < -4) {
                            desired = AIR;
                        } else if (wy == CentralChamber.FLOOR_Y) {
                            desired = material;
                        } else if (wy >= CentralChamber.AIR_MIN_Y && wy <= CentralChamber.AIR_MAX_Y
                            && CentralChamber.containsColumn(x0 + x, z0 + z)) {
                            desired = orbPossible && CentralChamber.isOrbShell(x0 + x, wy, z0 + z) ? ORB : AIR;
                        } else if (y0 >= SURFACE_MIN_Y) {
                            desired = states.get(x, y, z); // keep vanilla density geometry outside the chamber
                            if (wy >= TunnelCarver.MIN_Y && wy <= TunnelCarver.MAX_Y) {
                                if (chamberInside) {
                                    int caveIndex = ((wy - CentralChamber.AIR_MAX_Y) * 18 + z + 1) * 18 + x + 1;
                                    if (caves[caveIndex]) desired = AIR;
                                } else {
                                    int caveIndex = ((wy - TunnelCarver.MIN_Y) * 18 + z + 1) * 18 + x + 1;
                                    if (caves[caveIndex]) desired = lights.state(caves, caveIndex, 18, 18, x0 + x, wy, z0 + z);
                                }
                            }
                        } else if (wy >= TunnelCarver.MIN_Y && wy <= TunnelCarver.MAX_Y) {
                            int caveIndex = ((wy - TunnelCarver.MIN_Y) * 18 + z + 1) * 18 + x + 1;
                            desired = caves[caveIndex]
                                ? lights.state(caves, caveIndex, 18, 18, x0 + x, wy, z0 + z)
                                : material;
                        } else {
                            desired = material;
                        }
                        if (!states.get(x, y, z).equals(desired)) states.getAndSetUnchecked(x, y, z, desired);
                    }
                }
            } finally {
                states.release();
            }
            sections[index] = new LevelChunkSection(states, previous.getBiomes());
        }
        for (var type : new Heightmap.Types[] {Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG}) {
            var map = chunk.getOrCreateHeightmapUnprimed(type);
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) map.update(x, -1, z, BEDROCK);
        }
        if (PROFILE) { CHUNKS.increment(); FINISH_NS.add(System.nanoTime() - start); }
    }

    private static void placeOrbShell(PalettedContainer<BlockState> states, int x0, int y0, int z0) {
        for (int y = 0; y < 16; y++) {
            int worldY = y0 + y;
            if (worldY < CentralChamber.AIR_MIN_Y || worldY > CentralChamber.AIR_MAX_Y) continue;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (CentralChamber.containsColumn(x0 + x, z0 + z)
                    && CentralChamber.isOrbShell(x0 + x, worldY, z0 + z)) {
                    states.getAndSetUnchecked(x, y, z, ORB);
                }
            }
        }
    }

    public static void recordNative(long elapsed) { if (PROFILE) NATIVE_NS.add(elapsed); }
    public static String benchmarkSummary() {
        return String.format(java.util.Locale.ROOT, "chunks=%d native_ms=%.3f finish_ms=%.3f carve_ms=%.3f (parallel task durations, includes warmup; carve included in finish)",
            CHUNKS.sum(), NATIVE_NS.sum() / (double) CHUNKS.sum() / 1e6,
            FINISH_NS.sum() / (double) CHUNKS.sum() / 1e6, CARVE_NS.sum() / (double) CHUNKS.sum() / 1e6);
    }
}
