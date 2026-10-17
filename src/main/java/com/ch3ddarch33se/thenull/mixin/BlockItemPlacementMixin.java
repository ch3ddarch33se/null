package com.ch3ddarch33se.thenull.mixin;

import com.ch3ddarch33se.thenull.NullBlockTimers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class BlockItemPlacementMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void thenull$timePlayerPlacedBlocks(BlockPlaceContext context,
                                                CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer)) return;
        var pos = context.getClickedPos();
        NullBlockTimers.onPlayerPlaced(serverLevel, pos, level.getBlockState(pos));
    }
}
