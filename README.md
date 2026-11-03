# WorldScope 2.0.10 — Fabric 26.2

WorldScope is a single-player structure and biome scanner for Minecraft 26.2.

## 2.0.10 changes

- Structure candidates that Minecraft's presence cache reports as `START_NOT_PRESENT` are no longer discarded immediately. WorldScope independently loads the candidate to `STRUCTURE_STARTS` and checks the actual structure-start map, so a cache-negative answer cannot by itself hide a matching saved start.
- Up to 512 cache-negative candidate chunks are independently verified per scan. The final status reports how many cache-negative candidates contained the selected structure, how many remained empty, and the chunk coordinates of the first eight checked candidates. Any candidates beyond the safety cap are explicitly reported as not verified.
- The GUI header now has a persistent **SPEED 1x–4x** button for the status marquee. The default is 2x; each click cycles through four speeds and saves the choice in `config/worldscope/settings.properties`.
- Marquee drawing now interpolates between client ticks, so movement is smoother at higher frame rates. The existing two-second delay before each new message remains.

## Features

- Browse registered structures and biomes, including modded entries.
- Search the registry and filter structure subtypes.
- Select Overworld, Nether, or End.
- Maximum search radius from 128 to 10,000,000 blocks, with workload limits and partial-scan reporting.
- Structure candidates use the active dimension's `ChunkGeneratorStructureState` placement index and level seed.
- Minecraft's native structure-presence checker is used as a hint, followed by direct `STRUCTURE_STARTS` chunk verification for cache-negative candidates.
- Up to 128 structure and biome results with arrow-button and keyboard navigation.
- Left/Right arrows also navigate results during normal gameplay outside the menu.
- Shape Detail 1–8, structure-piece outlines, biome borders, through-wall ESP, coordinate HUD, and proximity alerts.
- Selected targets, search radius, and marquee speed are saved in `config/worldscope/settings.properties`.

## Accuracy and limits

- A candidate placement is only a possible start position. Minecraft can still reject it because the selected structure is not valid for that generation point or the structure set chooses another entry.
- Cache-negative candidate fallbacks are limited to 512 per scan to avoid generating thousands of distant chunks. If the cap is reached, the status identifies how many candidates were not independently verified.
- Structure candidate enumeration is capped at 1,000,000 placement-region tests per placement, 4,096 candidates per placement, and 8,192 aggregate candidate chunks.
- Biome scanning remains a multi-height sampled approximation, with a 750,000-sample budget and 128 displayed regions.
- Structure-piece boxes are approximate layout bounds rather than block-perfect voxel meshes.
- The 10,000,000-block maximum is accepted but does not guarantee complete coverage in one scan when workload limits are reached.
- WorldScope is single-player only and does not issue `/locate`.

## Installation

Place `worldscope-2.0.10.jar` in `.minecraft/mods` and remove older WorldScope JARs.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
