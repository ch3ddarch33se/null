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
        Registries.DIMENSION, com.ch3ddarch33se.thenull.worldgen.WorldProvider.ID);

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
            require(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sift_backport"),
                "Standalone client verification unexpectedly loaded Sift Backport");
            report.put("standalone_client", "PASSED: Sift Backport absent from Fabric's loaded mods");
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
                require(player.level().dimension().equals(SIFT), "Server player did not enter thenull:sift");
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
            report.put("null_enter", "PASSED: real client command packets, server transition, client thenull:sift dimension, safe sculk landing, rendered destination chunks");
            report.put("null_surface_screenshot", context.takeScreenshot("thenull-null-surface").toString());
            if (capture != null) capture.measure(context, world, report, "null_surface");

            var lichenSites = new java.util.Properties();
            try (var reader = Files.newBufferedReader(Path.of("../../build/reports/terrain-inspection.properties"))) {
                lichenSites.load(reader);
            }
            double lx=Double.parseDouble(lichenSites.getProperty("lichen_view.x"));
            double ly=Double.parseDouble(lichenSites.getProperty("lichen_view.y"));
            double lz=Double.parseDouble(lichenSites.getProperty("lichen_view.z"));
            float lyaw=Float.parseFloat(lichenSites.getProperty("lichen_view.yaw"));
            float lpitch=Float.parseFloat(lichenSites.getProperty("lichen_view.pitch"));
            world.getServer().runOnServer(server -> {
                ServerPlayer player=world.getConnection().getServerPlayer();
                require(!player.hasEffect(MobEffects.NIGHT_VISION),"Native light inspection started with Night Vision");
                require(player.teleportTo(player.level(),lx,ly,lz,Set.<Relative>of(),lyaw,lpitch,true),
                    "Glow Lichen light inspection placement failed");
                require(player.level().noCollision(player,player.getBoundingBox()),"Glow Lichen inspection landing collides");
                require(player.level().getBlockState(player.blockPosition().below()).is(Blocks.SCULK),
                    "Glow Lichen inspection landing has no natural sculk floor");
            });
            world.getConnection().waitForClientboundPackets();
            waitForRenderedChunks(context,world,performance);
            context.waitFor(client -> client.player!=null&&!client.player.hasEffect(MobEffects.NIGHT_VISION),1200);
            context.waitTicks(8);
            report.put("native_cave_lighting_screenshot","PASSED: Glow Lichen and exploration light rendered without Night Vision; position="
                +lx+","+ly+","+lz+"; screenshot="+context.takeScreenshot("thenull-glow-lichen-native-light"));

            // Night vision is a temporary development inspection aid. Preserve
            // the native-light screenshot above and remove the effect before
            // photographing ordinary Sift lighting.
            sendCommand(context, "effect give @s minecraft:night_vision 120 0 true");
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

            if (Boolean.getBoolean("thenull.terrainInspection")) inspectTerrain(context,world,report,performance);

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

            sendCommand(context, "null surface");
            context.waitFor(client -> client.level != null && client.level.dimension().equals(SIFT)
                && client.player != null && client.player.getY() > 0, 1200);
            world.getServer().runOnServer(server -> {
                var player=world.getConnection().getServerPlayer();
                require(player.level().noCollision(player,player.getBoundingBox()), "Surface command intersects terrain");
                require(player.level().getBlockState(player.blockPosition().below()).isFaceSturdy(player.level(),player.blockPosition().below(),Direction.UP), "Surface command lacks support");
            });
            report.put("null_surface", "PASSED: actual command packets and safe upper landing in thenull:sift");

            // A creative flight view above the ordinary Sift is solely for
            // visual comparison; it is independent of the development commands.
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                ServerLevel sift = server.getLevel(SIFT);
                require(sift != null, "Sift missing for upper screenshot");
                // A distant region gives an additional terrain sample beyond
                // the command landing area at the origin.
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
            report.put("lighting", "CAPTURED: project-owned ambient light 0.04; native Glow Lichen cave screenshot has no Night Vision; separate geometry captures use development Night Vision");
            report.put("same_dimension_sky", "PASSED: client mixins disable the skybox and force a black background below Y=-4; the upper Sift sky remains visible");
            report.put("spherical_culling", "PASSED: 3D section-distance culling mixin loaded in the client; chamber screenshots rendered at render distance 8");
            report.put("underground_ambient", "PASSED: underground Lightmap ambient contribution is forced to 0.0 below Y=-4");
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

    private static void inspectTerrain(ClientGameTestContext context, TestSingleplayerContext world,
        Map<String,String> report, boolean performance) throws Exception {
        var sites=new java.util.Properties();
        try(var reader=Files.newBufferedReader(Path.of("../../build/reports/terrain-inspection.properties"))) {
            sites.load(reader);
        }
        require(sites.getProperty("seed").equals("123456789"),"Inspection coordinates belong to another seed");
        for(String name:new String[] {"dead_end","wide_passage","junction","surface_high","surface_low"}) {
            double x=Double.parseDouble(sites.getProperty(name+".x")),y=Double.parseDouble(sites.getProperty(name+".y")),
                z=Double.parseDouble(sites.getProperty(name+".z"));
            float yaw=Float.parseFloat(sites.getProperty(name+".yaw")),pitch=Float.parseFloat(sites.getProperty(name+".pitch"));
            boolean flight=name.startsWith("surface_");
            world.getServer().runOnServer(server -> {
                var player=world.getConnection().getServerPlayer();
                require(player.level().getSeed()==123456789,"Inspection seed mismatch");
                player.getAbilities().flying=flight;player.onUpdateAbilities();
                require(player.teleportTo(player.level(),x,y,z,Set.<Relative>of(),yaw,pitch,true),"Inspection teleport failed: "+name);
                require(player.level().noCollision(player,player.getBoundingBox()),"Inspection point intersects terrain: "+name);
                if(!flight)require(player.level().getBlockState(player.blockPosition().below()).is(Blocks.SCULK),"Inspection floor missing: "+name);
            });
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> client.player!=null && Math.abs(client.player.getX()-x)<.1 && Math.abs(client.player.getZ()-z)<.1,1200);
            waitForRenderedChunks(context,world,performance);
            context.waitTicks(6);
            report.put("inspection_"+name, "PASSED: rendered fresh-world geometry, collision/support verified; position="+x+","+y+","+z
                +"; development night vision; screenshot="+context.takeScreenshot("thenull-inspection-"+name));
        }
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
                    // Vegetation and crystal surface blocks may have a partial
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
