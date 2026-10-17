package com.ch3ddarch33se.thenull;

import com.ch3ddarch33se.thenull.worldgen.CentralChamber;
import com.ch3ddarch33se.thenull.worldgen.WorldProvider;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Breakable vanilla-block portal at the floor of the central chamber. */
public final class NullPortal {
    public static final int Y = CentralChamber.AIR_MIN_Y;
    private static final BlockPos CORE = new BlockPos(0, Y, 0);

    private NullPortal() { }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerLevel level) || !world.dimension().equals(WorldProvider.WORLD)
                || !hit.getBlockPos().equals(CORE) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (!isIntact(level)) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "The main portal is broken. A new arrival from the Overworld must restore it."));
                return InteractionResult.FAIL;
            }
            return NullTeleport.leaveFromPortal(serverPlayer) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        });
    }

    public static void regenerate(ServerLevel level) {
        level.getChunkAt(CORE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            level.setBlock(new BlockPos(dx, Y, dz), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        }
    }

    public static boolean isIntact(ServerLevel level) {
        if (level == null) return false;
        level.getChunkAt(CORE);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!level.getBlockState(new BlockPos(dx, Y, dz)).is(Blocks.CRYING_OBSIDIAN)) return false;
        }
        return true;
    }

    public static boolean isNullDimension(Level level) { return level.dimension().equals(WorldProvider.WORLD); }
}
