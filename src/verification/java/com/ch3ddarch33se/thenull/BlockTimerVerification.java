package com.ch3ddarch33se.thenull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;

/** Local integration checks for the persistent lower-region timer rules. */
public final class BlockTimerVerification {
    private BlockTimerVerification() { }

    public static String run(ServerLevel level) {
        BlockPos foreign = new BlockPos(400, -800, 400);
        BlockPos upper = new BlockPos(401, 100, 400);
        BlockPos nativePlaced = new BlockPos(402, -800, 400);
        BlockPos naturalTerrain = new BlockPos(403, -800, 400);
        BlockPos naturalLichen = new BlockPos(404, -800, 400);

        NullBlockTimers.onPlayerPlaced(level, foreign, Blocks.COBBLESTONE.defaultBlockState());
        requireDuration(level, foreign, 90_000, "foreign player block");
        NullBlockTimers.onPlayerPlaced(level, upper, Blocks.COBBLESTONE.defaultBlockState());
        require(NullBlockTimers.remainingMillis(level, upper) < 0,
            "Upper Sift block was incorrectly assigned a Null corrosion timer");

        NullBlockTimers.onPlayerPlaced(level, nativePlaced, Blocks.SCULK.defaultBlockState());
        requireDuration(level, nativePlaced, 3_600_000, "player-placed native block");
        NullBlockTimers.onNativeBlockBroken(level, nativePlaced, Blocks.SCULK.defaultBlockState());
        require(NullBlockTimers.remainingMillis(level, nativePlaced) < 0,
            "Mined player-placed native block was incorrectly scheduled for terrain restoration");

        NullBlockTimers.onNativeBlockBroken(level, naturalTerrain, Blocks.SCULK.defaultBlockState());
        requireDuration(level, naturalTerrain, 60_000, "natural Sculk restoration");

        var lichen = Blocks.GLOW_LICHEN.defaultBlockState()
            .setValue(MultifaceBlock.getFaceProperty(Direction.UP), true)
            .setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true);
        NullBlockTimers.onPlayerPlaced(level, naturalLichen, lichen);
        requireDuration(level, naturalLichen, 3_600_000, "player-placed Glow Lichen");
        require(NullBlockTimers.timersArePersistent(), "Chunk block timers are not serialized");

        return "PASSED: 90-second foreign corrosion, 60-minute native placement expiry, 60-second Sculk restore, upper-region exclusion, persistent chunk data";
    }

    private static void requireDuration(ServerLevel level, BlockPos pos, long duration, String label) {
        long remaining = NullBlockTimers.remainingMillis(level, pos);
        require(remaining > duration - 2_000 && remaining <= duration,
            label + " timer has unexpected remaining time: " + remaining);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
