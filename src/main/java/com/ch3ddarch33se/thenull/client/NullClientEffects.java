package com.ch3ddarch33se.thenull.client;

import com.ch3ddarch33se.thenull.worldgen.WorldProvider;
import net.minecraft.client.Minecraft;

/** Client predicates shared by The Null's renderer mixins. */
public final class NullClientEffects {
    private NullClientEffects() { }

    public static boolean underground() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.dimension().equals(WorldProvider.WORLD)
            && minecraft.player != null && minecraft.player.getY() < -4.0;
    }

}
