# WorldScope 2.0.7 — Fabric 26.2

WorldScope is a single-player structure and biome scanner for Minecraft 26.2.

## 2.0.7 changes

- Long status/progress messages at the bottom of the scanner screen now scroll horizontally instead of being truncated with an ellipsis.
- Left and Right arrow keys navigate to the previous/next scan result, matching the on-screen arrows. When the search/radius text field is focused, arrow keys remain available for text editing. Selecting an entry in the registry list leaves the search field unfocused so result navigation works immediately.
- Structure ESP keeps a visible overall bounding outline at every Shape Detail setting. Generated piece bounds are drawn over it when available, so structures with incomplete piece lists from a reference chunk still retain a visible target marker.

## Features retained

- Native Minecraft structure-presence checks before candidate chunk loading, followed by verification of the generated structure start.
- Multiple nearby structure and biome results with previous/next navigation.
- Shape Detail 1–8, structure-piece outlines and higher-resolution sampled biome borders.
- Maximum search distance from 128 to 10,000,000 blocks, with workload caps and explicit partial-scan reporting.
- Selected targets and scan radius saved in `config/worldscope/settings.properties`.
- Single-player only; no `/locate` command and no multiplayer scanning.

## Notes

- Structure piece bounding boxes are approximate layout outlines, not block-perfect voxel meshes.
- Biome borders remain sampled approximations.
- The 10,000,000-block radius is a configurable maximum, not a guarantee that the entire area can be fully scanned in one pass. Candidate and sample budgets still apply.

## Installation

Place `worldscope-2.0.7.jar` in `.minecraft/mods` and remove older WorldScope JARs.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
