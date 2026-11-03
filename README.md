# WorldScope 2.0.9 — Fabric 26.2

WorldScope is a single-player structure and biome scanner for Minecraft 26.2.

## 2.0.8 changes

- Left and Right arrow keys are registered as Minecraft keybinds and navigate to the previous/next result during normal gameplay when the WorldScope screen is closed. The same keys still work inside the WorldScope screen when a text field is not focused.
- The status bar waits two seconds after a new message, then scrolls smoothly from right to left using subpixel movement at a slower speed. Long messages remain clipped to the status bar rather than being truncated.
- Structure candidates now come from `ChunkGeneratorStructureState.getPlacementsForStructure` for the selected structure in the active dimension. This avoids scanning unrelated structure sets that are not valid for that dimension.
- Random-spread candidates use `ChunkGeneratorStructureState.getLevelSeed`, the exact seed held by Minecraft's structure generator state, rather than re-reading the world options seed.
- Native presence diagnostics now describe `START_NOT_PRESENT` candidates as candidates ruled out by Minecraft's placement/biome checks, rather than implying a known generated structure was deleted.

## Features

- Browse registered structures and biomes, including modded registry entries.
- Search the catalog and use village/temple subtype filters.
- Select Overworld, Nether, or End.
- Maximum search radius from 128 to 10,000,000 blocks, with workload limits and partial-scan reporting.
- Native Minecraft structure-presence checks followed by actual `STRUCTURE_STARTS` chunk verification. `START_NOT_PRESENT` means Minecraft's presence cache found no matching saved start or ruled the candidate out; it is not a count of structures WorldScope found and then removed.
- Up to 128 structure results and 128 sampled biome regions, sorted by distance, with menu and in-game keyboard navigation.
- Shape Detail 1–8, individual structure-piece outlines and sampled biome borders.
- Search radius and selected targets saved in `config/worldscope/settings.properties`.
- Through-wall ESP, coordinates HUD, and proximity alerts.

## Accuracy and limits

- Structure placement candidates are dimension-specific. The native presence checker rules out candidates that fail Minecraft's placement or biome checks; a rejected candidate is not treated as a confirmed structure.
- Structure searches remain workload bounded: up to 1,000,000 placement-region tests per placement, 4,096 candidates per placement, 8,192 aggregate candidate chunks, and 128 displayed results.
- Biome scans are multi-height sampled approximations with a 750,000-sample budget and 128 displayed regions.
- Structure-piece boxes are approximate layout bounds, not block-perfect voxel meshes.
- A 10,000,000-block radius is accepted but cannot guarantee full coverage in one pass when workload caps are reached.
- WorldScope is single-player only and does not issue `/locate`.

## Installation

Place `worldscope-2.0.9.jar` in `.minecraft/mods` and remove older WorldScope JARs.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
