package com.ch3ddarch33se.thenull.mixin;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.ch3ddarch33se.thenull.worldgen.NullTerrain;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Isolated compatibility hook. AI-assisted original implementation, Apache-2.0. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class SiftTerrainMixin {
    @Inject(method = "buildTerrain", at = @At("RETURN"), cancellable = true)
    private void thenull$lowerTerrain(ChunkAccess chunk, Blender blender, RandomState random,
        StructureManager structures, BiomeManager biomes, WorldGenRegion region, Set<Holder<Biome>> skipped,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> result) {
        if (!region.getLevel().dimension().identifier().equals(Identifier.parse("sift:sift"))) return;
        if (chunk.getMinY() != -768 || chunk.getHeight() != 1024) {
            throw new IllegalStateException("The Null requires its Sift dimension-height extension pack");
        }
        result.setReturnValue(result.getReturnValue().thenApply(generated -> {
            NullTerrain.generate(random.seed(), generated);
            return generated;
        }));
    }
}
