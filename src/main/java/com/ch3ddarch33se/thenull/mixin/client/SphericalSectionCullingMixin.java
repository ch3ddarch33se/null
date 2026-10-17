package com.ch3ddarch33se.thenull.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft 26.3's section graph uses X/Z-only view distance. */
@Mixin(SectionOcclusionGraph.class)
abstract class SphericalSectionCullingMixin {
    @Shadow private ViewArea viewArea;

    @Inject(method = "isInViewDistance", at = @At("HEAD"), cancellable = true)
    private void thenull$useSphericalViewDistance(long section, long cameraSection,
                                                  CallbackInfoReturnable<Boolean> cir) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null
            || !minecraft.level.dimension().equals(com.ch3ddarch33se.thenull.worldgen.WorldProvider.WORLD)
            || minecraft.player == null) return;

        int dx = SectionPos.x(section) - SectionPos.x(cameraSection);
        int dy = SectionPos.y(section) - SectionPos.y(cameraSection);
        int dz = SectionPos.z(section) - SectionPos.z(cameraSection);
        // Section distance is measured center-to-center, so the configured
        // inclusive outer section is already represented by this radius.
        // Dropping the extra shell reduces the visible candidate pool.
        int radius = viewArea.getViewDistance();
        cir.setReturnValue((long) dx * dx + (long) dy * dy + (long) dz * dz <= (long) radius * radius);
    }
}
