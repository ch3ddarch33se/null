package com.ch3ddarch33se.thenull.mixin.client;

import com.ch3ddarch33se.thenull.client.NullClientEffects;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.Lightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Lightmap.class)
abstract class NullAmbientLightMixin {
    @ModifyExpressionValue(method = "getBrightness", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F"))
    private static float thenull$zeroUndergroundAmbient(float original) {
        return NullClientEffects.underground() ? 0.0F : original;
    }
}
