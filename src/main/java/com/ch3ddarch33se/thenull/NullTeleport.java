package com.ch3ddarch33se.thenull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Operator-only access to the buried Null terrain for disposable test worlds. */
public final class NullTeleport {
    private static final ResourceKey<Level> SIFT = ResourceKey.create(
        Registries.DIMENSION, Identifier.parse("sift:sift"));
    // Development-only, in-memory return points, kept separately for each
    // player. A server restart simply falls back to a nearby Overworld surface.
    private static final ConcurrentMap<UUID, BlockPos> RETURN_POINTS = new ConcurrentHashMap<>();
    private static final int[] COLUMN_OFFSETS = {0, 4, -4, 8, -8, 16, -16, 24, -24};

    private NullTeleport() { }

    public static int enter(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel sift = source.getServer().getLevel(SIFT);
        if (sift == null) {
            source.sendFailure(Component.literal("The Sift dimension is not loaded."));
            return 0;
        }

        // Search the predicted Null surface, never place the player in the
        // dark air gap or teleport into solid Sculk. Scan a few nearby columns
        // to cope with varied terrain height and cave openings.
        for (int x = 8; x <= 40; x += 16) {
            for (int z = 8; z <= 40; z += 16) {
                sift.getChunkAt(new BlockPos(x, -320, z));
                for (int y = -250; y >= -440; y--) {
                    BlockPos floor = new BlockPos(x, y, z);
                    if (sift.getBlockState(floor).is(Blocks.SCULK)
                        && isSafeFloor(sift, floor)) {
                        if (player.level().dimension().equals(Level.OVERWORLD)) {
                            RETURN_POINTS.put(player.getUUID(), player.blockPosition());
                        }
                        teleport(player, sift, floor.above());
                        player.sendSystemMessage(Component.literal("Entered the Null test region. Use /null leave to return."));
                        return 1;
                    }
                }
            }
        }
        source.sendFailure(Component.literal(
            "No safe Null surface found. Confirm that the Sift worldgen overlay loaded; test in a fresh world."));
        return 0;
    }

    public static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(Component.literal("Overworld unavailable."));
            return 0;
        }
        BlockPos origin = RETURN_POINTS.getOrDefault(player.getUUID(), player.blockPosition());
        BlockPos arrival = findSafeArrival(overworld, origin);
        if (arrival == null && source.getServer().getRespawnData().globalPos().dimension().equals(Level.OVERWORLD)) {
            arrival = findSafeArrival(overworld, source.getServer().getRespawnData().pos());
        }
        if (arrival == null) {
            source.sendFailure(Component.literal(
                "No safe Overworld landing found near the return position or world spawn."));
            return 0;
        }
        teleport(player, overworld, arrival);
        RETURN_POINTS.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Returned to the Overworld."));
        return 1;
    }

    private static BlockPos findSafeArrival(ServerLevel overworld, BlockPos origin) {
        // Retain safe interiors as return points, then search surface columns.
        overworld.getChunkAt(origin);
        if (isSafeFloor(overworld, origin.below())) {
            return origin;
        }
        for (int dx : COLUMN_OFFSETS) {
            for (int dz : COLUMN_OFFSETS) {
                BlockPos column = new BlockPos(origin.getX() + dx, 0, origin.getZ() + dz);
                // getChunkAt supplies a fully generated LevelChunk before
                // heightmaps or collision blocks are examined.
                overworld.getChunkAt(column);
                BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                if (isSafeFloor(overworld, top.below())) {
                    return top;
                }
            }
        }
        return null;
    }

    private static boolean isSafeFloor(ServerLevel level, BlockPos floor) {
        if (floor.getY() < level.getMinY() || floor.getY() + 2 > level.getMaxY()
            || !level.getWorldBorder().isWithinBounds(floor)) {
            return false;
        }
        BlockState support = level.getBlockState(floor);
        return support.isFaceSturdy(level, floor, Direction.UP)
            && support.getFluidState().isEmpty()
            && !support.is(Blocks.MAGMA_BLOCK)
            && !support.is(Blocks.CACTUS)
            && !support.is(Blocks.CAMPFIRE)
            && !support.is(Blocks.SOUL_CAMPFIRE)
            && level.getBlockState(floor.above()).isAir()
            && level.getBlockState(floor.above(2)).isAir();
    }

    private static void teleport(ServerPlayer player, ServerLevel destination, BlockPos feet) {
        player.stopRiding();
        player.teleportTo(destination, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
            Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }
}
