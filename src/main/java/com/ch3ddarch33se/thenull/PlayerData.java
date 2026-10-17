package com.ch3ddarch33se.thenull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Persistent per-player return point, serialized with the player's NBT. */
public final class PlayerData {
    public record Origin(String dimension, double x, double y, double z, float yaw, float pitch) {
        static final Codec<Origin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(Origin::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(Origin::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Origin::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Origin::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(Origin::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(Origin::pitch)
        ).apply(instance, Origin::new));
    }

    public static final AttachmentType<Origin> RETURN_ORIGIN = AttachmentRegistry.<Origin>builder()
        .persistent(Origin.CODEC)
        .copyOnDeath()
        .buildAndRegister(Identifier.parse("thenull:return_origin"));

    private PlayerData() { }

    public static void capture(ServerPlayer player) {
        player.setAttached(RETURN_ORIGIN, new Origin(player.level().dimension().identifier().toString(),
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
    }

    public static Origin origin(ServerPlayer player) { return player.getAttached(RETURN_ORIGIN); }
    public static void clear(ServerPlayer player) { player.removeAttached(RETURN_ORIGIN); }
}
