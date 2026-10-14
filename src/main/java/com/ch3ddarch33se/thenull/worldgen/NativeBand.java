package com.ch3ddarch33se.thenull.worldgen;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Runs vanilla's complete terrain/material/carver pass in its actual band.
 * 26.3 material evaluation otherwise scans the entire dimension, including its
 * empty gap. The temporary chunk confines both sampling and palette writes.
 * Features, structures and lighting still run on the real chunk afterwards.
 */
public final class NativeBand {
    private NativeBand() { }

    public static CompletableFuture<ChunkAccess> generate(NoiseBasedChunkGenerator generator,
        RandomState random, ChunkAccess target, Blender blender, StructureManager structures,
        BiomeManager biomes, PalettedContainerFactory factory, Set<Holder<Biome>> skipped) {
        var noise = generator.generatorSettings().value().noiseSettings();
        var band = new ProtoChunk(target.getPos(), UpgradeData.EMPTY,
            LevelHeightAccessor.create(noise.minY(), noise.height()), factory, null);
        band.fillBiomesFromNoise(target);
        // A null region is supported by vanilla's carver pass: biome settings
        // come from this delegate's own biome source instead of neighbour caches.
        return generator.buildTerrain(band, blender, random, structures, biomes, null, skipped)
            .thenApply(generated -> {
                for (int y = noise.minY(); y < noise.minY() + noise.height(); y += 16) {
                    int index = target.getSectionIndex(y);
                    // Biomes were copied from the target before generation.
                    // Transfer the section with vanilla's maintained counts;
                    // reconstructing it would scan its states a second time.
                    target.getSections()[index] = generated.getSection(generated.getSectionIndex(y));
                }
                generated.getBlockEntitiesPos().forEach(pos -> {
                    var nbt = generated.getBlockEntityNbt(pos);
                    if (nbt != null) target.setBlockEntityNbt(nbt);
                });
                return target;
            });
    }
}
