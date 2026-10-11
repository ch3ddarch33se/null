package com.ch3ddarch33se.thenull.verification;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Local-only client checks using Fabric's game framebuffer and fresh test worlds. */
public final class ClientVerification implements FabricClientGameTest {
    private static final ResourceKey<Level> SIFT = ResourceKey.create(
        Registries.DIMENSION, Identifier.parse("sift:sift"));

    @Override
    public void runTest(ClientGameTestContext context) {
        Map<String, String> report = new LinkedHashMap<>();
        boolean performance = Boolean.getBoolean("thenull.clientPerformance");
        try (TestSingleplayerContext world = context.worldBuilder()
            .setUseConsistentSettings(false)
            .adjustSettings(settings -> {
                settings.setName("thenull-client-verification-" + System.currentTimeMillis());
                settings.setSeed("123456789");
                settings.setAllowCommands(true);
                settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                settings.setDifficulty(Difficulty.PEACEFUL);
            }).create()) {
            report.put("world", world.getWorldSave().getSaveDirectory().toString());
            context.runOnClient(client -> {
                client.options.renderDistance().set(performance ? 8 : 4);
                client.options.simulationDistance().set(8);
                client.options.gamma().set(0.0);
                if (performance) {
                    client.options.enableVsync().set(false);
                    client.options.framerateLimit().set(260);
                    client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                    client.options.pauseOnLostFocus = false;
                }
                client.options.broadcastOptions();
            });
            world.getConnection().waitForServerboundPackets();
            if (performance) world.getServer().runOnServer(server -> {
                require(world.getConnection().getServerPlayer().requestedViewDistance() == 8,
                    "Client render-distance update did not reach the integrated server");
            });
            ClientPerformanceCapture capture = performance ? new ClientPerformanceCapture(world) : null;
            if (performance) report.put("performance_settings", "render_distance=8, simulation_distance=8, vsync=false, fps_option=260 (maximum), inactivity_limit=MINIMIZED (AFK cap disabled), gamma=0, peaceful, seed=123456789; 5s settle +10 FPS polls at 1s intervals per location");
            // Begin on a verified natural floor, so the return position is a
            // meaningful test rather than a creative-mode airborne location.
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                BlockPos feet = safeSurface(server.overworld(), player.blockPosition(), server.overworld().getMinY());
                require(player.teleportTo(server.overworld(), feet.getX() + 0.5, feet.getY(),
                    feet.getZ() + 0.5, Set.<Relative>of(), 45, 12, true), "Initial safe test placement failed");
            });
            world.getConnection().waitForClientboundPackets();
            waitForRenderedChunks(context, world, performance);
            context.waitTicks(4);
            report.put("overworld_screenshot", context.takeScreenshot("thenull-overworld").toString());
            if (capture != null) capture.measure(context, world, report, "overworld");

