# Third-party compatibility and attributions

The Null does not bundle Sift Backport's JAR, Java classes, textures, models, or
artwork. Sift Backport **1.0.3.0 for Fabric 26.3** is a required external mod.

Gradle resolves `curse.maven:sift-backport-1714907:9045257`, or reads an optional
separately supplied local JAR. `gradle/sift-overlay.gradle` reads only the
upstream metadata and these world-generation JSON definitions:

- `data/sift/dimension_type/sift.json`
- `data/sift/worldgen/noise_settings/sift.json`

The two adapted JSON definitions are included inside The Null's built-in data
pack, `resourcepacks/null_extension`. The generated pack also includes original
`thenull` noise definitions and factual `SIFT_ATTRIBUTION.txt`. The original
Sift Java code, other resources, and entire dependency JAR are not copied.
This compatibility layer is isolated for eventual migration away from Sift
Backport. It extends `sift:sift`; it does not create a new dimension.

Upstream attribution: **DerexXD / Derec-Mods**, Sift Backport.

- [Fabric 26.3 source](https://github.com/Derec-Mods/Sift-Backport/tree/main/fabric/26.3)
- [CurseForge project](https://www.curseforge.com/minecraft/mc-mods/sift-backport)

The exact input JAR's `fabric.mod.json` declares `"license": "MIT"` and lists
`DerexXD` as its author. The inspected JAR contains no license file or copyright
notice. An attempted upstream root `LICENSE` lookup did not supply one. The
prototype records these facts rather than inventing a copyright holder,
copyright year, or upstream license notice.

Before publicly redistributing a binary containing the adapted JSON, obtain
the applicable upstream MIT license text and copyright notice and preserve
them as required. The current task produces a local development artifact; no
release is published or distribution permission represented as confirmed.

No Mojang or Sift Backport textures are redistributed in this source project.
Any future original artwork for The Null must be authored separately and retain
its own accurate provenance and licensing.
