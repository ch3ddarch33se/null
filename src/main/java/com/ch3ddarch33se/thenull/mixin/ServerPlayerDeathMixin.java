package com.ch3ddarch33se.thenull.mixin;

import com.ch3ddarch33se.thenull.worldgen.WorldProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerDeathMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void thenull$wipeExperienceInNull(DamageSource source, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.level().dimension().equals(WorldProvider.WORLD) && player.getY() < -4.0) {
            player.experienceLevel = 0;
            player.totalExperience = 0;
            player.experienceProgress = 0.0F;
        }
    }
}