            sendCommand(context, "null enter");
            context.waitFor(client -> client.level != null && client.level.dimension().equals(SIFT)
                && client.player != null && client.player.getY() < -250, 1200);
            waitForRenderedChunks(context, world, performance);
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                require(player.level().dimension().equals(SIFT), "Server player did not enter sift:sift");
                require(player.level().getBlockState(player.blockPosition().below()).is(Blocks.SCULK),
                    "Real client command did not land on sculk");
                require(player.level().noCollision(player, player.getBoundingBox()),
                    "Real client command placed the player inside terrain");
                require(player.fallDistance == 0, "Enter retained fall damage");
            });
            context.runOnClient(client -> {
                client.player.setYRot(45);
                client.player.setXRot(12);
            });
            context.waitTicks(4);
            report.put("null_enter", "PASSED: real client command packets, server transition, client sift:sift dimension, safe sculk landing, rendered destination chunks");
            report.put("null_surface_screenshot", context.takeScreenshot("thenull-null-surface").toString());
            if (capture != null) capture.measure(context, world, report, "null_surface");

            // Night vision is a temporary development inspection aid. Preserve
            // the native-light screenshot above and remove the effect before
            // photographing ordinary Sift lighting.
            sendCommand(context, "effect give @s minecraft:night_vision 30 0 true");
            context.waitFor(client -> client.player != null
                && client.player.hasEffect(MobEffects.NIGHT_VISION), 1200);
            context.waitTicks(20);
            report.put("null_surface_night_vision_screenshot",
                context.takeScreenshot("thenull-null-surface-development-night-vision").toString());
            report.put("geometry_screenshot_lighting",
                "DEVELOPMENT ONLY: temporary vanilla night vision exposes terrain geometry; native darkness is captured separately");

            BlockPos cave = world.getServer().computeOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                for (int x = 8; x <= 24; x++) {
                    for (int z = 8; z <= 24; z++) {
                        for (int y = -340; y >= -480; y--) {
                            BlockPos feet = new BlockPos(x, y, z);
                            if (player.level().getBlockState(feet).isAir()
                                && player.level().getBlockState(feet.above()).isAir()
                                && player.level().getBlockState(feet.below()).is(Blocks.SCULK)) {
                                return feet;
                            }
                        }
                    }
                }
                return null;
            });
            if (cave != null) {
                world.getServer().runOnServer(server -> {
                    ServerPlayer player = world.getConnection().getServerPlayer();
                    require(player.teleportTo(player.level(), cave.getX() + 0.5, cave.getY(),
                        cave.getZ() + 0.5, Set.<Relative>of(), 45, 5, true), "Cave screenshot placement failed");
                });
                world.getConnection().waitForClientboundPackets();
                waitForRenderedChunks(context, world, performance);
                context.waitTicks(4);
                report.put("cave_night_vision_screenshot",
                    context.takeScreenshot("thenull-null-cave-development-night-vision").toString());
            } else {
                report.put("cave_screenshot", "NOT TESTED: no local two-block-high cave landing in screenshot search area");
            }

            sendCommand(context, "effect clear @s minecraft:night_vision");
            context.waitFor(client -> client.player != null
                && !client.player.hasEffect(MobEffects.NIGHT_VISION), 1200);
            sendCommand(context, "null leave");
            context.waitFor(client -> client.level != null && client.level.dimension().equals(Level.OVERWORLD)
                && client.player != null && client.player.getY() >= -64, 1200);
            waitForRenderedChunks(context, world, performance);
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                require(player.level() == server.overworld(), "Real client /null leave did not return to the Overworld");
                require(player.level().getBlockState(player.blockPosition().below())
                    .isFaceSturdy(player.level(), player.blockPosition().below(), Direction.UP),
                    "Leave has no supporting floor");
                require(player.level().noCollision(player, player.getBoundingBox()), "Leave intersects terrain");
            });
            report.put("null_leave", "PASSED: real client command packets and server/client transition back to a safe Overworld floor");

            // A creative flight view above the ordinary Sift is solely for
            // visual comparison; it is independent of the development commands.
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                ServerLevel sift = server.getLevel(SIFT);
                require(sift != null, "Sift missing for upper screenshot");
                // The Sift has large natural void regions near its origin.
                // Seed 123456789's distant chunk (128,128) contains normal
                // upper terrain, as verified by the dedicated-server sample.
                BlockPos surface = upperTerrain(sift);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                require(player.teleportTo(sift, surface.getX() + 0.5, performance ? surface.getY() + 3 : Math.max(110, surface.getY() + 16),
                    surface.getZ() + 0.5, Set.<Relative>of(), 45, performance ? 12 : 40, true),
                    "Upper Sift screenshot placement failed");
            });
            context.waitFor(client -> client.level != null && client.level.dimension().equals(SIFT)
                && client.player != null && client.player.getY() > (performance ? 0 : 100), 1200);
            waitForRenderedChunks(context, world, performance);
            context.waitTicks(4);
            report.put("upper_sift_screenshot", context.takeScreenshot("thenull-upper-sift").toString());
            if (capture != null) capture.measure(context, world, report, "sift_upper");
            report.put("lighting", "CAPTURED: default Sift ambient light retained; screenshots require visual inspection and do not establish independent underground darkness");
            report.put("same_dimension_sky", "LIMITATION: the native Sift sky renderer remains shared; underground views at limited render distance may still show sky beyond rendered terrain");
            report.put("automated_result", "PASSED");
        } catch (Throwable error) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            report.put("automated_result", "FAILED");
            report.put("error", trace.toString());
            throw new AssertionError("The Null real client verification failed", error);
        } finally {
            Path output = Path.of(System.getProperty("thenull.clientVerificationReport",
                "thenull-client-verification.txt"));
            try {
                Path parent = output.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                StringBuilder text = new StringBuilder();
                report.forEach((key, value) -> text.append(key).append(" = ").append(value).append('\n'));
                Files.writeString(output, text);
            } catch (Exception error) {
                throw new IllegalStateException("Could not write client verification report", error);
            }
        }
    }

    private static void sendCommand(ClientGameTestContext context, String command) {
        context.runOnClient(client -> {
            require(client.getConnection() != null, "Client has no server connection");
            client.getConnection().sendCommand(command);
        });
    }

    private static void waitForRenderedChunks(ClientGameTestContext context, TestSingleplayerContext world, boolean performance) {
        if (!performance) {
            world.getConnection().waitForChunksRender(1200);
            return;
        }
        // Fabric's strict helper requests a full square, but vanilla 26.3
        // sends a rounded tracking area. At distance 8 its corner chunks will
        // never arrive. Check the actual server tracking shape instead.
        int[] attempts = {0};
        context.waitFor(client -> {
            if (client.level == null || client.player == null) return false;
            var center = client.player.chunkPosition();
            int distance = client.options.getEffectiveRenderDistance();
            int missing = 0;
            for (int z = -distance; z <= distance; z++) for (int x = -distance; x <= distance; x++) {
                int cx = center.x() + x, cz = center.z() + z;
                if (net.minecraft.server.level.ChunkTrackingView.isWithinDistance(center.x(), center.z(), distance, cx, cz, true)
                    && client.level.getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) missing++;
            }
            if (attempts[0]++ % 200 == 0) System.out.println("NULL_RENDER_WAIT distance=" + distance
                + " loaded=" + client.level.getChunkSource().getLoadedChunksCount() + " missing=" + missing
                + " fps=" + client.getFps() + " center=" + center);
            return missing == 0;
        }, 1200);
        world.getConnection().waitForChunksRender(false, 1200);
    }

    private static BlockPos safeSurface(ServerLevel level, BlockPos origin, int minimumFeetY) {
        for (int dx = -16; dx <= 16; dx += 4) {
            for (int dz = -16; dz <= 16; dz += 4) {
                BlockPos column = new BlockPos(origin.getX() + dx, 0, origin.getZ() + dz);
                level.getChunkAt(column);
                BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                BlockPos floor = feet.below();
                var support = level.getBlockState(floor);
                if (feet.getY() >= minimumFeetY && support.isFaceSturdy(level, floor, Direction.UP) && support.getFluidState().isEmpty()
                    && !support.is(Blocks.MAGMA_BLOCK) && !support.is(Blocks.CAMPFIRE)
                    && !support.is(Blocks.SOUL_CAMPFIRE) && !support.is(Blocks.CACTUS)
                    && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                    return feet;
                }
            }
        }
        throw new IllegalStateException("No safe natural test floor near " + origin);
    }

    private static BlockPos upperTerrain(ServerLevel level) {
        for (int[] origin : new int[][] {{2056, 2056}, {-2056, -2056}, {4104, -4088}}) {
            for (int dx = -16; dx <= 16; dx += 4) {
                for (int dz = -16; dz <= 16; dz += 4) {
                    int x = origin[0] + dx;
                    int z = origin[1] + dz;
                    level.getChunkAt(new BlockPos(x, 0, z));
                    // Ash and other Sift surface blocks may have a partial
                    // collision shape. This is a flight view, so locate actual
                    // terrain directly instead of requiring a full floor face.
                    for (int y = 220; y >= 1; y--) {
                        BlockPos surface = new BlockPos(x, y, z);
                        if (!level.getBlockState(surface).isAir()) {
                            return surface;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("No upper Sift terrain in the dedicated-server reference sample regions");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
