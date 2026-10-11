package com.ch3ddarch33se.thenull.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** Local-only real FPS and integrated-server tick sampling. AI-assisted, Apache-2.0. */
final class ClientPerformanceCapture {
    private final List<Long> ticks = new ArrayList<>();
    private final MinecraftServer server;
    private boolean sampling;
    private long started;

    ClientPerformanceCapture(TestSingleplayerContext world) {
        server = world.getServer().computeOnServer(value -> value);
        ServerTickEvents.START_SERVER_TICK.register(value -> {
            if (value == server) started = System.nanoTime();
        });
        ServerTickEvents.END_SERVER_TICK.register(value -> {
            if (value == server && sampling) ticks.add(System.nanoTime() - started);
        });
    }

    void measure(ClientGameTestContext context, TestSingleplayerContext world,
        Map<String, String> report, String location) {
        // Destination has already finished chunk rendering. Settle for five
        // seconds, then poll actual rendered FPS once per second for ten seconds.
        context.waitTicks(100);
        world.getServer().runOnServer(value -> { ticks.clear(); sampling = true; });
        List<Integer> fps = new ArrayList<>();
        List<String> limits = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            context.waitTicks(20);
            context.runOnClient(client -> {
                var tracker = client.getFramerateLimitTracker();
                String reason = tracker.getThrottleReason().name();
                if (!reason.equals("NONE")) throw new AssertionError("FPS sample is throttled: " + reason);
                fps.add(client.getFps());
                limits.add(tracker.getFramerateLimit() + ":" + reason);
            });
        }
        String serverStats = world.getServer().computeOnServer(value -> {
            sampling = false;
            long[] sorted = ticks.stream().mapToLong(Long::longValue).sorted().toArray();
            if (sorted.length == 0) throw new AssertionError("No integrated server ticks sampled");
            return String.format(Locale.ROOT, "n=%d mean_ms=%.3f p95_ms=%.3f max_ms=%.3f over_50ms=%d",
                sorted.length, ticks.stream().mapToLong(Long::longValue).average().orElseThrow() / 1e6,
                sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6, sorted[sorted.length - 1] / 1e6,
                ticks.stream().filter(time -> time > 50_000_000).count());
        });
        int[] sorted = fps.stream().mapToInt(Integer::intValue).sorted().toArray();
        if (sorted[0] <= 0) throw new AssertionError("Rendered FPS counter is not active");
        report.put(location + "_fps", String.format(Locale.ROOT,
            "n=10 mean=%.1f median=%d min=%d max=%d samples=%s",
            fps.stream().mapToInt(Integer::intValue).average().orElseThrow(), sorted[5], sorted[0], sorted[9], fps));
        report.put(location + "_server_tick", serverStats);
        report.put(location + "_fps_limits", limits.toString());
        context.runOnClient(client -> report.put(location + "_position",
            client.level.dimension() + " " + client.player.blockPosition() + "; window="
                + client.getWindow().getWidth() + "x" + client.getWindow().getHeight()));
        context.runOnClient(client -> report.put(location + "_loaded_chunks", Integer.toString(client.level.getChunkSource().getLoadedChunksCount())));
        System.out.println("NULL_CLIENT_PERF " + location + " " + report.get(location + "_fps") + " " + serverStats);
    }
}
