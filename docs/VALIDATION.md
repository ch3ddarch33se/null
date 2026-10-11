# v0.0.1 validation — 2026-10-11

The prototype compiled, loaded on a dedicated Fabric server, loaded from the
actual built mod JAR through Fabric's production launcher, and ran real-client
command tests in a fresh singleplayer world. The final dedicated-server,
production-JAR, and client test reports each record `automated_result = PASSED`.
This is an automated runtime-tested terrain prototype, not a full gameplay
release or a claim that every world-generation and lighting target is solved.

## Commands and local artifacts

The following Gradle tasks were executed using Java 25 and the project-local
Gradle cache. From the repository on macOS:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew build
./gradlew build compileVerificationJava
./gradlew runVerificationServer
./gradlew build runVerificationServer
./gradlew runClient
./gradlew runClientVerification --console=plain
./gradlew runPackagedVerificationServer
```

`runClient` was a separate startup check: Minecraft reached its graphical
client with both mods loaded and was then deliberately terminated. Its exit
143 reflects that termination, not a successful clean application shutdown.
The later automated `runClientVerification` completed successfully and tested
real commands, dimension transitions, and rendered chunks.

The playable artifact is `build/libs/The-Null-0.0.1.jar`. The dedicated-server
report is `build/reports/verification.txt`; production-JAR results are in
`build/reports/packaged-verification.txt`; real-client results are in
`build/reports/client-verification.txt`. Generated worlds, reports, and
screenshots remain local in ignored directories. No existing user saves were
used, and no commit, push, or release was performed.

Implementation files created or modified:

- `build.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, and
  `gradle/wrapper/*` — compatible dependency resolution and a working wrapper.
- `gradle/sift-overlay.gradle` — Gradle-native isolated Sift data-pack generation.
- `src/main/java/com/ch3ddarch33se/thenull/TheNull.java`, `NullTeleport.java`, and
  `src/main/resources/fabric.mod.json`, `pack.mcmeta` — pack registration,
  operator commands, exact external-mod requirement, and current pack formats.
- `gradle/verification.gradle`, `gradle/packaged-verification.gradle`,
  `src/verification/*`, and `src/clientVerification/*` — disposable server,
  production-JAR, and real-client validation, excluded from the playable JAR.
- `.gitignore` and `docs/DEVELOPMENT.md`, `docs/THIRD_PARTY.md`,
  `docs/VALIDATION.md` — local artifact exclusions and truthful development notes.

Root `README.md`, `LICENSE`, and `NOTICE` were preserved.

## Final results

All terrain counts below are the final server run using seed `123456789`,
cave thresholds `0.18` / `0.17`, eleven chunks, and 2,816 columns. These are
finite samples, not guarantees about every location or seed.

| Check | Result | Evidence / scope |
| --- | --- | --- |
| Gradle build | **PASSED** | Java 25 compilation and mod JAR generation |
| Dedicated-server startup | **PASSED** | The Null, external Sift Backport, and Fabric API loaded |
| Actual production mod JAR | **PASSED** | Production Fabric loaded the packaged built-in data pack and completed the same checks |
| Sift vertical bounds | **PASSED** | Minimum -768, height 1024, inclusive maximum 255 |
| Overworld bounds | **PASSED** | Minimum -64, height 384, inclusive maximum 319 |
| Original upper Sift density | **PASSED** | 25,344 native density samples compared to unmodified Sift settings; zero mismatches |
| Upper Sift terrain | **PASSED** | 63,151 solid blocks in positive-Y terrain samples; real client captured upper terrain and sky |
| Separating bedrock | **PASSED** | No incorrect blocks at Y=-4 through -1 in sampled columns |
| Guaranteed air gap | **PASSED** | Zero non-air blocks at Y=-256 through -5 in sampled columns |
| Lower terrain material | **PASSED** | No unexpected lower blocks: Sculk, air, and bottom bedrock only |
| Lower bedrock boundary | **PASSED** | Every sampled bottom column had bedrock at Y=-768 |
| Exposed Null elevation | **PASSED** | Sampled surfaces -319 through -282; mean -300.83 |
| Interior cave air | **PASSED** | 353,331 interior air blocks among 1,262,047 sampled interior cells |
| Cave headroom | **PASSED** | 91,256 sampled Sculk floors with two air blocks above |
| Narrow cave passages | **PASSED, limited measurement** | 6,656 interior X-axis air runs were 2–4 blocks long |
| Direct gap skylight | **PASSED** | Zero skylight at sampled gap positions |
| `/null enter` | **PASSED** | Two synthetic server players and a real client arrived safely on exposed Sculk in `sift:sift` |
| `/null leave` | **PASSED** | Synthetic players returned to separate original safe Overworld positions; real client returned to a safe Overworld floor |
| Operator restriction | **PASSED** | Non-operator command access and execution were rejected |
| Multiplayer state isolation | **PASSED** | Teleporting one synthetic server player did not move the other; return positions remained independent |
| Client packets and rendering | **PASSED** | Real client sent both commands, changed dimensions, and waited for rendered chunks |
| Sampled generation reproducibility | **PASSED** | All eleven upper and eleven lower chunk hashes matched between separate fresh development and production-JAR worlds |

