package com.ch3ddarch33se.thenull.mixin;

import com.ch3ddarch33se.thenull.NullPortal;
import com.ch3ddarch33se.thenull.PlayerData;
import com.ch3ddarch33se.thenull.worldgen.WorldProvider;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerNullMixin {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
        at = @At("HEAD"))
    private void thenull$captureOriginOnEntry(ServerLevel destination, double x, double y, double z,
                                               Set<Relative> relatives, float yaw, float pitch,
                                               boolean avoidSuffocation, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!player.level().dimension().equals(WorldProvider.WORLD)
            && destination.dimension().equals(WorldProvider.WORLD)) {
            if (player.level().dimension().equals(Level.OVERWORLD)) NullPortal.regenerate(destination);
            PlayerData.capture(player);
        }
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void thenull$denyNullRespawn(ServerPlayer.RespawnConfig config, boolean sendMessage,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (config != null && config.respawnData().dimension().equals(WorldProvider.WORLD)
            && config.respawnData().pos().getY() < -4) ci.cancel();
    }
}
