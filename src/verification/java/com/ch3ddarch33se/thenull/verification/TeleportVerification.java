package com.ch3ddarch33se.thenull.verification;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ch3ddarch33se.thenull.NullPortal;
import com.ch3ddarch33se.thenull.PlayerData;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Exercises registered commands against real server player and level objects. */
public final class TeleportVerification {
    private static final ResourceKey<Level> SIFT = ResourceKey.create(
        Registries.DIMENSION, com.ch3ddarch33se.thenull.worldgen.WorldProvider.ID);

    private TeleportVerification() { }

    public static Map<String, String> run(MinecraftServer server) throws Exception {
        require(server.isSameThread(), "Teleport verification must run on the server thread");
        ServerLevel overworld = server.overworld();
        require(server.getLevel(SIFT) != null, "Sift dimension is missing");
        BlockPos firstOrigin = safeSurface(overworld, server.getRespawnData().pos(), null);
        BlockPos secondOrigin = safeSurface(overworld, firstOrigin.offset(4, 0, 4), firstOrigin);
        ServerPlayer first = createPlayer(server, overworld, firstOrigin, "NullTestOne");
        ServerPlayer second = null;
        try {
            second = createPlayer(server, overworld, secondOrigin, "NullTestTwo");

            CommandSourceStack unprivileged = first.createCommandSourceStack()
                .withPermission(PermissionSet.NO_PERMISSIONS).withSuppressedOutput();
            var nullCommand = server.getCommands().getDispatcher().getRoot().getChild("null");
            require(nullCommand != null, "The /null command was not registered");
            require(!nullCommand.canUse(unprivileged), "Non-operator can access the /null command");
            boolean rejected = false;
            try {
                server.getCommands().getDispatcher().execute("null enter", unprivileged);
            } catch (CommandSyntaxException expected) {
                rejected = true;
            }
            require(rejected && first.level() == overworld,
                "Non-operator /null enter was not rejected without teleporting");

            enter(server, first);
            BlockPos firstNullPosition = first.blockPosition();
            PlayerData.Origin saved = PlayerData.origin(first);
            require(saved != null && saved.dimension().equals("minecraft:overworld")
                && saved.x() == firstOrigin.getX() + 0.5 && saved.y() == firstOrigin.getY()
                && saved.z() == firstOrigin.getZ() + 0.5,
                "Entry did not persist the player's exact origin in PlayerData");
            require(PlayerData.RETURN_ORIGIN.isPersistent(), "Player origin attachment is not serialized to NBT");
            ServerLevel sift = server.getLevel(SIFT);
            var lowerSpawn = new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(SIFT, new BlockPos(20, -800, 20), 0.0F, 0.0F), false);
            first.setRespawnPosition(lowerSpawn, false);
            require(first.getRespawnConfig() == null, "A Null bed/anchor spawn was accepted");
            var upperSpawn = new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(SIFT, new BlockPos(20, 100, 20), 0.0F, 0.0F), false);
            first.setRespawnPosition(upperSpawn, false);
            require(first.getRespawnConfig() != null && first.getRespawnConfig().respawnData().pos().getY() == 100,
                "The Null spawn restriction also blocked upper Sift spawn points");
            require(NullPortal.isIntact(sift), "Arrival did not create the main portal");
            sift.setBlock(new BlockPos(1, NullPortal.Y, 0), Blocks.AIR.defaultBlockState(), 3);
            require(!NullPortal.isIntact(sift), "The main portal is not breakable");
            int strandedResult = server.getCommands().getDispatcher().execute("null leave", operatorSource(first));
            require(strandedResult == 0 && first.level().dimension().equals(SIFT),
                "A broken main portal did not strand existing Null players");
            enter(server, second);
            require(NullPortal.isIntact(sift), "A new Overworld arrival did not regenerate the main portal");
            require(first.level().dimension().equals(SIFT)
                && first.blockPosition().equals(firstNullPosition),
                "Entering with another player changed the first player");

            leave(server, second, secondOrigin);
            require(first.level().dimension().equals(SIFT),
                "Leaving with one player changed another player's dimension");
            int surfaceResult = server.getCommands().getDispatcher().execute("null surface", operatorSource(first));
            require(surfaceResult == 1 && first.level().dimension().equals(SIFT) && first.getY() > 0,
                "/null surface did not reach the upper region in the same world");
            verifyLanding(first);
            leave(server, first, firstOrigin);
            verifyDeathExperience(server, first, server.getLevel(SIFT));

