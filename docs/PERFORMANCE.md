# Tunnel-path revision — measured validation, 2026-10-11

The first in-game feedback identified slow performance, periodic-looking
surface terrain and excessively hollow caves. This revision replaces the lower
generator and preserves the existing Sift integration and commands. Code and
test harnesses were implemented with AI assistance. No commit or push was made.

## Measurement before changes

The unchanged noise-density generator was benchmarked first on Java 25.0.4.1,
macOS arm64, with 10 available processors and a 2 GiB server heap. Both mods and
Fabric API loaded in a fresh world with seed `123456789`. Four FULL requests
were warmup, followed by 32 measured requests at the same widely spaced Sift
coordinates, one per server tick. A request includes required neighbouring
generation, features and lighting; it is not the CPU time of one noise task.
The workload generated 956 lower-terrain chunks in the instrumented runs.

Then the harness measured 100 ticks with terrain loaded and one forced Sift
chunk. There were no connected players, machines or multiplayer workload.
Timing uses server START/END tick events, excludes the 50 ms pacing sleep and
includes synchronous benchmark work. It separately times FULL requests and
cached retrieval. An initial autosave and world startup precede the warmup.

| Measurement | Original noise-density | Final tunnel paths |
| --- | ---: | ---: |
| Fresh FULL request mean | 295.026 ms | 94.078 ms |
| Fresh FULL request median | 286.450 ms | 93.820 ms |
| Fresh FULL request p95 | 398.450 ms | 140.830 ms |
| Fresh FULL request maximum | 418.302 ms | 153.689 ms |
| Generation tick mean | 324.256 ms | 121.576 ms |
| Generation tick p95 | 433.309 ms | 187.940 ms |
| Generation ticks exceeding 50 ms | 32 / 32 | 32 / 32 |
| Loaded tick mean | 0.419 ms | 0.681 ms |
| Loaded tick p95 | 2.108 ms | 1.163 ms |
| Loaded ticks exceeding 50 ms | 0 / 100 | 0 / 100 |
| Cached retrieval mean | 0.005 ms | 0.049 ms |
| Client FPS | NOT MEASURED | NOT MEASURED |

Fresh chunk waits improved **3.14x / 68.1%** in the final run. Two earlier runs
of the replacement averaged 106.649 and 95.854 ms, so this is a substantial
observed improvement rather than a claimed universal speed guarantee. Loaded
ticks were healthy in both versions; their small differences do not establish
a steady-state improvement. Exploration still produces ticks above 50 ms.
This headless benchmark does not measure client FPS. The later three-location
client comparison is described below; the original generator's FPS was not
recorded, so no before/after FPS improvement is claimed.

The final lower-pass timings, collected only when `thenull.benchmark=true`,
averaged 0.054 ms for the height field, 0.212 ms for paths/mask and 0.895 ms for
section fill/heightmaps: approximately 1.16 ms per chunk. These are parallel
task wall durations, include warmup and scheduling effects, and do not isolate
the remaining native Sift, lighting, I/O and allocation costs. The empty-gap
fast path removes work but its individual speedup was below run variability.

## Implementation

- `TerrainShape.java`: deterministic hashed gradient noise, five scales,
  independent coordinate warps and a ridge contribution. No sine-based shape
  or repeated terrain tile. Bounds -348..-270 leave at least 265 air blocks
  below the separator even at the highest possible surface.
- `TunnelCarver.java`: solid rock carved by narrow, swept cubic Bezier paths.
  Jittered junctions, horizontal cycles, attached dead-end branches, selected
  vertical links and surface entrances. Eight depth bands, nominal horizontal
  diameter 2.5..3.3 blocks, taller headroom, wider intersections. Fixed owner
  and conservative reach per path; chunks reconstruct and clip the same paths.
- `NullTerrain.java`: uniform section palettes, Sculk material centralized in
  `MATERIAL`, preserved biome containers, updated generation heightmaps and a
  fast path for wholly empty gap sections. No per-voxel density noise, aquifers,
  fluid generation, shared writable neighbour chunks or unbounded cache.
- `SiftTerrainMixin.java` / `thenull.mixins.json`: one required injection chains
  the lower pass onto Sift's completed terrain future before features/lighting.
  It checks the actual dimension key and height, and writes only below Y=0.
- `gradle/sift-overlay.gradle`: overrides only Sift's dimension type in the
  always-enabled built-in pack. Original Sift noise settings, sea level, density,
  surface rules, biome source, structures and features remain upstream data.
- `build.gradle`, `gradle/verification.gradle`, `src/verification/*`: repeatable
  benchmarks, voxel tests and disposable runtime validation. The additional
  `performance-comparison.gradle` and standalone `ComparisonVerification`
  testmod compare the Overworld and Sift with/without The Null, and test ticks
  at different player elevations. Client performance sampling is isolated in
  `ClientPerformanceCapture.java` and `ClientVerification.java` in the
  `src/clientVerification` source set.
  Geometry checks
  run as part of `build`; verification mods are excluded from the playable JAR.
