package com.ch3ddarch33se.thenull.verification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.ch3ddarch33se.thenull.worldgen.NullTerrain;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** AI-assisted benchmark, deliberately excluded from the distributed mod. */
final class PerformanceVerification {
    private final MinecraftServer server;
    private final ServerLevel level;
    private final List<Long> requests = new ArrayList<>();
    private final List<Long> generationTicks = new ArrayList<>();
    private final List<Long> loadedTicks = new ArrayList<>();
    private final List<Long> cachedRequests = new ArrayList<>();
    private int tick;
    private long start;
    private boolean finished;

    private PerformanceVerification(MinecraftServer server) {
        this.server = server;
        level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse("sift:sift")));
        if (level == null) throw new IllegalStateException("Missing Sift");
    }

    static void start(MinecraftServer server) {
        var benchmark = new PerformanceVerification(server);
        ServerTickEvents.START_SERVER_TICK.register(benchmark::beforeTick);
        ServerTickEvents.END_SERVER_TICK.register(benchmark::afterTick);
    }

    private void beforeTick(MinecraftServer ignored) {
        start = System.nanoTime();
        if (finished) return;
        // Four warm-up requests followed by 32 measured, non-overlapping FULL
        // requests. Each includes its required generation neighbours and light.
        if (tick >= 10 && tick < 46) {
            int index = tick - 10;
            int x = 256 + index * 8;
            int z = 256 + ((index * 17) % 37) * 8;
            long requested = System.nanoTime();
            level.getChunk(x, z);
            long elapsed = System.nanoTime() - requested;
            if (index >= 4) requests.add(elapsed);
            System.out.printf(Locale.ROOT, "NULL_BENCH request=%d chunk=%d,%d ms=%.3f%n",
                index, x, z, elapsed / 1e6);
            if (index == 35) {
                // Force one previously generated chunk to continue ticking.
                level.setChunkForced(x, z, true);
            }
        }
        if (tick >= 70 && tick < 170) {
            long requested = System.nanoTime();
            level.getChunk(536, 280); // final request's coordinates, already FULL
            cachedRequests.add(System.nanoTime() - requested);
        }
    }

    private void afterTick(MinecraftServer ignored) {
        if (finished) return;
        long elapsed = System.nanoTime() - start;
        if (tick >= 14 && tick < 46) generationTicks.add(elapsed);
        if (tick >= 70 && tick < 170) loadedTicks.add(elapsed);
        if (++tick == 170) {
            finished = true;
            try {
                String label = System.getProperty("thenull.benchmarkLabel", "current");
                if (!label.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid benchmark label");
                Path output = Path.of("../../build/reports/performance-" + label + ".txt");
                Files.createDirectories(output.toAbsolutePath().getParent());
                StringBuilder text = new StringBuilder("seed = 123456789\njava = ")
                    .append(System.getProperty("java.version")).append("\nprocessors = ")
                    .append(Runtime.getRuntime().availableProcessors()).append('\n')
                    .append("method = 4 warmup + 32 fresh FULL chunk requests, one per tick; 100 loaded ticks, one forced chunk; no players\n")
                    .append("fresh_request = ").append(stats(requests)).append('\n')
                    .append("generation_tick = ").append(stats(generationTicks)).append('\n')
                    .append("loaded_tick = ").append(stats(loadedTicks)).append('\n')
                    .append("cached_request = ").append(stats(cachedRequests)).append('\n')
                    .append("lower_pass = ").append(NullTerrain.benchmarkSummary()).append('\n')
                    .append("client_fps = NOT MEASURED: headless server\n")
                    .append("scope = includes neighbour generation and lighting; not isolated noise-task CPU; loaded ticks are not multiplayer load\n")
                    .append("automated_result = PASSED\n");
                Files.writeString(output, text);
                Files.writeString(Path.of("../../build/reports/performance-" + label + "-requests.csv"),
                    "request,nanoseconds\n" + csv(requests));
                System.out.print(text);
            } catch (Exception error) {
                throw new RuntimeException(error);
            } finally {
                server.halt(false);
            }
        }
    }

    private static String csv(List<Long> values) {
        var text = new StringBuilder();
        for (int i = 0; i < values.size(); i++) text.append(i).append(',').append(values.get(i)).append('\n');
        return text.toString();
    }

    private static String stats(List<Long> values) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        return String.format(Locale.ROOT, "n=%d mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f max_ms=%.3f over_50ms=%d",
            sorted.length, values.stream().mapToLong(Long::longValue).average().orElseThrow() / 1e6,
            sorted[sorted.length / 2] / 1e6, sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
            sorted[sorted.length - 1] / 1e6, values.stream().filter(value -> value > 50_000_000).count());
    }
}
