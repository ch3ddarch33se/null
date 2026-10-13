# Dense underground ecosystem — 2026-10-11

This v0.0.3-dev milestone changes the lower cave system only. The upper
Sift-inspired region, the shared thenull:sift dimension, exposed lower
surface, ambience, separator, empty gap, commands and materials remain in
place. The original playable JAR is preserved at
build/milestones/ecosystem-baseline/The-Null-0.0.3-dev.jar.

## Generator

The original seeded Bezier passage graph remains and retains its original
surface-entrance widths. A second graph adds 32-block owner cells, 12 levels
spaced 30 blocks vertically, horizontal cycles, vertical links, branches,
long connections and attached chambers. Most new graph passages are 3.2–5
blocks wide; wider paths range from 5–10 and occasionally 8–20 blocks.
Owner-based generation and the cave halo make neighboring chunks reproduce
the same blocks.

Graph-node caverns occupy Y=-748 through Y=-369. They use three diameter
classes: small 10–25, medium 25–60 and rare large 60–120 blocks. Rotated,
unequal ellipsoids, an offset secondary lobe, regional vanilla noise for size
variation and sparse noise-selected rock pillars form the chambers. The
chamber core remains connected to its graph node. This is a bounded,
deterministic 3D chamber algorithm rather than a random air-noise field, which
avoids Swiss-cheese pockets. A small reservation/clearance API is present for
future structures; no structures are generated.

The terrain remains Sculk. Vanilla Glow Lichen is attached with valid
multiface states to nearby walls, ceilings and occasional floors. Lighting
density is configurable in config/thenull-caves.json (wall_lichen and
floor_lichen; defaults 0.55 and 0.20). The lower chunk writer initializes
deep sections as air and writes Sculk only into solid voxels, reducing
redundant block writes. The top and bottom bedrock barriers remain intact.

## Measurements

The command ./gradlew verifyEcosystemGeometry -PecosystemSize=256 sampled four
seeds in the same 256×256 region. The fixed comparison volume starts below
Y=-310 and ends at the world floor; the gap and open landscape above it are
excluded. This constant-ceiling survey is useful for comparing seeds, while
the production-server test below measures the actual terrain-defined rock
volume.

| Seed | Air | Shallow air (-530..-385) | Deep air (-750..-571) | Largest component | Enclosed isolated components |
| --- | ---: | ---: | ---: | ---: | ---: |
| 123456789 | 59.780% | 75.393% | 71.911% | 99.992% | 0 |
| 0 | 51.114% | 62.004% | 63.042% | 99.987% | 0 |
| -1 | 58.158% | 72.487% | 71.570% | 99.989% | 0 |
| 987654321 | 50.232% | 61.605% | 61.464% | 99.989% | 0 |

The constant-ceiling mean is 54.821%. The spread is real; cave openness varies
substantially by location and seed. In the packaged dedicated-server world,
the actual lower surface ranged from Y=-314 to -304. Across its 240×240 sample,
the actual underground rock volume contained **15,687,954 air voxels out of
24,913,558 (62.970%)**. The largest cave component held 99.980% of sampled air,
with zero enclosed isolated components. Air within the chamber core below
Y=-369 measured 68.913%.

That server sample contained 1,074 caverns (216 small, 832 medium, 26 large)
and 533,994 Glow Lichen blocks. All 4,172 sampled lichen/light positions had
block light level 6 or higher; the safe landing sample had level 6. A physical
cap search found seven genuine dead ends, along with 25 paths over 100 blocks
and passages wider than six blocks. The longest observed route was 138.6
blocks; the sampled player-walk route spanned 239 blocks. The finite survey
does not prove global connectivity for every seed.

Four 256×256 masks matched independently carved 16×16 chunks at all
118,751,232 compared underground voxels, with zero border mismatches. The
packaged server also checked 22,752,000 actual Minecraft mask blocks, bedrock,
the air gap, surface and upper biome/features, player commands and Glow Lichen
support.

The one graphical client run passed. It rendered a dead end, wide passage,
junction, two lower-surface samples and the unchanged upper region. The native
Glow Lichen screenshot used no Night Vision, but the test deliberately set
gamma to zero; the view is very dark despite the measured light levels. The
lichen is visible around the player, while large recesses remain dark. This
placeholder lighting still needs review at ordinary client brightness before
it can be called comfortable exploration lighting. Screenshots and the
complete client report are preserved in
build/milestones/cave-ecosystem-final/screenshots and
build/milestones/cave-ecosystem-final/client-performance.txt.

## Performance and trade-off

Matched Java 25.0.4.1 / 10-processor server tests used seed 123456789, four
warmups, 32 fresh FULL requests and 100 loaded ticks. The preserved v0.0.3-dev
control averaged **112.395 ms** per fresh request. This build averaged
**157.780 ms** (**40.4% slower**), with p50 154.321 ms and p95 235.612 ms.
Generation-tick mean was 180.503 ms. Loaded-tick mean/p95 was 1.051/0.899 ms;
one sample exceeded 50 ms (maximum 54.424 ms). These are server measurements,
not client FPS. Full requests include neighbor generation, features and
lighting.

This is above the previous 31% regression reference. The implementation keeps
the requested approximately 60% actual-seed openness and did not trade away
cave density to hide the cost. The best tested result remains a 40.4% fresh
request regression; reaching the earlier 31% ceiling will require another
measured optimization or an explicit density trade-off.

## Verification and artifacts

Passed commands:

- ./gradlew build runPackagedVerificationServer
- ./gradlew verifyEcosystemGeometry -PecosystemSize=256
- ./gradlew runPerformanceServer -PbenchmarkLabel=cave-final
- ./gradlew runClientPerformance -PterrainInspection

Reports are under build/reports: ecosystem-geometry.txt,
packaged-verification.txt, performance-ecosystem-control.txt,
performance-cave-final.txt and client-performance.txt. The playable JAR is
build/libs/The-Null-0.0.3-dev.jar. Use fresh worlds; old chunk data is not
regenerated or migrated. No mobs, blocks, structures, textures or commits were
added.
