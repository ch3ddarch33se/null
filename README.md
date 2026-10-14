# The Null

An experimental Minecraft Fabric mod adding The Null, a dark underground region
beneath an original temporary Sift-inspired world, focused on exploration,
progression, and environmental mechanics.

## Current Version

v0.0.3-dev — Experimental native-generation prototype

Currently being developed for Minecraft Java Edition 26.3 using Fabric.

## Status

Very early development. Expect major changes, bugs, broken worlds, and
unfinished content.

## Building

With Java 25 installed, run `./gradlew build`. The playable JAR is
`build/libs/The-Null-0.0.3-dev.jar`; install with Minecraft 26.3, Fabric Loader
0.19.5 and Fabric API 0.161.0+26.3. Sift Backport is no longer required.

Create a **new world**. Both regions share `thenull:sift`. Operator commands:
`/null enter`, `/null surface`, `/null leave`. Existing `sift:sift` saves are not
migrated. Original 0.0.2 source and artifacts are preserved for comparison.

See [current architecture, tests and benchmarks](docs/NATIVE_REFACTOR.md) and
[development commands](docs/DEVELOPMENT.md). The upper environment uses original
terrain/biome data and vanilla placeholder blocks; artistic assets remain unfinished.

The latest [lower terrain and cave refinement](docs/TERRAIN_REFINEMENT.md) adds
Overworld-style regional geometry and denser, varied connected passages.

## License

The source code for The Null is licensed under the Apache License 2.0.

Original textures, models, artwork, sounds, lore, and other creative assets
are licensed under the Creative Commons Attribution 4.0 International
License (CC BY 4.0).

When redistributing or adapting The Null or its original assets, please credit:

Original project created by Tianran Yu (@ch3ddarch33se).
