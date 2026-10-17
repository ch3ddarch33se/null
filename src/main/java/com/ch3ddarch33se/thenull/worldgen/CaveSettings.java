package com.ch3ddarch33se.thenull.worldgen;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;

/** Immutable settings, read once per launch. Changes require fresh chunks. */
public record CaveSettings(double cavernScale, double wallLichen, double floorLichen) {
    public static final CaveSettings DEFAULT = new CaveSettings(1.12, 0.55, 0.20);
    private static CaveSettings loaded;

    public CaveSettings {
        if (!Double.isFinite(cavernScale) || cavernScale < .7 || cavernScale > 1.25
            || !Double.isFinite(wallLichen) || wallLichen < 0 || wallLichen > 1
            || !Double.isFinite(floorLichen) || floorLichen < 0 || floorLichen > 1) {
            throw new IllegalArgumentException("Cavern scale must be 0.7..1.25; lichen densities 0..1");
        }
    }

    public static synchronized CaveSettings load() {
        if (loaded != null) return loaded;
        var file = FabricLoader.getInstance().getConfigDir().resolve("thenull-caves.json");
        if (!Files.exists(file)) return loaded = DEFAULT;
        try (var reader = Files.newBufferedReader(file)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            return loaded = new CaveSettings(
                json.has("cavern_scale") ? json.get("cavern_scale").getAsDouble() : DEFAULT.cavernScale,
                json.has("wall_lichen") ? json.get("wall_lichen").getAsDouble() : DEFAULT.wallLichen,
                json.has("floor_lichen") ? json.get("floor_lichen").getAsDouble() : DEFAULT.floorLichen);
        } catch (Exception error) {
            throw new IllegalStateException("Invalid " + file, error);
        }
    }
}