- `DEVELOPMENT.md`, `THIRD_PARTY.md`, `VALIDATION.md`: updated current mechanism
  and marked earlier noise-generator/client results as historical.

No changes to commands, new blocks, textures, lighting, upper Sift generation
or unrelated worlds. Root README, LICENSE, NOTICE and the pre-existing modified
`.DS_Store` were preserved.

## Actual tests

**PASSED:** build; development dedicated server; actual built JAR with
production Fabric; dimension bounds; continuous barrier; empty gap; Sculk-only
lower terrain plus ordinary bottom bedrock; zero direct gap skylight; both
registered commands with two independent synthetic server players; operator
restriction. Lighting still retains Sift ambient light 0.8.

Upper Sift: 25,344 native density samples matched original Sift data. All eleven
Y=0..255 block hashes matched the pre-change baseline, including distant chunks
with actual upper terrain. Development and production lower/upper hashes also
matched. Overworld bounds remained -64..319, and return commands passed; a full
Overworld baseline block comparison was not performed.

The eleven-chunk terrain sample contained 11,910 internal air cells among
1,248,907 interior cells (**0.954%**, previously **28.0%**), and 3,560 floors with
two air blocks above. Of 292 uncensored X-axis air runs, 254 (**87.0%**) were
2..4 blocks long. Axis runs are orientation-dependent and are not a guarantee
of cross-section at every bend or junction.

Actual six-neighbour flood fills covered four **144x144x507 voxel windows**:

| Seed | Underground air voxels | Largest component | Enclosed isolated components |
| --- | ---: | ---: | ---: |
| 123456789 | 81,235 | 99.035% | 0 |
| 0 | 80,044 | 98.438% | 0 |
| -1 | 79,719 | 99.212% | 0 |
| 987654321 | 79,009 | 99.346% | 0 |

The real Minecraft chunk window for seed `123456789` produced the same 81,235
cave voxels and 99.035% component result. Air above surface-minus-ten is removed
before the flood fill, so outdoor air cannot falsely join caves. All remaining
small fragments touch a clipped boundary. Tests require zero enclosed isolated
components and at least 95% of sampled air in one component. These prove the
reported finite air connectivity, not player traversal of every vertical shaft
or global connectivity in every seed.

Each seed also compared a whole-region carve against 81 independently generated
chunks in forward, reverse and shuffled order. **126,157,824 voxel comparisons,
zero mismatches**, including negative coordinates and all shared borders.
Actual Minecraft chunks matched expected lower block states with zero
mismatches. A 768x768 surface map ranged -347..-273, mean -308.473, with no
clamped plateaus in that sample. Local review images are
`build/reports/surface-relief.png` and `build/reports/cave-plan.png`; these are
geometry maps, not client screenshots or mod textures.

**NOT TESTED:** long multiplayer exploration, other data packs, migration of old
worlds and independent darkness. The native generator now spans 0..255 while
the dimension spans -768..255. Use a **new disposable world**, as existing chunks
retain the old generation and can form seams where new chunks meet them.

## Commands executed

