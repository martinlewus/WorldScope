# WorldScope 2.0.14 — Fabric 26.2

WorldScope is a single-player structure and biome scanner for Minecraft 26.2.

## Biome improvements in 2.0.14

- Biomes are traced as connected horizontal regions instead of showing only the first sampled point.
- Each sampled horizontal cell records the biome's contiguous vertical extent, so the ESP outlines a 3D biome volume rather than a thin flat ring at one Y level.
- Removed the misleading aggregate rectangular biome box; biome ESP now draws the sampled region boundary.
- Shape Detail now uses a finer coarsest sampling step (32 blocks instead of 48), with detail 8 sampling at 4-block biome resolution.
- Increased the biome sample budget from 750,000 to 2,000,000 samples.
- Increased the per-region outline limit from 8,192 to 32,768 cells.
- Increased biome result capacity from 128 to 512. Structure result capacity is also 512.

## Features

- Browse registered structures and biomes, including modded entries.
- Search structures using dimension-specific generator placements, native presence checks, and direct/referenced structure-start verification.
- Nearby loaded-chunk structure verification to recover generated structures close to the player.
- Up to 512 structure and biome results with menu arrows and gameplay Left/Right keybinds.
- Shape Detail 1–8, structure-piece outlines and sampled 3D biome borders.
- Maximum search range 128 to 10,000,000 blocks, with workload caps and partial-scan reporting.
- Persistent target selection, search radius, marquee speed, tracer setting, and ESP colour.
- Proximity alerts and coordinates HUD.
- Single-player only. WorldScope does not issue `/locate`.

## Accuracy and limits

- Structure starts are verified against generated chunk data and neighboring structure references.
- Structure ESP uses generated piece bounding boxes, not exact block-occupancy meshes.
- Biome borders are sampled approximations. Their horizontal resolution is controlled by Shape Detail; vertical extents follow the contiguous matching biome quart range at each sampled column.
- The maximum search radius does not guarantee the entire area can be completed in one pass when workload limits are reached. Biome scans can stop at the 2,000,000-sample budget or 512-result limit; structure scans retain their own placement/candidate workload limits.

## Installation

Place `worldscope-2.0.14.jar` in `.minecraft/mods` and remove older WorldScope JARs.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
