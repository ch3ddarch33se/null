#!/usr/bin/env python3
"""Build-time Sift Backport 26.3 data patcher for The Null.

Reads the separately installed/distributed Sift Backport mod JAR; generates
worldgen data overrides in the temporary Gradle build directory. Does not
bundle the backport's source, Java classes, textures, or mod JAR.

The generated worldgen settings build on the backport's original JSON data;
consult docs/THIRD_PARTY.md before distributing generated binaries.
"""
import argparse
import json
import os
from pathlib import Path
import zipfile

MOD_RESOURCE_ROOT = "data/sift/"
SOURCE_FILES = {
    "dimension_type": "dimension_type/sift.json",
    "noise_settings": "worldgen/noise_settings/sift.json",
}

MIN_Y = -768
TOTAL_HEIGHT = 1024
BEDROCK_BOTTOM = -5  # y=-5 through -1; approximate 5-block barrier
SURFACE_CROSSING = -300


def gradient(from_y, to_y, low_value, high_value):
    return {
        "type": "minecraft:gradient",
        "axis": "y",
        "from_coordinate": from_y,
        "to_coordinate": to_y,
        "from_value": low_value,
        "to_value": high_value,
    }


def y_noise(name, xz, y):
    return {"type": "minecraft:noise", "noise": name, "xz_scale": xz, "y_scale": y}


def range_choice(source, lo, hi, yes, no):
    return {
        "type": "minecraft:range_choice",
        "input": source,
        "min_inclusive": lo,
        "max_exclusive": hi,
        "when_in_range": yes,
        "when_out_of_range": no,
    }


def add(left, right):
    return {"type": "minecraft:add", "left": left, "right": right}


def multiply(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def below_height(y):
    return {
        "type": "minecraft:not",
        "invert": {
            "type": "minecraft:y_above",
            "anchor": {"absolute": y},
            "surface_depth_multiplier": 0,
            "add_stone_depth": False,
        },
    }


def noise_settings_overlay(original):
    """Keep EVERY original upper-Sift noise setting; replace only lower Y."""
    result = json.loads(json.dumps(original))
    if result.get("noise", {}).get("min_y") != 0:
        raise ValueError("Sift version changed min_y; refuse unreviewed overlay")
    if result.get("noise", {}).get("height") != 256:
        raise ValueError("Sift height changed; refuse unreviewed overlay")
    original_density = result.get("noise_router", {}).get("final_density")
    if original_density is None or "material_rule" not in result:
        raise ValueError("Unsupported Sift 26.3 noise-settings schema")
    original_material = result["material_rule"]
    result["noise"] = {**result["noise"], "min_y": MIN_Y, "height": TOTAL_HEIGHT}

    # Dense, irregular terrain around -300 (with a large empty gap above).
    # Below the surface, intersect two zero-level noise surfaces. Their
    # near-zero intersection approximates branching 3D tunnels, although
    # this DOES NOT guarantee every individual tunnel is connected.
    # Noise scales/thresholds intentionally exposed for future playtesting.
    surface = add(
        gradient(-390, -210, 1.9, -1.9),
        multiply(0.45, y_noise("thenull:surface_variation", 0.014, 0.0)),
    )
    secondary_cave = range_choice(
        {"type": "minecraft:abs", "argument": y_noise("thenull:cave_secondary", 0.13, 0.12)},
        0.0, 0.13, -1.0, surface,
    )
    caves = range_choice(
        {"type": "minecraft:abs", "argument": y_noise("thenull:cave_primary", 0.13, 0.12)},
        0.0, 0.13, secondary_cave, surface,
    )

    # -5..-1 = solid for bedrock; -256..-6 = guaranteed air;
    # lower terrain starts between ~-280 and -320, so the vertical
    # opening above the upper Null surface is approximately 275–315 blocks.
    lower = range_choice(
        "minecraft:y", -5.0, 0.0, 1.0,
        range_choice("minecraft:y", -256.0, -5.0, -1.0, caves),
    )
    result["noise_router"]["final_density"] = range_choice(
        "minecraft:y", 0.0, 4096.0, original_density, lower,
    )

    # Material rules are evaluated after solid/air is selected.
    # Use a conditional Sculk/bedrock override only BELOW Sift's Y=0.
    result["material_rule"] = {
        "type": "minecraft:sequence",
        "sequence": [
            {
                "type": "minecraft:condition",
                "if_true": below_height(0),
                "then_run": {
                    "type": "minecraft:sequence",
                    "sequence": [
                        {
                            "type": "minecraft:condition",
                            "if_true": {
                                "type": "minecraft:y_above",
                                "anchor": {"absolute": BEDROCK_BOTTOM},
                                "surface_depth_multiplier": 0,
                                "add_stone_depth": False,
                            },
                            "then_run": {"type": "minecraft:block", "result_state": "minecraft:bedrock"},
                        },
                        {"type": "minecraft:block", "result_state": "minecraft:sculk"},
                    ],
                },
            },
            original_material,
        ],
    }
    return result


def dimension_type_overlay(original):
    result = json.loads(json.dumps(original))
    if (result.get("min_y"), result.get("height")) != (0, 256):
        raise ValueError("Sift dimension type changed; review it before generation")
    result["min_y"] = MIN_Y
    result["height"] = TOTAL_HEIGHT
    result["logical_height"] = TOTAL_HEIGHT
    # Keep skylight, ambient light and visual properties of the upper Sift.
    # Altitude-sensitive darkness remains a known client-rendering blocker.
    return result


def dump_json(root, relative, obj):
    dest = root / relative
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def produce_overlay_from_data(dimension_type, noise_settings, output):
    output = Path(output)
    dump_json(output, "data/sift/dimension_type/sift.json", dimension_type_overlay(dimension_type))
    dump_json(output, "data/sift/worldgen/noise_settings/sift.json", noise_settings_overlay(noise_settings))
    for name in ("surface_variation", "cave_primary", "cave_secondary"):
        dump_json(output, f"data/thenull/worldgen/noise/{name}.json", {
            "firstOctave": -3, "amplitudes": [1.0, 0.5, 0.25]
        })


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sift-jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.sift_jar.is_file():
        parser.error(f"Sift jar not found: {args.sift_jar}")
    with zipfile.ZipFile(args.sift_jar) as jar:
        try:
            meta = json.loads(jar.read("fabric.mod.json"))
            if meta.get("id") != "sift_backport":
                raise ValueError(f"Not a Sift Backport Fabric jar: {meta.get('id')!r}")
            originals = {
                name: json.loads(jar.read(MOD_RESOURCE_ROOT + relative))
                for name, relative in SOURCE_FILES.items()
            }
        except KeyError as e:
            raise ValueError(f"Missing expected Sift 26.3 resource: {e}") from e
    produce_overlay_from_data(originals["dimension_type"], originals["noise_settings"], args.output)
    print(f"Generated Sift lower-world overlay at {args.output}")


if __name__ == "__main__":
    main()
