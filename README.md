# WorldScope 2.0.17 — Fabric 26.2

WorldScope is a single-player structure and biome scanner for Minecraft 26.2.

## Biome improvements in 2.0.17

- Biomes are sampled at the terrain generator's surface height instead of at arbitrary player-relative Y levels. This prevents sky/cave biome samples from being mistaken for the visible surface biome.
- Connected components use eight-way adjacency and a shared visited-tile set, preventing the same surface biome patch from being returned repeatedly or split at diagonal joins.
- Removed the misleading aggregate rectangular biome box; biome ESP now draws the sampled region boundary.
- Shape Detail now samples at 16, 12, 8, or 4-block intervals, with detail 8 sampling each 4-block biome quart cell.
- The scan counts both region tracing and outward search against a 10,000-sample budget, preventing scans from running for hours.
- Per-region outlines are capped at 8,192 cells; hitting either limit reports a partial scan.
- Coarser Shape Detail levels use larger tiles (32 blocks at Detail 1) to cover more ground per sample.
- Increased biome result capacity from 128 to 512. Structure result capacity is also 512.

## Surface biome tracing

- The scanner samples the biome at the generated terrain surface for each horizontal tile.
- Each returned region is a connected surface patch. It is not a vertical biome-volume search; cave-only biomes need a separate 3D search mode.
- Biome tiles are 32 blocks at detail 1, 24 at detail 2, 16 at detail 3, 12 at detail 4, 8 at details 5–6, and 4 at details 7–8.
- Search and flood-fill share a 10,000-sample budget, and each region can contain up to 8,192 tiles.

## Features

- Browse registered structures and biomes, including modded entries.
- Search structures using dimension-specific generator placements, native presence checks, and direct/referenced structure-start verification.
- Nearby loaded-chunk structure verification to recover generated structures close to the player.
- Up to 512 structure and biome results with menu arrows and gameplay Left/Right keybinds.
- Shape Detail 1–8, structure-piece outlines and sampled surface-biome borders.
- Maximum search range 128 to 10,000,000 blocks, with workload caps and partial-scan reporting.
- Persistent target selection, search radius, marquee speed, tracer setting, and ESP colour.
- Proximity alerts and coordinates HUD.
- Single-player only. WorldScope does not issue `/locate`.

## Accuracy and limits

- Structure starts are verified against generated chunk data and neighboring structure references.
- Structure ESP uses generated piece bounding boxes, not exact block-occupancy meshes.
- Biome borders are sampled surface approximations drawn as thin wireframe edges (not filled translucent walls). Their horizontal resolution is controlled by Shape Detail; they follow the generator's surface height and do not represent cave-only or full 3D biome volumes.
- The maximum search radius does not guarantee the entire area can be completed in one pass when workload limits are reached. Biome scans can stop at the 10,000-sample budget, 8,192-tile region limit, or 512-result limit; structure scans retain their own placement/candidate workload limits.

## Installation

Place `worldscope-2.0.17.jar` in `.minecraft/mods` and remove older WorldScope JARs.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
