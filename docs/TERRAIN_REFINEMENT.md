# Lower terrain and cave refinement — 2026-10-11

This follows the native standalone milestone documented in
[NATIVE_REFACTOR.md](NATIVE_REFACTOR.md). Version remains **0.0.3-dev** on the
existing local branch `dev/native-standalone-sift`; no commit/push/release.
Pre-refinement source, JARs and reports are preserved in
`build/milestones/terrain-refinement-baseline`.

## Scope and implementation

Only the lower terrain changed. A source/archive comparison confirms that all
other pre-existing production files and JAR entries are byte-identical, including
upper density/settings/materials/biomes/features, the dimension/type and ID,
provider/integration, commands, ambience, metadata and licenses.
`build/reports/refinement-scope.txt` records that check.

The lower native density now references Minecraft 26.3's actual Overworld
continentalness, erosion, folded-ridge offset/factor splines and jaggedness.
A translated/compressed depth gradient fits that regional shaping inside the
existing lower band. Native quarter-negative composition and 4x8 interpolation
retain three-dimensional density evaluation. Smooth lower/upper slides plus
constant range guards guarantee solid substrate and air above the exposed
landscape. Sculk, darkness, bedrock, the gap and `thenull:sift` are unchanged.
No Overworld biomes, vegetation, fluids or materials are used.

The initial version also used the Overworld's legacy blended 3D noise. It cost
106.883 ms/fresh request; curve clipping measured 108.583 ms. The retained version
uses a lightweight native 3D normal-noise detail field instead, keeping the
Overworld's regional splines and height/factor/jaggedness composition. It measured
99.730 ms. This is a practical performance tradeoff, not an exact shifted copy
of the Overworld generator. No custom Java heightmap generates the surface.

The tunnel generator retains its connected, seeded Bezier owner graph:

- Jittered 40-block owners and 10 irregular levels; formerly 48 blocks/8 levels.
- Most nominal swept diameters 2.7–5 blocks; occasional 6–9-block passages.
  Five independent radius knots vary each path without periodic sine patterns.
- Short branches, longer winding branches, occasional two-cell connections,
  alternate vertical connections and attached small chambers.
- More extra branches and wide/long connections deeper underground. The
  connected backbone remains; branch tips are allowed to cap inside solid rock.
- Maximum owner/control/radius bounds maintain deterministic chunk seams.
  Span-based ellipsoid filling and exact subcurve bounds skip irrelevant work.
  Observed region carves bypass batch clipping and validate chunk masks against
  the full reference, including negative coordinates.

Production changes: `TunnelCarver.java`, benchmark counters in
`NativeNullTerrain.java`, `null_geology.json`, `null_surface.json`; added
`null_depth.json` and `null_sloped_cheese.json`. Development-only changes:
`build.gradle` client inspection flag; `GeometryVerification`, `NativeVerification`,
legacy verification `NullTerrain`, and `ClientVerification`; added
`BaselineTunnelCarver`, `CaveGeometryVerification`, `SurfaceGeometryVerification`.

## Server, geometry and performance results

`./gradlew build runPackagedVerificationServer runPerformanceServer
-PbenchmarkLabel=refinement-lightweight --offline --console=plain` passed in
**43s** using Temurin 25.0.4.1, Loader 0.19.5, API 0.161.0+26.3 and cached Gradle.
Log: `build/refinement-lightweight.log`. Existing deprecation warnings remain.

Actual chunks passed both bedrock layers, gap, skylight, Sculk, exact deep cave
masks, all three operator commands, independent multiplayer return points,
14 climate-router comparisons and 7,056 density/block comparisons. Upper sample
counts remain exactly 92 decorations and 2,239 material blocks, height 85..125.

The native density survey samples 16,641 columns across 4096x4096 blocks at
32-block spacing: height **-321..-281**, mean -310.617; 7,604 flat-neighbour,
8,524 rolling-neighbour and 513 steeper-neighbour samples. These classifications
use adjacent survey height differences <=1, 2..8 and >8, respectively; they are
not biome categories or exhaustive cliff measurements. Preview:
`build/reports/null-surface-native.png`. The small independent 63-column test
found zero lower multiple-rock intervals; overhang frequency is not established.

Cave comparison uses identical 240x240x507 windows, aligned with both generators'
owner cells. The former small window could clip connecting paths, including
baseline paths; its connectivity ratio was not treated as a global result.
The existing >=95% largest-component and zero enclosed-isolated checks remain.