All commands ran from the repository with:

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew runPerformanceServer -PbenchmarkLabel=baseline --console=plain
./gradlew build --console=plain
./gradlew runVerificationServer --console=plain
./gradlew verifyNullGeometry --console=plain
./gradlew runPerformanceServer -PbenchmarkLabel=tunnels-first --console=plain
./gradlew build verifyNullGeometry runVerificationServer runPerformanceServer -PbenchmarkLabel=profiled --console=plain
./gradlew build verifyNullGeometry runPackagedVerificationServer runPerformanceServer -PbenchmarkLabel=final --console=plain
./gradlew runPackagedVerificationServer --console=plain
./gradlew runSiftControlServer runPerformanceComparison --console=plain
./gradlew runClientPerformance --console=plain
```

Reports/CSV samples are under `build/reports`, logs under `build`, and fresh
worlds under ignored `runs`. No existing saves were opened or overwritten.
The original JAR and original chunk report are preserved locally under
`build/baseline`. The built playable artifact is
`build/libs/The-Null-0.0.1.jar`. Install it with the existing Java 25 / Minecraft
26.3 / Fabric Loader 0.19.5 / Fabric API 0.161.0+26.3 / Sift Backport 1.0.3.0
instance, create a new world, and use `/null enter` and `/null leave`.

## Whole-dimension performance comparison

After the user clarified that lag also occurred above the separator, an
independent testmod ran through **production Fabric** both without The Null
(unmodified Sift Backport) and with the built mod. It repeats the same 4+32
fresh FULL Sift requests and measures Overworld requests separately. Loaded
tick phases use one stationary synthetic player, a forced 3x3 area, 20 settling
ticks and 100 samples per location. Upper Sift probes stood above actual Sift
terrain at Y=75; the Null probe used the same X/Z at Y=-304. It does not compare
client rendering and is not a multiplayer stress test.

| Measurement | Sift Backport alone | With the revised Null |
| --- | ---: | ---: |
| Sift fresh FULL mean | 56.778 ms | 94.253 ms |
| Sift fresh FULL p95 | 106.111 ms | 167.030 ms |
| Overworld fresh FULL mean | 165.762 ms | 165.628 ms |
| Upper Sift loaded tick mean | 1.688 ms | 2.039 ms |
| Upper Sift loaded tick p95 | 3.960 ms | 1.612 ms |
| Null loaded tick mean | Absent | 1.370 ms |
| Null loaded tick p95 | Absent | 1.923 ms |
| Overworld loaded tick mean | 5.488 ms | 5.851 ms |
| Overworld loaded tick p95 | 14.368 ms | 11.166 ms |

The revised full-height Sift still costs approximately 37.5 ms more per fresh
FULL request than original Sift in these runs. Generation always creates the
entire expanded chunk, so this cost also applies when the player is above the
bedrock. Upper Sift had one 57.6 ms outlier among 100 ticks; there was no sustained
loaded tick bottleneck in any tested location. Overworld generation remained
effectively identical. These controls support a whole-Sift exploration cost
from the extension; they do not independently apportion the remaining cost to
lighting, terrain fill, memory or I/O, or establish the cause of historical
client FPS without an original FPS capture.

Reports: `build/reports/comparison-sift-control.txt` and
`build/reports/comparison-with-null.txt`, with raw CSV samples beside each.

## Targeted client comparison

The user could not provide FPS/MSPT readings, so a targeted real-client test
was added to distinguish rendering from integrated-server ticks. It samples
the Overworld, exposed Null surface and upper Sift at render/simulation distance
8, VSync off, maximum FPS option, native gamma 0 and peaceful difficulty, in a
new world. Each phase waits for actual tracked chunks and render work, settles
for five seconds, then samples FPS ten times at one-second intervals while
recording server tick durations.

Two initial attempts **FAILED in the harness**, before FPS sampling, waiting
for Overworld chunk downloads. The harness changed client render distance but
did not broadcast the new client settings to the server. The default Fabric
helper also expects a square of chunks, while vanilla 26.3 sends a rounded
tracking area. The corrected harness broadcasts options, checks the actual
server-requested distance, waits for the tracked shape, then waits for
render/light work. These failures do not establish an Overworld, Sift or Null
rendering failure. Original failed reports/logs remain under
`build/reports/client-performance-first.txt`, `client-performance-second.txt`
and the corresponding `build/client-performance-*.log` files. The current
report is `build/reports/client-performance.txt`; captures are under
`runs/client-performance/screenshots`.

One intermediate completed run recorded upper Sift at 29.9 FPS. Local inspection
of vanilla `FramerateLimitTracker` confirmed its 60-second idle threshold caps
FPS at 30; the upper phase was sampled last after that threshold. This capped
result is **not evidence of a Sift rendering fault** and is preserved as
`client-performance-idle-capped.txt`. The final run set the test-only inactivity
option to `MINIMIZED`, disabling the AFK cap, and required throttle reason
`NONE` for every sample. No installed Prism settings were modified.

The final real-client test **PASSED**, including actual `/null enter` and
`/null leave` packets, dimension transitions, safe landings and new screenshots:

| Location | Mean FPS | Ten-poll min / max | Mean integrated tick | p95 tick |
| --- | ---: | ---: | ---: | ---: |
| Overworld | 315.8 | 267 / 352 | 3.101 ms | 5.997 ms |
| Upper Sift | 224.7 | 198 / 339 | 4.698 ms | 6.384 ms |
| Exposed Null | 290.5 | 245 / 380 | 4.629 ms | 6.011 ms |

All locations had 329 downloaded chunks, an **854x480** OpenGL client window,
render/simulation distance 8, no shader packs, native lighting, no AFK throttle and
200 integrated-server tick samples. No sample tick exceeded 50 ms. FPS values
are ten one-second counters, not per-frame 1% lows. The views used different
natural locations with the same camera yaw/pitch, and are not identical scenes.
Upper Sift rendered slower in this sample; an unmodified-Sift client FPS control
was not run, so that difference is not attributed to The Null or to a specific
renderer. The original generator's FPS and the user's Prism resolution/settings
remain unmeasured. This test did not reproduce severe loaded-area lag at the
tested settings; it does not establish performance at high resolution, larger
render distances, other hardware or prolonged multiplayer exploration.

The confirmed expensive original lower generation explains how new-chunk lag
could affect the entire Sift regardless of player elevation. The server
controls isolate that cost from loaded ticks, while the client measurements
show the revised build's FPS separately. They do not prove every part of the
user's historical lag had the same cause. Surface and cave screenshots were
reviewed locally: the cave is an enclosed narrow passage and the surface has
irregular relief; native darkness still shares Sift's sky and ambient lighting.
