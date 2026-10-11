package com.ch3ddarch33se.thenull.verification;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** AI-assisted, local-only checks against real chunks in a disposable server world. */
public final class NullVerification implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("thenull-verification");
    private static final ResourceKey<Level> SIFT = ResourceKey.create(
        Registries.DIMENSION, Identifier.parse("sift:sift"));
    // Adjacent origin chunks plus distant positive/negative coordinates expose
    // several terrain samples without generating a large persistent world.
    private static final int[][] CHUNKS = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {-5, 3}, {9, -7},
        {128, 128}, {-128, -128}, {64, 0}, {-64, 64}, {256, -256}};
    private final Map<String, String> report = new LinkedHashMap<>();
    private int failures;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::run);
    }

    private void run(MinecraftServer server) {
        long started = System.nanoTime();
        try {
            check("mods_load", FabricLoader.getInstance().isModLoaded("thenull")
                && FabricLoader.getInstance().isModLoaded("sift_backport"),
                "The Null and external Sift Backport loaded");
            ServerLevel sift = server.getLevel(SIFT);
            if (sift == null) {
                throw new IllegalStateException("sift:sift dimension is absent");
            }
            check("seed", sift.getSeed() == 123456789L, "actual=" + sift.getSeed());
            check("sift_bounds", sift.getMinY() == -768 && sift.getHeight() == 1024 && sift.getMaxY() == 255,
                "min_y=" + sift.getMinY() + ", height=" + sift.getHeight() + ", max_y_inclusive=" + sift.getMaxY());
            ServerLevel overworld = server.overworld();
            check("overworld_bounds", overworld.getMinY() == -64 && overworld.getHeight() == 384 && overworld.getMaxY() == 319,
                "min_y=" + overworld.getMinY() + ", height=" + overworld.getHeight() + ", max_y_inclusive=" + overworld.getMaxY());
            verifyOriginalUpperDensity(sift);
            verifyTerrain(sift);
            report.put("ambient_light", Float.toString(sift.dimensionType().ambientLight()));
            report.put("lighting_visibility", "NOT TESTED: requires a client; ambient light is retained from Sift");
            report.put("cave_connectivity", "NOT TESTED: cave air counts and widths do not prove connectivity");
            report.put("null_enter", "NOT TESTED: command verification has not completed");
            report.put("null_leave", "NOT TESTED: command verification has not completed");
            report.putAll(TeleportVerification.run(server));
            report.put("teleport_client_network", "NOT TESTED: synthetic server players exercise commands and transitions; no real clients render or consume packets");
            report.put("sift_visual_preservation", "NOT TESTED: requires client screenshots or an unmodified Sift comparison");
        } catch (Throwable error) {
            failures++;
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            report.put("uncaught_error", trace.toString());
            LOGGER.error("Disposable-world verification failed", error);
        } finally {
            report.put("elapsed_seconds", String.format(java.util.Locale.ROOT, "%.3f",
                (System.nanoTime() - started) / 1_000_000_000.0));
            report.put("automated_result", failures == 0 ? "PASSED" : "FAILED (" + failures + " checks)");
            Path output = Path.of(System.getProperty("thenull.verificationReport",
                "thenull-verification.txt"));
            try {
                if (output.toAbsolutePath().getParent() != null) {
                    Files.createDirectories(output.toAbsolutePath().getParent());
                }
                StringBuilder text = new StringBuilder();
                report.forEach((key, value) -> text.append(key).append(" = ").append(value).append('\n'));
                Files.writeString(output, text, StandardCharsets.UTF_8);
                LOGGER.info("The Null verification {}. Report: {}", report.get("automated_result"), output);
            } catch (Exception error) {
                LOGGER.error("Could not write verification report", error);
            }
            // This harness runs only in its own source set and disposable run
            // directory. Graceful shutdown saves its test world for inspection.
            server.halt(false);
        }
    }

    private void verifyOriginalUpperDensity(ServerLevel level) throws Exception {
        Path originalResource = FabricLoader.getInstance().getModContainer("sift_backport")
            .orElseThrow().findPath("data/sift/worldgen/noise_settings/sift.json").orElseThrow();
        NoiseGeneratorSettings originalSettings;
        try (var reader = Files.newBufferedReader(originalResource, StandardCharsets.UTF_8)) {
            var parsed = NoiseGeneratorSettings.DIRECT_CODEC.parse(
                RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), JsonParser.parseReader(reader));
            originalSettings = parsed.result().orElseThrow(() -> new IllegalStateException(
                "Native original Sift settings decode failed: " + parsed.error()));
        }
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        NoiseGeneratorSettings actualSettings = generator.generatorSettings().value();
        RandomState originalRandom = RandomState.create(level.registryAccess().lookupOrThrow(Registries.NOISE),
            level.getSeed(), originalSettings);
        RandomState actualRandom = level.getChunkSource().randomState();
        long samples = 0;
        long mismatches = 0;
        long originalSolidSamples = 0;
        for (int[] coordinates : CHUNKS) {
            for (int offsetX : new int[] {0, 8, 15}) {
                for (int offsetZ : new int[] {0, 8, 15}) {
                    for (int y = 0; y < 256; y++) {
                        int x = coordinates[0] * 16 + offsetX;
                        int z = coordinates[1] * 16 + offsetZ;
                        float expected = originalRandom.sampleBlockValueUncached(
                            originalSettings.noiseRouter().finalDensity(), x, y, z);
                        float actual = actualRandom.sampleBlockValueUncached(
                            actualSettings.noiseRouter().finalDensity(), x, y, z);
                        samples++;
                        if (expected > 0) {
                            originalSolidSamples++;
                        }
                        if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual)) {
                            mismatches++;
                            report.putIfAbsent("first_upper_density_mismatch",
                                x + ", " + y + ", " + z + ": expected=" + expected + ", actual=" + actual);
                        }
                    }
                }
            }
        }
        check("upper_sift_density_preserved", mismatches == 0,
            "native_original_sift_samples=" + samples + ", mismatches=" + mismatches
                + ", original_positive_samples=" + originalSolidSamples);
    }

    private void verifyTerrain(ServerLevel level) {
        long barrierWrong = 0;
        long gapWrong = 0;
        long materialWrong = 0;
        long bottomWrong = 0;
        long caveAir = 0;
        long caveSampleCells = 0;
        long walkableCaveFloors = 0;
        long upperSolid = 0;
        long skyLightWrong = 0;
        long[] caveRuns = new long[17];
        long totalSurface = 0;
        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        int surfaces = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] coordinates : CHUNKS) {
            int startX = coordinates[0] * 16;
            int startZ = coordinates[1] * 16;
            LOGGER.info("Verifying Sift chunk {}, {}", coordinates[0], coordinates[1]);
            LevelChunk chunk = level.getChunkAt(new BlockPos(startX, -300, startZ));
            int[][] columnSurfaces = new int[16][16];
            long upperHash = 0xcbf29ce484222325L;
            long lowerHash = 0xcbf29ce484222325L;
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    int x = startX + localX;
                    int z = startZ + localZ;
                    for (int y = -4; y <= -1; y++) {
                        if (!chunk.getBlockState(pos.set(x, y, z)).is(Blocks.BEDROCK)) {
                            barrierWrong++;
                            recordFirst("barrier", pos, chunk.getBlockState(pos));
                        }
                    }
                    for (int y = -256; y <= -5; y++) {
                        if (!chunk.getBlockState(pos.set(x, y, z)).isAir()) {
                            gapWrong++;
                            recordFirst("gap", pos, chunk.getBlockState(pos));
                        }
                    }
                    int surface = Integer.MIN_VALUE;
                    for (int y = -257; y >= -768; y--) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        lowerHash ^= state.toString().hashCode();
                        lowerHash *= 0x100000001b3L;
                        if (!(state.isAir() || state.is(Blocks.SCULK)
                            || (y <= -764 && state.is(Blocks.BEDROCK)))) {
                            materialWrong++;
                            recordFirst("material", pos, state);
                        }
                        if (surface == Integer.MIN_VALUE && state.is(Blocks.SCULK)) {
                            surface = y;
                        }
                        if (y == -768 && !state.is(Blocks.BEDROCK)) {
                            bottomWrong++;
                            recordFirst("bottom", pos, state);
                        }
                        // Exclude open air above the landscape and its first
                        // ten blocks so this counts internal tunnels.
                        if (surface != Integer.MIN_VALUE && y < surface - 10 && y > -760) {
                            caveSampleCells++;
                            if (state.isAir()) {
                                caveAir++;
                                if (chunk.getBlockState(pos.above()).isAir()
                                    && chunk.getBlockState(pos.below()).is(Blocks.SCULK)) {
                                    walkableCaveFloors++;
                                }
                            }
                        }
                    }
                    columnSurfaces[localX][localZ] = surface;
                    if (surface != Integer.MIN_VALUE) {
                        minSurface = Math.min(minSurface, surface);
                        maxSurface = Math.max(maxSurface, surface);
                        totalSurface += surface;
                        surfaces++;
                    }
                    for (int y = 0; y < 256; y++) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (!state.isAir()) {
                            upperSolid++;
                        }
                        upperHash ^= state.toString().hashCode();
                        upperHash *= 0x100000001b3L;
                    }
                    for (int y : new int[] {-5, -128, -256}) {
                        if (level.getBrightness(LightLayer.SKY, pos.set(x, y, z)) != 0) {
                            skyLightWrong++;
                            recordFirst("skylight", pos, chunk.getBlockState(pos));
                        }
                    }
                }
            }
            // One-dimensional interior air-run samples describe widths along
            // X, not cave connectivity or minimum/maximum cross-section.
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int y = -360; y > -760; y -= 8) {
                    int run = 0;
                    boolean touchesEdge = false;
                    for (int localX = 0; localX < 16; localX++) {
                        boolean internalAir = y < columnSurfaces[localX][localZ] - 10
                            && chunk.getBlockState(pos.set(startX + localX, y, startZ + localZ)).isAir();
                        if (internalAir) {
                            if (run == 0) {
                                touchesEdge = localX == 0;
                            }
                            run++;
                        } else if (run > 0) {
                            if (!touchesEdge) {
                                caveRuns[run]++;
                            }
                            run = 0;
                        }
                    }
                    // Runs reaching the right edge are censored to avoid
                    // reporting artificially truncated chunk-edge widths.
                }
            }
            report.put("upper_hash_" + coordinates[0] + "_" + coordinates[1], Long.toUnsignedString(upperHash, 16));
            report.put("lower_hash_" + coordinates[0] + "_" + coordinates[1], Long.toUnsignedString(lowerHash, 16));
        }
        check("barrier_continuous", barrierWrong == 0, "wrong_blocks=" + barrierWrong);
        check("air_gap_empty", gapWrong == 0, "non_air_blocks=" + gapWrong);
        check("null_material", materialWrong == 0, "unexpected_blocks=" + materialWrong);
        check("lower_boundary", bottomWrong == 0, "missing_bottom_bedrock=" + bottomWrong);
        check("surface_elevation", surfaces == CHUNKS.length * 256
            && minSurface >= -350 && maxSurface <= -257,
            "columns=" + surfaces + ", min=" + minSurface + ", max=" + maxSurface
                + ", average=" + (surfaces == 0 ? "n/a" : totalSurface / (double) surfaces));
        check("caves_present", caveAir > 0, "internal_air=" + caveAir + ", sampled_cells=" + caveSampleCells);
        check("cave_player_headroom_present", walkableCaveFloors > 0,
            "two_air_blocks_above_sculk_floor=" + walkableCaveFloors);
        check("narrow_cave_runs_present", caveRuns[2] + caveRuns[3] + caveRuns[4] > 0,
            "x_runs_width_2_to_4=" + (caveRuns[2] + caveRuns[3] + caveRuns[4]));
        check("upper_sift_terrain_present", upperSolid > 0, "solid_blocks=" + upperSolid);
        check("gap_skylight", skyLightWrong == 0, "nonzero_samples=" + skyLightWrong);
        StringBuilder widths = new StringBuilder();
        for (int width = 1; width < caveRuns.length; width++) {
            if (widths.length() > 0) {
                widths.append(", ");
            }
            widths.append(width).append(':').append(caveRuns[width]);
        }
        report.put("cave_x_run_histogram", widths.toString());
        report.put("sample_chunks", Integer.toString(CHUNKS.length));
    }

    private void check(String name, boolean passed, String detail) {
        report.put(name, (passed ? "PASSED: " : "FAILED: ") + detail);
        if (!passed) {
            failures++;
        }
    }

    private void recordFirst(String category, BlockPos pos, BlockState state) {
        report.putIfAbsent("first_" + category + "_mismatch", pos.toShortString() + ": " + state);
    }
}