The reproducibility comparison covers the two reported runs and their sampled
chunks. A separate test that deliberately varies neighboring chunk generation
order or explores a large world has not been performed.

## Cave and lighting limits

The cave-width check samples interior air runs along X, excludes runs touching
chunk edges, and does not measure a tunnel's minimum cross-section. The observed
histogram was:

| X-axis air-run length | Count |
| --- | ---: |
| 1 | 7,464 |
| 2 | 4,164 |
| 3 | 1,620 |
| 4 | 872 |
| 5 or more | 872 |

There are substantial one-block recesses and runs, alongside traversable
passages. These samples do not justify a global claim that most tunnels are
2–4 blocks wide. Cave connectivity, loops, inaccessible pockets, and long-run
exploration performance are **NOT TESTED**. Optimization and additional cave
refinement are deferred.

The client rendered the exposed Null landscape and an interior cave. Native
brightness was captured separately from development-only temporary vanilla
night vision used to show geometry. The dark native screenshot is an observed
view at one setting; **independent lower-region lighting is NOT IMPLEMENTED**.
Sift ambient light remains 0.8, and its sky renderer remains shared. At limited
render distance, sky may still appear beyond rendered terrain or the roof.
There is no second dimension or second sky implementation.

Upper Sift terrain and its sky were visually captured, and the original native
density was compared numerically. A complete visual comparison against a
separate unmodified Sift client instance is **NOT TESTED**. A byte-for-byte
comparison of all Overworld generation against a separate baseline instance,
remote multiplayer clients, other world seeds, existing-world migration, and
other user data-pack combinations are also **NOT TESTED**.

Local screenshot files are under `runs/client-verification/screenshots`:

- `0000_thenull-overworld.png`
- `0001_thenull-null-surface.png` — native light / no night vision
- `0002_thenull-null-surface-development-night-vision.png` — geometry inspection
- `0003_thenull-null-cave-development-night-vision.png` — geometry inspection
- `0004_thenull-upper-sift.png`

## Failures found and repaired

The first server run **FAILED**: Minecraft's native global fluid picker filled
the gap and caves with lava below Y=-54 despite the air default fluid. The
data-only `sea_level=-768` adjustment places the lava cutoff below this world's
minimum. Subsequent fresh server and production-JAR runs contain no lava in the
sampled lower terrain or guaranteed gap.

The initial height assertions used an exclusive maximum while 26.3's
`getMaxY()` returns an inclusive value; those harness assertions were corrected.
Initial upper-terrain samples all landed in natural Sift void regions near the
origin. Broader samples found original upper terrain and the native-density
comparison established that the preserved branch matched the upstream data.

A single measured cave-threshold adjustment widened the initial passages.
Final reports use the resulting `0.18` / `0.17` thresholds; the earlier reports
are retained locally for truthful debugging history.

See `DEVELOPMENT.md` for build and Prism Launcher instructions and
`THIRD_PARTY.md` for upstream data attribution and the missing upstream license
notice limitation before any public binary redistribution.
