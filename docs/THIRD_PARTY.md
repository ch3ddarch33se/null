# Third-party compatibility and attributions

The Null does not include the Sift Backport mod JAR, Java code or textures.

During the build, `tools/generate_sift_overlay.py` reads the user's separately
resolved Sift Backport 1.0.3.0 Fabric JAR and produces modified copies of its
world-generation JSON definitions, specifically the Sift dimension type and
noise settings. The generated JSON files are packaged in The Null's JAR so that
the two mods can share `sift:sift` dimension generation.

Sift Backport is developed by **DerexXD / Derec-Mods**:
https://github.com/Derec-Mods/Sift-Backport
https://www.curseforge.com/minecraft/mc-mods/sift-backport

Sift Backport's Fabric metadata and CurseForge page identify its license as MIT.
Before publicly distributing a binary containing the adapted world-generation
JSON files, confirm the precise license and preservation of copyright notices
for these files with the maintainer and include the applicable license text.

All original art for The Null must be authored separately. No Mojang or Sift
Backport textures are redistributed in this source project.
