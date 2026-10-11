# The Null v0.0.1 development

This prototype extends **the existing `sift:sift` dimension**. It does not
register a separate Null dimension. Use fresh disposable worlds: changing an
existing world's dimension height and generator settings is not a supported
upgrade path for this version.

## Build

The verified dependency targets are Minecraft **26.3**, Java **25**, Gradle
**9.6.0**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**, Fabric Loom
**1.17.21**, and external Sift Backport **1.0.3.0** for Fabric 26.3.
The Gradle wrapper is included. No Python installation is required.

From the project directory on macOS with a Java 25 JDK installed:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew build
```

The playable mod artifact is `build/libs/The-Null-0.0.1.jar`. The sources JAR is
not a playable mod. The first build resolves Minecraft and Maven dependencies;
subsequent builds use the project-local Gradle cache above. Sift Backport is
resolved from `curse.maven:sift-backport-1714907:9045257`, or read from an optional
local `libs/Sift-Backport-1.0.3.0-26.3-fabric.jar`. Neither input is bundled.

`gradle/sift-overlay.gradle` performs required generation through Gradle and
Groovy. Historical Python tooling and its tests remain in the repository but
are not part of the build or Minecraft runtime.

## Generation and compatibility

The build checks the external Sift JAR's mod ID, version, Minecraft target,
initial vertical bounds, fluid, and expected world-generation schema. A changed
upstream input fails generation instead of silently applying an unchecked
patch. Generated resources live in `build/generated/sift-overlay`.

The adaptation changes Sift's dimension type and noise settings inside a
Fabric built-in data pack, `thenull:null_extension`. Fabric registers it as
`ALWAYS_ENABLED`, with `TOP` priority after normal mod data. This makes its two
Sift registry overrides deterministic relative to Sift Backport's normal mod
resources. Explicit user world data packs can still override these definitions.
There are no competing `data/sift` definitions at The Null's main resource root.

The original dimension definition, biome source, climate routers, positive-Y
density function, Sift material-rule reference, features, and structures are
retained. Only the lower density and lower material selection are added.

| Vertical range | Prototype behavior |
| --- | --- |
| Y=0 through 255 | Original Sift generation |
| Y=-4 through -1 | Continuous vanilla bedrock separator |
| Y=-256 through -5 | Explicitly guaranteed empty air |
| Around Y=-300 | Irregular exposed Sculk landscape |
| Beneath the landscape | Sculk terrain with intersecting narrow noise tunnels |
| Y=-768 through -764 | Continuous vanilla bedrock lower boundary |

The dimension has `min_y=-768`, `height=1024`, and an inclusive maximum Y of
255. Sculk is centralized in the compatibility script as the terrain material
replacement point for future Abyssite Stone. No custom blocks, textures,
structures, portals, or Deep Dark Sculk features are added.

Minecraft's native global fluid picker generates lava below
`min(-54, sea_level)` even when the configured default fluid is air. The
extension sets `sea_level=-768` to put that cutoff outside the playable range.
The default fluid remains air, preserving the upper Sift's empty spaces and
keeping the Null's gap and cave passages free of lava.

Caves use the near-zero intersections of two pairs of continuous 3D noise
fields. Generation is deterministic from the world seed and evaluates at world
coordinates without separate chunk-local tunnel carving. The first-pass scales
target narrow passages. Presence and sampled widths can be tested; these facts
do not establish full connectivity, the absence of inaccessible pockets, or
server performance at large exploration distances.

## Commands

`/null enter` and `/null leave` require Minecraft's gamemaster/operator command
permission. Entry generates and checks candidate Sift chunks, finds an exposed
Sculk floor with two air blocks above, and teleports on the server. Leaving
prefers each player's saved safe Overworld position, then checks nearby
Overworld surface columns and a safe position near the Overworld spawn as a
fallback. Return positions are keyed by player UUID and held
in memory; they reset with the server. Both commands clear velocity and fall
distance. Failed searches report an error without teleporting the player.

These are development commands, not progression or portal mechanics.

## Validation

The dedicated-server harness is in `src/verification`, a separate development
source set. It is not packaged in the playable JAR. It loads the real mods and
inspects real generated chunks in an ignored disposable server directory. It
also exercises registered commands with two synthetic server players, checking
safe floors, headroom, collision, motion, per-player return positions, and
non-operator rejection. Synthetic players do not exercise a real client's
network handshake or rendering.

With the same Java and Gradle environment as the build:

```sh
./gradlew runVerificationServer
```

This task depends on `prepareVerificationWorld`, which writes test-only
settings and `eula=true` for the requested server run under `runs/verification`.
It binds only to loopback, selects an ephemeral port, and assigns a new UUID
world name on every invocation with seed `123456789`. Existing test worlds are
left intact. The harness stops the server when its checks finish. Gradle fails
the task if the report is missing or records failed checks, even if Minecraft
itself exits normally.

`src/clientVerification` is also a development-only source set. Its Fabric
Client GameTest creates a fresh creative test world, sends real command
packets, waits for dimension transitions and rendered chunks, checks the
server-side landing, and captures the Overworld, exposed Null surface, a cave,
and upper Sift. It requires a working graphical Minecraft client:

```sh
./gradlew runClientVerification
```

Client results are written to `build/reports/client-verification.txt`, and
screenshots to `runs/client-verification/screenshots`. Both verification mods
are excluded from `build/libs/The-Null-0.0.1.jar`.

Production packaging is checked independently with the actual built mod JAR
and Fabric's production launcher, rather than exploded development resources:

```sh
./gradlew runPackagedVerificationServer
```

This uses a separate local testmod JAR under `build/verification`, a new
disposable world under `runs/packaged-verification`, and writes
`build/reports/packaged-verification.txt`.

The latest machine-readable local results are written to
`build/reports/verification.txt`. See `VALIDATION.md` for the verified outcomes
and commands from this implementation session. A successful Gradle build alone
does not establish a successful Minecraft load or playtest.

Sift's skylight and ambient light **0.8** are retained to preserve its upper
appearance. The separator blocks direct skylight into the Null, but underground
depth does not remove ambient lighting. Independent lower-region darkness is
**not implemented**. Client screenshots show darkness at the tested native
brightness setting, but this does not establish independent lighting. Sift's
sky renderer is shared: at limited render distance, sky may appear beyond the
rendered roof or terrain. Development screenshots with temporary vanilla night
vision expose terrain geometry; they are not evidence of ordinary visibility.

## Prism Launcher / equivalent Fabric instance

1. Create a fresh Minecraft **26.3** instance and select a Java **25** runtime.
2. Install Fabric Loader **0.19.5**.
3. Add Fabric API **0.161.0+26.3**, external Sift Backport **1.0.3.0** for
   Fabric 26.3, and `build/libs/The-Null-0.0.1.jar` to the instance's mods.
4. Create a new disposable world with cheats enabled, or use an operator
   account on a fresh dedicated server with those same mods.
5. Run `/null enter` to visit the exposed lower Sculk surface. Run `/null leave`
   to return safely to the Overworld. Sift's own `/siftteleport` command
   (alias `/sifttp`) can be used to inspect the upper dimension separately.

Do not copy the Sift dependency JAR or Minecraft textures into public source or
release archives for The Null.

## Provenance

The original world layout and design requirements were supplied by the project
creator. The starter, repairs, Java implementation, Gradle compatibility
generation, documentation, and validation harness were prepared with AI
assistance. They are not represented as manually written implementation.

The repository's Apache-2.0 license applies to eligible original code, and the
owner's CC BY 4.0 declaration covers eligible original human-authored creative
assets and lore. Existing root `README.md`, `LICENSE`, and `NOTICE` are preserved.
See `THIRD_PARTY.md` for the adapted Sift data and upstream license evidence.