            return Map.of(
                "null_enter", "PASSED: registered command moved two server players into thenull:sift onto exposed sculk, with clear headroom and reset motion/fall distance",
                "null_leave", "PASSED: exact per-player origins returned; breaking the central portal stranded current players and a new Overworld arrival regenerated it",
                "null_surface", "PASSED: safe upper landing in the same dimension, retaining the original Overworld return point",
                "respawn_rules", "PASSED: lower Null spawn points are rejected while upper Sift spawn points remain allowed",
                "death_experience", "PASSED: death below the separator clears level, total experience and progress before drops",
                "permissions", "PASSED: non-operator source could not use /null and command execution was rejected",
                "multiplayer", "PASSED: distinct player UUIDs retained independent return positions; teleporting one did not move the other",
                "teleport_client", "NOT TESTED: synthetic server players queue packets without a connected Minecraft client; rendering and client handshake are not exercised");
        } finally {
            removePlayer(first);
            if (second != null) {
                removePlayer(second);
            }
        }
    }

    private static void verifyDeathExperience(MinecraftServer server, ServerPlayer player, ServerLevel sift) {
        require(player.teleportTo(sift, 0.5, -700, 0.5, Set.<Relative>of(), 0, 0, true),
            "Could not place death-verification player inside The Null");
        player.experienceLevel = 17;
        player.totalExperience = 1234;
        player.experienceProgress = 0.75F;
        player.die(sift.damageSources().inWall());
        require(player.experienceLevel == 0 && player.totalExperience == 0 && player.experienceProgress == 0.0F,
            "Death in The Null did not wipe XP before vanilla drop handling");
    }

    private static ServerPlayer createPlayer(MinecraftServer server, ServerLevel level,
        BlockPos origin, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server,
            new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        level.addNewPlayer(player);
        return player;
    }

    private static void enter(MinecraftServer server, ServerPlayer player) throws Exception {
        player.setDeltaMovement(new Vec3(1, -8, 2));
        player.fallDistance = 55;
        int result = server.getCommands().getDispatcher().execute("null enter", operatorSource(player));
        require(result == 1, "/null enter did not report success");
        require(player.level().dimension().equals(SIFT), "/null enter left the player outside thenull:sift");
        require(player.getY() < -250 && player.getY() > -440,
            "/null enter placed the player outside the exposed Null surface range");
        require(player.level().getBlockState(player.blockPosition().below()).is(Blocks.SCULK),
            "/null enter did not land on sculk terrain");
        verifyLanding(player);
        // The landing column must remain open above the Null until the barrier;
        // this distinguishes an exposed upper surface from an interior cave.
        for (int y = player.blockPosition().getY() + 2; y < -5; y++) {
            require(player.level().getBlockState(new BlockPos(player.blockPosition().getX(), y,
                player.blockPosition().getZ())).isAir(), "/null enter landed in an enclosed cave");
        }
    }

    private static void leave(MinecraftServer server, ServerPlayer player, BlockPos expected) throws Exception {
        player.setDeltaMovement(new Vec3(-2, -6, 1));
        player.fallDistance = 72;
        int result = server.getCommands().getDispatcher().execute("null leave", operatorSource(player));
        require(result == 1, "/null leave did not report success");
        require(player.level() == server.overworld(), "/null leave did not return to the Overworld");
        require(player.blockPosition().equals(expected), "/null leave returned to another player's position");
        verifyLanding(player);
    }

    private static CommandSourceStack operatorSource(ServerPlayer player) {
        return player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER)
            .withSuppressedOutput();
    }

    private static void verifyLanding(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        require(player.level().getBlockState(feet.below()).isFaceSturdy(player.level(), feet.below(), Direction.UP),
            "Teleport landing has no full supporting floor");
        require(player.level().getBlockState(feet.below()).getFluidState().isEmpty(),
            "Teleport landing is in fluid");
        require(player.level().getBlockState(feet).isAir()
            && player.level().getBlockState(feet.above()).isAir(), "Teleport landing has insufficient headroom");
        require(player.level().noCollision(player, player.getBoundingBox()), "Player intersects terrain after teleport");
        require(player.getDeltaMovement().lengthSqr() == 0 && player.fallDistance == 0,
            "Teleport preserved unsafe momentum or fall distance");
    }

    private static BlockPos safeSurface(ServerLevel level, BlockPos origin, BlockPos excluded) {
        for (int radius = 0; radius <= 24; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos column = new BlockPos(origin.getX() + dx, 0, origin.getZ() + dz);
                    level.getChunkAt(column);
                    BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                    BlockPos floor = feet.below();
                    var support = level.getBlockState(floor);
                    if (!feet.equals(excluded) && support.isFaceSturdy(level, floor, Direction.UP)
                        && support.getFluidState().isEmpty() && !support.is(Blocks.MAGMA_BLOCK)
                        && !support.is(Blocks.CACTUS) && !support.is(Blocks.CAMPFIRE)
                        && !support.is(Blocks.SOUL_CAMPFIRE) && level.getBlockState(feet).isAir()
                        && level.getBlockState(feet.above()).isAir()) {
                        return feet;
                    }
                }
            }
        }
        throw new IllegalStateException("No safe Overworld origin for teleport verification near " + origin);
    }

    private static void removePlayer(ServerPlayer player) {
        player.level().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        player.getTextFilter().leave();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
