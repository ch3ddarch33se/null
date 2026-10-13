# Native world generation refactor — 2026-10-11

This records the original standalone milestone. Subsequent lower terrain/cave
changes and current benchmarks are in [TERRAIN_REFINEMENT.md](TERRAIN_REFINEMENT.md).

## Preservation and foundation

Started from clean `main`, commit `0e064c2`. Local development branch:
`dev/native-standalone-sift`. No commit, push or release. Original playable and
source JARs, a Git source archive, and historical reports are preserved under
`build/baseline-v0.0.2`. The unchanged generator was benchmarked again in a new
disposable world: `build/reports/performance-v002-control.txt`.

Verified existing targets rather than replacing them: Minecraft 26.3,
Temurin 25.0.4.1, Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.17.21,
Gradle 9.6.0. Actual local game classes and vanilla registry JSON were inspected.
26.3 uses `left`/`right` density operations, `gradient`, explicit interpolation
cell sizes, `material_rule`, the `worldgen/feature` registry, and the combined
`buildTerrain` lifecycle. Relevant official references:
[26.3 technical changes](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-10),
[Fabric 26.3](https://www.fabricmc.net/2026/09/15/263.html).

The foundation adds original native 3D density composition, noise settings and
material rules, while preserving the existing upper provider and deep tunnel
graph. The old generator remains the default during this stage. Development
flag: `-PnativeFoundation`. Native exposed geology samples only -368..-257;
deep rock uses uniform palettes and connected tunnels, and the air gap receives
no density evaluations. Tunnel entrances extend to guaranteed exterior air.

The first runtime attempt applied the integration hook recursively to its own
native delegate and was interrupted. A subsequent constant material rule also
proved too broad: 26.3 material evaluation can reach outside the noise settings.
Height guards and baseline upper block hashes fixed this. These intermediate
attempts are not validated artifacts.

Generating the native band in a full-height chunk cost 244.254 ms per fresh
FULL request. Restricting the rule reduced this to 128.623 ms; using a temporary
band-height ProtoChunk reduced it to 108.931 ms. The control was 96.238 ms.
This final foundation measurement is 13.2% slower; the major regression was
removed, but no universal performance equivalence is claimed. Both benchmarks
use seed 123456789, 10 processors, a 2 GiB server heap, four warmups, 32 requests
including neighbours/lighting, and 100 loaded ticks. Foundation loaded tick
p95 was 0.718 ms, with one 50.118 ms outlier; control p95 was 1.076 ms.

Native foundation checks passed against actual chunks: bedrock, gap, skylight,
Sculk material, surface envelope/variation, exact deep graph voxels, baseline
upper hashes, operator permissions and two independent players' enter/leave
commands. A local milestone archive is under `build/milestones/native-foundation`.

## Standalone stage

The standalone candidate passed both dedicated-server and real graphical
client tests with Sift Backport absent before the production requirement was
removed. Candidate reports/source/captures are preserved in
`build/milestones/standalone-candidate`. The foundation flags above describe
archived stages; they are no longer switches in the final build.

The current version is **0.0.3-dev**, with one project-owned dimension,
**`thenull:sift`**, centralized in `WorldProvider`. It does not register or
override `sift:sift` or any speculative vanilla ID. Operator-only `/null enter`,
`/null surface` and `/null leave` work on dedicated servers and real clients.
Return points remain independent per player, including surface/Null transitions.

## Final architecture and changed files

- Added `worldgen/LayeredChunkGenerator.java`: codec-backed adapter for two
  actual vanilla `NoiseBasedChunkGenerator` instances. Registry-context noise
  lookup and seed-bound `RandomState`s use 26.3 APIs. Native multi-noise biome
  generation uses the upper router's 2D climate and height-dependent depth.
- Added `mixin/LayeredRandomStateMixin.java`: a small vanilla-only provider
  adapter for 26.3's zero-router fallback on custom generators. `ChunkMap`
  now exposes the upper native state to vanilla biome-location/debug queries
  and normal buffer cleanup. An end-tick callback cleans the lower state's
  buffers too. No external-mod integration or dimension-name interception occurs.
- Added `worldgen/NativeBand.java`: runs vanilla density, material and carver
  generation in bounded ProtoChunks, then transfers their sections. Only
  112 lower and 256 upper blocks of height undergo density/material generation;
  the 252-block explicit gap and guaranteed solid deep sections do not. Native
  biome containers still exist throughout the dimension, as Minecraft requires.
- Added `worldgen/NativeNullTerrain.java`: uniform deep Sculk, five bottom bedrock
  blocks, the continuous -4..-1 separator, and connected tunnel carving. This
  small Java pass remains because sampling hundreds of guaranteed solid/air
  layers would add work without changing geology.
- Changed `TunnelCarver.java`; added `SeedHash.java`: retain the proven seeded
  Bezier graph, independent of the old heightmap. Native entrances extend to
  guaranteed exterior air. Nominal horizontal diameter remains 2.5..3.3 blocks,
  with taller headroom, loops, branches, intersections and vertical connections.
- Added 18 original JSON definitions under `data/thenull`: dimension/type,
  three noise definitions, two 3D density compositions, two noise settings,
  two material rules, two upper biomes plus Null depths, two configured features
  (`worldgen/feature` in 26.3) and two placed features. Biome prototypes are
  **Tideglass Grove** and **Prism Heath**. Their geography, material palettes and
  decoration distributions are original; all block textures come from the game.
- Changed `TheNull.java`, `NullTeleport.java`, `fabric.mod.json`, `build.gradle`,
  `gradle.properties` and packaged-verification Gradle setup. No required or
  optional Sift dependency remains in the active project or playable JAR.
- Retired `SiftTerrainMixin.java`, its old mixin configuration, the overlay Gradle
  script and external-Sift comparison Gradle script to `comparison/v0.0.2`.
  Old `NullTerrain.java` and `TerrainShape.java` now belong to verification only.
  Their classes are absent from production. Historical comparison test sources
  are preserved outside the active source sets.
- Updated verification/client harnesses and development documentation. README
  retains project authorship/license text; LICENSE and NOTICE are unchanged.

Native composition combines a height gradient with independently seeded 3D
relief/fold noise and 4-block interpolation. Lower range choices guarantee a
solid deep region and a clear gap. This is not a renamed Java heightmap. Material
rules and default rock are separate registry definitions, ready for Abyssite.

Native feature and structure generation remains in Minecraft's normal later
chunk stages. No settlements/structures are implemented yet. Native carvers
are supported by the delegates but disabled in the prototype biomes: random
vanilla cave carving would not preserve the connected lower graph. This adapter
currently requires dry bands, air fluid defaults and no aquifers. Fluids and
their scheduled/postprocessing data need explicit work before enabling them.

## Final validation

The final command, including the native-state lifecycle fix, passed in **2m 1s**,
offline using the existing Gradle cache:

```sh
./gradlew build runPackagedVerificationServer runPerformanceServer runClientPerformance \
  -PbenchmarkLabel=native-lifecycle --offline --console=plain
```

Environment variables were the Java 25 and project-local Gradle paths above.
Log: `build/native-lifecycle.log`. Build warnings concern existing deprecated
APIs/Gradle features; no compilation or runtime validation failure remained.

`build/reports/packaged-verification.txt` confirms the real built JAR loaded
through production Fabric without Sift Backport. Actual chunk tests pass for:
dimension bounds, both bedrock layers, empty gap, zero gap skylight, Sculk-only
lower rock, exact deep graph voxels, all three commands, operator restrictions,
two players' independent return positions, biome selection, materials and features.
The final seven-chunk sample ranged -323..-299 below and 85..125 above, with
92 decoration blocks and 2,239 sampled upper material blocks.

Independent native sampling matches 7,056 actual lower blocks after accounting
for connected carving. Fourteen direct comparisons also confirm that vanilla's
chunk-source climate sampler uses the seeded upper router at both vertical
regions; it no longer receives the zero-router fallback for custom generators.
Pre-carving density has multiple separated rock
intervals in 1 lower and 13 upper columns of 63 tested, confirming geometry
beyond a single-valued heightmap. This finite sample is not a global shape proof.

`build/reports/geometry.txt` passes 168,210,432 region/chunk voxel comparisons
across four seeds (legacy and native graph variants), with zero mismatches.
Native flood fills have 98.416..99.336% of sampled cave air in their largest
component and zero enclosed isolated components. The actual Minecraft
144x144x507 region agrees: 80,072 air voxels, 99.021% largest component, zero
enclosed isolated components. These checks prove sampled air connectivity,
not survival traversal of every vertical passage or connectivity in every seed.

The final client test passed without Sift Backport, including real command
packets, transitions, collision/support checks, rendered chunks and reviewed
screenshots under `runs/client-performance/screenshots`. Default light is
captured separately from temporary development night vision. Report:
`build/reports/client-performance.txt`.

## Performance comparison

The untouched 0.0.2 project was also extracted and rerun under
`build/baseline-v0.0.2/project`, immediately before the final sequence. Both
server workloads use the same seed, coordinates, Java, hardware and 2 GiB heap,
with four warmups, 32 fresh FULL requests (including neighbours/lighting), 956
generated lower chunks and 100 loaded ticks with one forced chunk and no players.
They are separate processes, not a controlled CPU microbenchmark.

| Metric | Initial 0.0.2 control | Repeated 0.0.2 control | Final standalone |
| --- | ---: | ---: | ---: |
| Fresh request mean | 96.238 ms | 97.645 ms | 128.032 ms |
| Fresh request median | 98.600 ms | 92.810 ms | 131.896 ms |
| Fresh request p95 | 177.206 ms | 194.571 ms | 244.266 ms |
| Generation tick mean | 123.435 ms | 127.476 ms | 172.966 ms |
| Generation tick p95 | 210.828 ms | 248.079 ms | 273.286 ms |
| Loaded tick mean | 0.573 ms | 0.197 ms | 1.135 ms |
| Loaded tick p95 | 1.076 ms | 0.458 ms | 2.046 ms |
| Loaded ticks >50 ms | 0/100 | 0/100 | 0/100 |

**Performance parity is not achieved.** The final fresh request mean is 31.1%
slower than the repeated control (33.0% slower than the initial control). Both
final and repeated-control generation workloads have 32/32 ticks above 50 ms.
The final loaded maximum was 48.640 ms. The preceding standalone-confirmed run
averaged 113.126 ms per fresh request (+15.9% against the repeated control),
with loaded p95 0.713 ms and a 62.732 ms outlier. These separate short process
runs show variability; neither establishes performance parity.
Reported lower native timing includes task scheduling/warmup, not isolated CPU.
Reports/CSV: `performance-v002-control`, `performance-v002-repeat` and
`performance-standalone-confirmed` / `performance-native-lifecycle` under
`build/reports`.

Intermediate standalone runs averaged 111.523 and 115.602 ms. A custom dry
native-density palette fill averaged 121.285 ms and offered no demonstrated
gain, so it was removed in favour of the simpler complete vanilla delegate.
The band-height restriction is retained because it removed the earlier
244.254 ms regression. Further generation optimization remains outstanding.

Final graphical measurements are independent of the server benchmark:

| Location | Mean FPS (10 polls) | Integrated tick mean / p95 (200 samples) |
| --- | ---: | ---: |
| Overworld | 365.1 | 2.638 / 4.118 ms |
| Exposed Null | 755.8 | 1.511 / 1.794 ms |
| Upper world | 622.5 | 1.819 / 2.413 ms |

OpenGL on Apple M4; 854x480 window, 329 downloaded chunks, render/simulation
distance 8, VSync off, no AFK throttling, no shaders, gamma 0, peaceful. No
final integrated tick sample exceeded 50 ms. These are one-second FPS counters,
not frame-time percentiles. Views and geometry differ from the old provider;
these numbers do not establish a matched before/after rendering speedup.

## Known limitations and needed artwork

Use fresh worlds. There is no automatic migration from `sift:sift`, no survival
portal and no new dimension data written into old saves. Existing chunks would
retain old terrain if generator data were changed in-place. Multiplayer stress,
long exploration, mod/datapack coexistence and high-resolution rendering have
not been validated.

The upper world has dry prototype vegetation/crystals, native stone/deepslate
underground and a vanilla sky. The Null is very dark at ambient light 0.04;
native sky/fog remain shared with the upper region and can appear beyond the
rendered terrain horizon, especially with night vision. No dedicated sky
renderer, fluid, original trees, biological formations, ruins, mobs, ores or
progression are implemented. The generator's base-column query predicts broad
geology before connected carving, as native column queries do before carvers.

Original human-authored Abyssite Stone textures, cohesive biome ground/plant
textures and models, and eventual sky/effects/sound artwork are still needed.
No Mielon artwork/code/designs were copied and no borrowed assets are claimed
as project-owned. This is an experimental local artifact, not a polished release.

Playable JAR: `build/libs/The-Null-0.0.3-dev.jar`. The untouched 0.0.2 JAR and
sources JAR were restored in `build/libs` and also remain in the baseline archive.
The final source/report/capture milestone is saved under
`build/milestones/native-lifecycle`; the preceding standalone-final milestone is
also retained. A complete file inventory is `build/reports/native-refactor-files.txt`,
and the production archive checks/hashes are `build/reports/production-archive.txt`.
Work remains uncommitted on
`dev/native-standalone-sift`; HEAD is still `0e064c2`. Nothing was pushed/published.
