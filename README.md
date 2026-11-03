# WorldScope 2.0.5 — Fabric 26.2

WorldScope is a **single-player world scanner**. It does not issue vanilla chat commands and does not require cheats or operator permissions.

## Features

- Press **K** in a single-player world to open the paused WorldScope screen.
- Browse registered structures and biomes, including modded registry entries.
- Search the catalog, click an entry to autofill its ID, and use the village/temple subtype filters.
- Choose Overworld, Nether, or End as the scan dimension.
- Set maximum search distance from **128 to 10,000,000 blocks**.
- The distance setting and last selected target for each catalog mode are saved in `config/worldscope/settings.properties`.
- Structure scans use each structure's placement method. Random-spread structures are enumerated by placement regions; strongholds use generated concentric-ring positions.
- Browse up to **128 structure results** or **128 sampled biome regions** using the previous/next arrows. Results are sorted by distance from the scan origin.
- **Shape Detail** ranges from 1 to 8. Structures progressively reveal generated piece bounds; biomes use progressively finer sampled outlines.
- The optional HUD shows the selected result's dimension and bounding coordinates.
- Through-wall ESP uses a two-sided render pipeline so the outlines remain visible when viewed from different angles.
- Proximity alerts and their trigger radius remain configurable.

## Search and accuracy notes

- WorldScope is intentionally single-player only. Multiplayer sessions are rejected.
- Structure scans use structure-set placement data and generated structure starts, not `/locate`.
- Structure ESP detail uses generated piece bounding boxes. These are more precise than one overall start box but are not block-perfect voxel meshes.
- Biome scans sample multiple heights and can find underground biomes from the surface. Their outlines are sampled approximations, not exact biome-volume meshes.
- Nearby structure placement candidates are checked more generously than before to reduce missed close results. The scanner can still be workload-limited at extreme distances: up to 1,000,000 placement-region tests per placement, 8,192 aggregate candidate chunks, and 128 displayed results.
- Biome sampling is capped at 750,000 samples and 128 displayed regions. When a budget/display cap is reached, the status says the results may be partial. A 10,000,000-block radius is accepted but cannot guarantee a complete scan of that enormous area within one pass.

## Installation

Place `worldscope-2.0.5.jar` in the Minecraft `.minecraft/mods` folder. Remove older WorldScope JARs first.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
