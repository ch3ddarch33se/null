package com.ch3ddarch33se.thenull.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The temporary provider's only public identity. Future official integration
 * belongs here; no speculative vanilla dimension is registered.
 */
public final class WorldProvider {
    public static final Identifier ID = Identifier.parse("thenull:sift");
    public static final ResourceKey<Level> WORLD = ResourceKey.create(Registries.DIMENSION, ID);
    private WorldProvider() { }
}
