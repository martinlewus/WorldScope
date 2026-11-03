# WorldScope 2.0.11 — Fabric 26.2

WorldScope is a single-player structure and biome scanner for Minecraft 26.2.

## 2.0.11 changes

- Structure verification now loads candidate chunks through `STRUCTURE_REFERENCES`, not just `STRUCTURE_STARTS`.
- It asks Minecraft's `StructureManager.startsForStructure` for starts stored directly in the candidate chunk or referenced by that chunk, then also checks the candidate chunk's direct start. This can recover large structures whose start record is in a neighboring chunk but whose pieces overlap the chunk being checked.
- The end-of-scan diagnostics distinguish cache-negative candidates, actual starts recovered through references, duplicate references, and candidate chunks with no matching start. It reports recovered start chunk coordinates.
- The header **SPEED 1x–4x** setting controls marquee scroll speed. It is saved in `config/worldscope/settings.properties`. The marquee retains its two-second delay and frame-interpolated movement.

## Features

- Browse registered structures and biomes, including modded entries.
- Search the registry and filter structure subtypes.
- Select Overworld, Nether, or End.
- Search radius from 128 to 10,000,000 blocks, with workload limits and partial-scan reporting.
- Structure placement candidates come from the selected structure's placement index for the active dimension and the generator's own level seed.
- Native structure-presence checks are followed by direct and reference-based start verification.
- Up to 128 structure and biome results with menu arrows and in-game Left/Right keybinds.
- Shape Detail 1–8, structure-piece outlines, biome borders, through-wall ESP, coordinate HUD, and proximity alerts.
- Search radius, selected targets, and marquee speed are saved in `config/worldscope/settings.properties`.

## Accuracy and limits

- A structure placement candidate is only a possible start. The scanner verifies actual `StructureStart` records before showing a result.
- Cache-negative candidates are independently checked, up to 512 per scan. Beyond that cap, the scanner reports how many were not verified.
- Structure candidate enumeration is capped at 1,000,000 placement-region tests per placement, 4,096 candidates per placement, and 8,192 aggregate candidate chunks.
- Biome scanning is a multi-height sampled approximation with a 750,000-sample budget and 128 displayed regions.
- Structure-piece boxes are approximate layout bounds, not block-perfect voxel meshes.
- The 10,000,000-block maximum does not guarantee full coverage in one scan when workload limits are reached.
- WorldScope is single-player only and does not issue `/locate`.

## Installation

Place `worldscope-2.0.11.jar` in `.minecraft/mods` and remove older WorldScope JARs.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
