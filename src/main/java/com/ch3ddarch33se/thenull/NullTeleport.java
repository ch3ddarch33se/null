package com.ch3ddarch33se.thenull;

import java.util.Set;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Operator-only access to the buried Null terrain for disposable test worlds. */
public final class NullTeleport {
    private static final ResourceKey<Level> SIFT = ResourceKey.create(
        Registries.DIMENSION, Identifier.parse("sift:sift"));

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
                        && sift.getBlockState(floor.above()).isAir()
                        && sift.getBlockState(floor.above(2)).isAir()) {
                        player.teleportTo(sift, x + 0.5, y + 1.0, z + 0.5,
                            Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
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
        int x = (int) player.getX();
        int z = (int) player.getZ();
        BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
            new BlockPos(x, 0, z));
        player.teleportTo(overworld, x + 0.5, top.getY() + 2.0, z + 0.5,
            Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
        player.sendSystemMessage(Component.literal("Returned to the Overworld."));
        return 1;
    }
}
