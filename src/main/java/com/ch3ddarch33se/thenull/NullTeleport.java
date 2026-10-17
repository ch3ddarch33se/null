package com.ch3ddarch33se.thenull;

import java.util.Set;
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
import com.ch3ddarch33se.thenull.worldgen.CentralChamber;
import com.ch3ddarch33se.thenull.worldgen.WorldProvider;

/** Operator commands and portal traversal for the two regions in one dimension. */
public final class NullTeleport {
    private static final ResourceKey<Level> SIFT = WorldProvider.WORLD;
    private static final int[] COLUMN_OFFSETS = {0, 4, -4, 8, -8, 16, -16, 24, -24};
    // The central chamber removes the original exposed surface around (0, 0).
    private static final int[] NULL_SURFACE_X = {400, 416, 432};
    private static final int[] NULL_SURFACE_Z = {400, 416, 432};

    private NullTeleport() { }

    public static int enter(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel sift = source.getServer().getLevel(SIFT);
        if (sift == null) {
            source.sendFailure(Component.literal("The Sift dimension is not loaded."));
            return 0;
        }
        boolean fromOverworld = player.level().dimension().equals(Level.OVERWORLD);
        if (!player.level().dimension().equals(SIFT) && fromOverworld) NullPortal.regenerate(sift);

        for (int x : NULL_SURFACE_X) for (int z : NULL_SURFACE_Z) {
            sift.getChunkAt(new BlockPos(x, -320, z));
            for (int y = -250; y >= -440; y--) {
                BlockPos floor = new BlockPos(x, y, z);
                if (sift.getBlockState(floor).is(Blocks.SCULK) && isSafeFloor(sift, floor)) {
                    if (!player.level().dimension().equals(SIFT)) PlayerData.capture(player);
                    teleport(player, sift, floor.above().getX() + 0.5, floor.above().getY(),
                        floor.above().getZ() + 0.5, player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("Entered the Null test region. Use /null leave to return."));
                    return 1;
                }
            }
        }
        source.sendFailure(Component.literal("No safe Null surface found. Test the native generator in a fresh world."));
        return 0;
    }

    public static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.level().dimension().equals(SIFT) && !NullPortal.isIntact(source.getServer().getLevel(SIFT))) {
            source.sendFailure(Component.literal("The main portal is broken; all Null travelers are stranded."));
            return 0;
        }
        if (!returnToOrigin(player)) {
            source.sendFailure(Component.literal("The saved origin dimension is unavailable and no safe fallback exists."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Returned to your saved origin."));
        return 1;
    }

    public static boolean leaveFromPortal(ServerPlayer player) {
        return NullPortal.isIntact(player.level().getServer().getLevel(SIFT)) && returnToOrigin(player);
    }

    private static boolean returnToOrigin(ServerPlayer player) {
        PlayerData.Origin origin = PlayerData.origin(player);
        if (origin != null) {
            try {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(origin.dimension()));
                ServerLevel destination = player.level().getServer().getLevel(key);
                if (destination == null) return false;
                destination.getChunkAt(BlockPos.containing(origin.x(), origin.y(), origin.z()));
                teleport(player, destination, origin.x(), origin.y(), origin.z(), origin.yaw(), origin.pitch());
                PlayerData.clear(player);
                return true;
            } catch (RuntimeException invalidOrigin) {
                TheNull.LOGGER.warn("Could not restore saved player origin {}", origin.dimension(), invalidOrigin);
                return false;
            }
        }

        ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return false;
        BlockPos safe = findSafeArrival(overworld, player.blockPosition());
        if (safe == null) return false;
        teleport(player, overworld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
            player.getYRot(), player.getXRot());
        return true;
    }

    public static int surface(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getServer().getLevel(SIFT);
        if (level == null) {
            source.sendFailure(Component.literal("The Null's temporary Sift world is not loaded."));
            return 0;
        }
        for (int x = 8; x <= 40; x += 4) for (int z = 8; z <= 40; z += 4) {
            level.getChunkAt(new BlockPos(x, 0, z));
            for (int y = 250; y >= 1; y--) {
                BlockPos floor = new BlockPos(x, y, z);
                if (isSafeFloor(level, floor)) {
                    if (!player.level().dimension().equals(SIFT)) PlayerData.capture(player);
                    teleport(player, level, floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                    return 1;
                }
            }
        }
        source.sendFailure(Component.literal("No safe upper surface found near the development landing area."));
        return 0;
    }

    private static BlockPos findSafeArrival(ServerLevel overworld, BlockPos origin) {
        overworld.getChunkAt(origin);
        if (isSafeFloor(overworld, origin.below())) return origin;
        for (int dx : COLUMN_OFFSETS) for (int dz : COLUMN_OFFSETS) {
            BlockPos column = new BlockPos(origin.getX() + dx, 0, origin.getZ() + dz);
            overworld.getChunkAt(column);
            BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            if (isSafeFloor(overworld, top.below())) return top;
        }
        return null;
    }

    private static boolean isSafeFloor(ServerLevel level, BlockPos floor) {
        if (floor.getY() < level.getMinY() || floor.getY() + 2 > level.getMaxY()
            || !level.getWorldBorder().isWithinBounds(floor)) return false;
        BlockState support = level.getBlockState(floor);
        return support.isFaceSturdy(level, floor, Direction.UP) && support.getFluidState().isEmpty()
            && !support.is(Blocks.MAGMA_BLOCK) && !support.is(Blocks.CACTUS)
            && !support.is(Blocks.CAMPFIRE) && !support.is(Blocks.SOUL_CAMPFIRE)
            && level.getBlockState(floor.above()).isAir() && level.getBlockState(floor.above(2)).isAir();
    }

    private static void teleport(ServerPlayer player, ServerLevel destination,
                                 double x, double y, double z, float yaw, float pitch) {
        player.stopRiding();
        player.teleportTo(destination, x, y, z, Set.<Relative>of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }
}
