package com.ch3ddarch33se.thenull.mixin.client;

import com.ch3ddarch33se.thenull.client.NullClientEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class NullSkyboxMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void thenull$hideSkybox(ClientLevel level, float partialTick, Camera camera,
                                    SkyRenderState state, CallbackInfo ci) {
        if (NullClientEffects.underground()) state.skybox = DimensionType.Skybox.NONE;
    }
}
