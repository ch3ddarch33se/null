#!/usr/bin/env python3
"""Offline validation of The Null's Sift worldgen overlay transformations."""
import json
import sys
from pathlib import Path
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).parent))
from generate_sift_overlay import (dimension_type_overlay, noise_settings_overlay,
                                   produce_overlay_from_data, MIN_Y, TOTAL_HEIGHT)


class OverlayTests(unittest.TestCase):
    def setUp(self):
        # Synthetic Sift-like fixture, not copied from the upstream project.
        self.dimension = {"min_y": 0, "height": 256, "logical_height": 256,
                          "has_skylight": True, "ambient_light": 0.8,
                          "attributes": {"minecraft:visual/fog_color": "#abcdef"}}
        self.noise = {"noise": {"min_y": 0, "height": 256},
                      "default_block": "minecraft:gray_concrete",
                      "noise_router": {"final_density": {
                          "type": "minecraft:gradient", "axis": "y",
                          "from_coordinate": 0, "to_coordinate": 256,
                          "from_value": 2.0, "to_value": -2.0}},
                      "material_rule": "sift:sift", "other_setting": "preserve"}

    def test_dimension_extends_down_preserving_existing_options(self):
        altered = dimension_type_overlay(self.dimension)
        self.assertEqual((altered['min_y'], altered['height']), (MIN_Y, TOTAL_HEIGHT))
        self.assertTrue(altered['has_skylight'])
        self.assertEqual(altered['attributes'], self.dimension['attributes'])
        self.assertEqual(self.dimension['height'], 256)

    def test_upper_sift_density_preserved_exactly(self):
        altered = noise_settings_overlay(self.noise)
        density = altered['noise_router']['final_density']
        self.assertEqual(density['when_in_range'], self.noise['noise_router']['final_density'])
        self.assertEqual(altered['other_setting'], 'preserve')
        self.assertEqual(altered['default_block'], 'minecraft:gray_concrete')
        self.assertEqual(altered['material_rule']['sequence'][-1], 'sift:sift')

    def test_lower_rules_and_material(self):
        altered = noise_settings_overlay(self.noise)
        density = altered['noise_router']['final_density']
        lower = density['when_out_of_range']
        self.assertEqual((lower['min_inclusive'], lower['max_exclusive']), (-5.0, 0.0))
        self.assertEqual(lower['when_in_range'], 1.0)
        gap = lower['when_out_of_range']
        self.assertEqual((gap['min_inclusive'], gap['max_exclusive']), (-256.0, -5.0))
        self.assertEqual(gap['when_in_range'], -1.0)
        self.assertIn('minecraft:sculk', json.dumps(altered))
        self.assertIn('minecraft:bedrock', json.dumps(altered))

    def test_changed_upstream_schema_rejected(self):
        self.noise['noise']['min_y'] = -64
        with self.assertRaises(ValueError):
            noise_settings_overlay(self.noise)

    def test_writes_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            produce_overlay_from_data(self.dimension, self.noise, tmp)
            root = Path(tmp) / 'data'
            self.assertTrue((root / 'sift/worldgen/noise_settings/sift.json').exists())
            self.assertTrue((root / 'thenull/worldgen/noise/cave_primary.json').exists())
            for f in root.rglob('*.json'):
                json.loads(f.read_text())


if __name__ == '__main__':
    unittest.main()
