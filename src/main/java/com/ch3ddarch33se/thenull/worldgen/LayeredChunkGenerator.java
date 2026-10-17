package com.ch3ddarch33se.thenull.worldgen;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.core.BlockPos;

/** Two vanilla generators in one dimension, with a guaranteed unsampled gap.
 * Geometry, biomes and material rules are registry data. This adapter only
 * schedules the occupied bands and adds the proven connected lower tunnels.
 */
public final class LayeredChunkGenerator extends ChunkGenerator {
    public static final MapCodec<LayeredChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
        NoiseGeneratorSettings.CODEC.fieldOf("upper_settings").forGetter(g -> g.upperSettings),
        NoiseGeneratorSettings.CODEC.fieldOf("lower_settings").forGetter(g -> g.lowerSettings),
        RegistryOps.<NormalNoise, LayeredChunkGenerator>retrieveGetter(Registries.NOISE)
    ).apply(instance, LayeredChunkGenerator::new));

    private final Holder<NoiseGeneratorSettings> upperSettings, lowerSettings;
    private final HolderGetter<NormalNoise> noises;
    private final NoiseBasedChunkGenerator upper, lower;
    private volatile States cachedStates;
    private volatile PalettedContainerFactory paletteFactory;
    private record States(long seed, RandomState upper, RandomState lower, CaveSystem caves, CaveLighting lights) { }

    public LayeredChunkGenerator(BiomeSource biomes, Holder<NoiseGeneratorSettings> upperSettings,
        Holder<NoiseGeneratorSettings> lowerSettings, HolderGetter<NormalNoise> noises) {
        super(biomes);
        this.upperSettings = upperSettings;
        this.lowerSettings = lowerSettings;
        this.noises = noises;
        this.upper = new NoiseBasedChunkGenerator(biomes, upperSettings);
        this.lower = new NoiseBasedChunkGenerator(biomes, lowerSettings);
        if (upper.getMinY() != 0 || upper.getGenDepth() != 256
            || lower.getMinY() != NativeNullTerrain.SURFACE_MIN_Y || lower.getGenDepth() != 112
            || upperSettings.value().aquifers().isPresent() || lowerSettings.value().aquifers().isPresent()
            || !upperSettings.value().defaultFluid().isAir() || !lowerSettings.value().defaultFluid().isAir()) {
            throw new IllegalArgumentException("The temporary provider requires dry upper 0..255 and lower -368..-257 bands");
        }
    }

    private States states(RandomState outer) {
        var result = cachedStates;
        if (result == null || result.seed != outer.seed()) synchronized (this) {
            result = cachedStates;
            if (result == null || result.seed != outer.seed()) {
                var settings=CaveSettings.load();
                result = new States(outer.seed(), RandomState.create(noises, outer.seed(), upperSettings.value()),
                    RandomState.create(noises, outer.seed(), lowerSettings.value()),new CaveSystem(outer.seed(),settings),
                    new CaveLighting(outer.seed(),settings));
                cachedStates = result;
            }
        }
        return result;
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    public RandomState levelRandomState(RandomState fallback) { return states(fallback).upper; }

    public void garbageCollectLowerBuffers() {
        var states = cachedStates;
        if (states != null) states.lower.garbageCollect();
    }

    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState random, long seed) {
        return super.createState(structures, states(random).upper, seed);
    }

    @Override public CompletableFuture<ChunkAccess> createBiomes(RandomState random, Blender blender,
        StructureManager structures, ChunkAccess chunk) {
        return upper.createBiomes(states(random).upper, blender, structures, chunk);
    }

    @Override public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState random,
        StructureManager structures, BiomeManager biomes, WorldGenRegion region, Set<Holder<Biome>> skipped) {
        var states = states(random);
        if (paletteFactory == null) synchronized (this) {
            if (paletteFactory == null) paletteFactory = PalettedContainerFactory.create(region.getLevel().registryAccess());
        }
        long started = System.nanoTime();
        return NativeBand.generate(lower, states.lower, chunk, blender, structures, biomes, paletteFactory, skipped)
            .thenCompose(generated -> {
                NativeNullTerrain.recordNative(System.nanoTime() - started);
                NativeNullTerrain.finish(states.caves, states.lights, generated, lowerSettings.value().defaultBlock());
                return NativeBand.generate(upper, states.upper, generated, blender, structures, biomes, paletteFactory, skipped);
            }).thenApply(generated -> {
                Heightmap.primeHeightmaps(generated, Set.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
                return generated;
            });
    }

    @Override public void spawnOriginalMobs(WorldGenRegion region) { upper.spawnOriginalMobs(region); }
    @Override public int getGenDepth() { return 2016; }
    @Override public int getMinY() { return NativeNullTerrain.MIN_Y; }
    @Override public int getSeaLevel() { return upper.getSeaLevel(); }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return Math.max(0, upper.getBaseHeight(x, z, type, height, states(random).upper));
    }

    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        var states = states(random);
        var upperColumn = upper.getBaseColumn(x, z, height, states.upper);
        var lowerColumn = lower.getBaseColumn(x, z, height, states.lower);
        int bottom = Math.max(getMinY(), height.getMinY()), top = Math.min(255, height.getMaxY());
        BlockState[] blocks = new BlockState[Math.max(0, top - bottom + 1)];
        Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
        for (int y = bottom; y <= top; y++) {
            blocks[y - bottom] = y >= 0 ? upperColumn.getBlock(y)
                : y >= -4 || y <= CentralChamber.BEDROCK_MAX_Y ? Blocks.BEDROCK.defaultBlockState()
                : y >= NativeNullTerrain.GAP_MIN_Y ? Blocks.AIR.defaultBlockState()
                : y == CentralChamber.FLOOR_Y ? lowerSettings.value().defaultBlock()
                : y >= CentralChamber.AIR_MIN_Y && y <= CentralChamber.AIR_MAX_Y
                    && CentralChamber.containsColumn(x, z)
                    ? CentralChamber.isOrbShell(x, y, z) ? Blocks.TINTED_GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState()
                : y >= NativeNullTerrain.SURFACE_MIN_Y ? lowerColumn.getBlock(y)
                : lowerSettings.value().defaultBlock();
        }
        return new NoiseColumn(bottom, blocks);
    }

    @Override public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos, SamplerContext context) {
        var states = states(random);
        if (pos.getY() < -256) lower.addDebugScreenInfo(lines, states.lower, pos, context);
        else upper.addDebugScreenInfo(lines, states.upper, pos, context);
    }
}
