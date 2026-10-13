package com.ch3ddarch33se.thenull.mixin;

import com.ch3ddarch33se.thenull.worldgen.LayeredChunkGenerator;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 26.3 assigns a zero router to non-noise generators. Expose our native upper
 * router to vanilla climate queries and native buffer cleanup as well.
 * This provider adapter contains no external Sift assumptions or mod hooks.
 */
@Mixin(ChunkMap.class)
public abstract class LayeredRandomStateMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JZLnet/minecraft/world/level/block/state/BlockState;ILnet/minecraft/world/level/levelgen/NoiseRouter;)Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState thenull$providerRandomState(RandomState fallback,
        @Local(argsOnly = true) ChunkGenerator generator) {
        return generator instanceof LayeredChunkGenerator layered ? layered.levelRandomState(fallback) : fallback;
    }
}
