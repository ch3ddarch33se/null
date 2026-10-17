package com.ch3ddarch33se.thenull.mixin.client;

import com.ch3ddarch33se.thenull.client.NullClientEffects;
import org.joml.Vector4f;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
abstract class NullBackgroundMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vector4f thenull$blackNullBackground(Vector4f clearColor) {
        return NullClientEffects.underground() ? new Vector4f(0.0F, 0.0F, 0.0F, 1.0F) : clearColor;
    }
}
