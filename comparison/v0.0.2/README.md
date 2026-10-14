# Preserved 0.0.2 comparison

These files are exact copies from `0e064c2`, preserved after the standalone
candidate passed client and dedicated-server tests without Sift Backport.
They are outside all production source sets and are not applied by Gradle.

The complete working source remains on `main` and in
`build/baseline-v0.0.2/source.tar`; its original playable JAR, reports and fresh
control benchmark are alongside it. Extract that archive into a separate
directory inside this workspace to rerun the old project. Do not run it against
new or existing player saves. No Git commit or push was made for this refactor.

The old heightmap/section fill also lives in the verification source set for
local comparisons; the playable JAR contains the native implementation only.
