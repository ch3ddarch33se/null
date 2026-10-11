package com.ch3ddarch33se.thenull.verification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Standalone testmod works with or without The Null; AI-assisted, Apache-2.0. */
public final class ComparisonVerification implements ModInitializer {
    private MinecraftServer server;
    private ServerLevel sift;
    private boolean withNull, finished;
    private int tick, upperX, upperZ;
    private long tickStart;
    private ServerPlayer probe;
    private final List<Long> siftRequests = new ArrayList<>(), overworldRequests = new ArrayList<>();
    private final List<Long> upperTicks = new ArrayList<>(), nullTicks = new ArrayList<>(), overworldTicks = new ArrayList<>();
    private final List<String> raw = new ArrayList<>();

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(instance -> {
            server = instance;
            sift = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse("sift:sift")));
            withNull = FabricLoader.getInstance().isModLoaded("thenull");
            if (sift == null || sift.getMinY() != (withNull ? -768 : 0)) throw new IllegalStateException("Wrong control dimension height");
            ServerTickEvents.START_SERVER_TICK.register(this::beforeTick);
            ServerTickEvents.END_SERVER_TICK.register(this::afterTick);
        });
    }

    private void beforeTick(MinecraftServer ignored) {
        tickStart = System.nanoTime();
        if (finished) return;
        if (tick >= 10 && tick < 46) request(sift, tick - 10, siftRequests, "sift_full");
        if (tick >= 50 && tick < 86) request(server.overworld(), tick - 50, overworldRequests, "overworld_full");
        if (tick == 90) {
            upperX = 128 * 16 + 8; upperZ = 128 * 16 + 8;
            sift.getChunk(128, 128);
            int y = sift.getHeight(Heightmap.Types.MOTION_BLOCKING, upperX, upperZ);
            if (y <= 1) throw new IllegalStateException("Upper probe must occupy actual upper Sift terrain");
            positionProbe(sift, upperX, y + 1, upperZ);
        }
        if (tick == 210) {
            if (withNull) {
                int floor = -257;
                while (floor > -440 && sift.getBlockState(new net.minecraft.core.BlockPos(upperX, floor, upperZ)).isAir()) floor--;
                if (floor <= -440) throw new IllegalStateException("Missing Null surface for tick probe");
                positionProbe(sift, upperX, floor + 2, upperZ);
            } else positionOverworld();
        }
        if (withNull && tick == 330) positionOverworld();
    }

    private void request(ServerLevel level, int index, List<Long> samples, String name) {
        int x = 256 + index * 8, z = 256 + ((index * 17) % 37) * 8;
        long start = System.nanoTime();
        level.getChunk(x, z);
        long elapsed = System.nanoTime() - start;
        if (index >= 4) { samples.add(elapsed); raw.add(name + "," + index + "," + elapsed); }
        System.out.printf(Locale.ROOT, "NULL_COMPARE %s request=%d ms=%.3f%n", name, index, elapsed / 1e6);
    }

    private void positionOverworld() {
        int x = 128 * 16 + 8, z = 128 * 16 + 8;
        ServerLevel level = server.overworld();
        level.getChunk(128, 128);
        positionProbe(level, x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1, z);
    }

    private void positionProbe(ServerLevel level, int x, int y, int z) {
        if (probe != null) {
            probe.level().removePlayerImmediately(probe, Entity.RemovalReason.DISCARDED);
            probe.getTextFilter().leave();
            // Retain exactly one forced 3x3 area, not all prior test positions.
            for (ServerLevel world : new ServerLevel[] {sift, server.overworld()}) {
                for (int cz = 127; cz <= 129; cz++) for (int cx = 127; cx <= 129; cx++) world.setChunkForced(cx, cz, false);
            }
        }
        for (int cz = 127; cz <= 129; cz++) for (int cx = 127; cx <= 129; cx++) level.setChunkForced(cx, cz, true);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "NullPerfProbe");
        probe = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        probe.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
            probe, CommonListenerCookie.createInitial(profile, false));
        probe.setPos(x + .5, y, z + .5);
        probe.setNoGravity(true);
        level.addNewPlayer(probe);
        System.out.println("NULL_COMPARE probe=" + level.dimension() + " y=" + y);
    }

    private void afterTick(MinecraftServer ignored) {
        if (finished) return;
        long elapsed = System.nanoTime() - tickStart;
        if (tick >= 110 && tick < 210) { upperTicks.add(elapsed); raw.add("sift_upper_tick," + tick + "," + elapsed); }
        if (withNull && tick >= 230 && tick < 330) { nullTicks.add(elapsed); raw.add("null_tick," + tick + "," + elapsed); }
        int overworldStart = withNull ? 350 : 230;
        if (tick >= overworldStart && tick < overworldStart + 100) {
            overworldTicks.add(elapsed); raw.add("overworld_tick," + tick + "," + elapsed);
        }
        if (++tick == overworldStart + 100) finish();
    }

    private void finish() {
        finished = true;
        try {
            Path output = Path.of(System.getProperty("thenull.comparisonReport"));
            Files.createDirectories(output.getParent());
            String text = "with_null = " + withNull + "\nsift_min_y = " + sift.getMinY()
                + "\nsift_height = " + sift.getHeight() + "\nseed = " + sift.getSeed()
                + "\njava = " + System.getProperty("java.version")
                + "\nsift_fresh_full = " + stats(siftRequests)
                + "\noverworld_fresh_full = " + stats(overworldRequests)
                + "\nsift_upper_loaded_tick = " + stats(upperTicks)
                + "\nnull_loaded_tick = " + (withNull ? stats(nullTicks) : "ABSENT: unmodified Sift control")
                + "\noverworld_loaded_tick = " + stats(overworldTicks)
                + "\nmethod = production Fabric, fresh world, 4+32 FULL requests per dimension; 20 settling +100 ticks per location, stationary synthetic player and 3x3 forced area\n"
                + "generation_scope = upper Sift and Null share each full-height chunk; generation time cannot be separated by player Y\n"
                + "client_fps = NOT MEASURED; synthetic player has no rendering/network consumer\n"
                + "automated_result = PASSED\n";
            Files.writeString(output, text);
            Files.writeString(output.resolveSibling(output.getFileName().toString().replace(".txt", ".csv")),
                "phase,sample,nanoseconds\n" + String.join("\n", raw) + "\n");
            System.out.print(text);
        } catch (Exception error) { throw new RuntimeException(error); }
        finally {
            if (probe != null) { probe.level().removePlayerImmediately(probe, Entity.RemovalReason.DISCARDED); probe.getTextFilter().leave(); }
            server.halt(false);
        }
    }

    private static String stats(List<Long> samples) {
        long[] sorted = samples.stream().mapToLong(Long::longValue).sorted().toArray();
        return String.format(Locale.ROOT, "n=%d mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f max_ms=%.3f over_50ms=%d",
            sorted.length, samples.stream().mapToLong(Long::longValue).average().orElseThrow() / 1e6,
            sorted[sorted.length / 2] / 1e6, sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
            sorted[sorted.length - 1] / 1e6, samples.stream().filter(value -> value > 50_000_000).count());
    }
}