| Four seeds | Before refinement | Refined |
| --- | ---: | ---: |
| Underground cave air | 0.953–0.964% | 3.905–3.979% |
| Largest air component | 99.155–99.476% | 98.208–98.869% |
| Enclosed isolated components | 0 | 0 |

The actual Minecraft seed-123456789 region matches **3.979%** air and **98.208%**
connectivity. Air in deeper -750..-571 samples is 4.523%, versus 3.607% in
shallower -530..-386 samples. Four-seed physical-cap flood probes found
**229–270 genuine dead ends**: reachable boundary air exits only behind the
branch's final tangent. There are 11–22 paths longer than 100 blocks per sample;
maximum sampled path length is 138.6 blocks. Two-block-clear floor-route flood
fills span 239 blocks, allowing one-block step/jump transitions and checking
headroom. These finite tests do not prove every passage is walkable or every
seed globally connected. Steeper vertical routes can require climbing/jumping.

Refined chunk/reference comparisons total 116,812,800 voxels over four seeds,
with zero mismatches. Retained legacy checks add 126,157,824 comparisons.
Report: `build/reports/geometry.txt`.

| Server metric | Current v0.0.3-dev control | Retained refinement |
| --- | ---: | ---: |
| Fresh FULL request mean | 93.154 ms | 99.730 ms |
| Fresh FULL request median | 88.443 ms | 94.261 ms |
| Fresh FULL request p95 | 165.541 ms | 155.789 ms |
| Generation tick mean | 120.921 ms | 125.474 ms |
| Generation tick p95 | 193.326 ms | 189.434 ms |
| Loaded tick mean / p95 | 0.137 / 0.286 ms | 0.153 / 0.348 ms |
| Loaded ticks over 50 ms | 0/100 | 0/100 |

Fresh mean is **7.1% slower**, generation-tick mean **3.8% slower**; this is not
zero-cost. Both use seed 123456789, the same coordinates, 10 processors, Java 25,
2 GiB server heap, four warmups, 32 fresh FULL requests including neighbour
creation/lighting (956 generated lower chunks), and 100 loaded ticks with one
forced chunk/no players. Prior-session 128.032 ms is historical, not today's
paired control. Short process runs vary with JIT/GC/hardware; do not infer FPS
or a universal speedup from these server numbers. Reports/CSVs:
`performance-refinement-control`, `performance-refinement-first`,
`performance-refinement-clipped`, `performance-refinement-lightweight`.

## Graphical validation and use

One fresh-world client run uses `./gradlew runClientPerformance
-PterrainInspection --offline --console=plain`. The optional flag reads verified
inspection sites generated by the packaged-server check; run that server check
first. It captures native darkness separately from temporary development night
vision, then inspects a physical dead end, wide passage, junction, high ground
and broad lowland. Native lighting is restored before checking the unchanged
upper world. Commands remain `/null enter`, `/null surface`, `/null leave`.

The client run **passed in 1m 43s** (`build/refinement-client.log`). All five
inspection destinations rendered and passed collision checks; cave sites also
had supporting Sculk floors. Screenshots were reviewed: capped passages,
wider spaces, a branching junction, rolling high ground and broad lowland.
The native-light view remains very dark; night vision is an inspection aid.
Captures and the complete report are saved in
`build/milestones/terrain-refinement-final/screenshots` and
`build/reports/client-performance.txt`.

| Client location | Mean FPS, 10 polls | Integrated tick mean / p95, 200 samples |
| --- | ---: | ---: |
| Overworld | 486.4 | 2.078 / 2.741 ms |
| Exposed Null | 877.1 | 1.356 / 1.658 ms |
| Unchanged upper world | 679.8 | 1.499 / 1.725 ms |

Apple M4 OpenGL, 854x480, render/simulation distance 8, 329 downloaded chunks,
VSync off and AFK throttling disabled; no shaders. No integrated tick exceeded
50 ms. These are short FPS counters, not frame-time percentiles or a matched
before/after rendering comparison. Only one graphical client was launched.

Use fresh worlds. Existing pre-refinement chunks retain their original geometry;
there is no save migration, terrain regeneration or modification of existing
Minecraft saves. The upper environment and shared-sky limitations are unchanged.
No new mobs, materials, structures, artwork or runtime dependencies were added.
Playable JAR remains `build/libs/The-Null-0.0.3-dev.jar`.
The tested final source, JARs, reports and captures are preserved under
`build/milestones/terrain-refinement-final`. Its production JAR is 49,533 bytes,
SHA256 `a20ac399a1836ba4b0f79a98e919e95ec698fb90089e57da976acb502d310ac3`.
