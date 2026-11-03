# WorldScope 2.0.4 — Fabric 26.2

WorldScope is a **single-player world scanner**. It does not issue vanilla chat commands and does not require cheats or operator permissions.

## Features

- Press **K** in a single-player world to open the paused WorldScope screen.
- Browse the world's registered structures and biomes, including modded registry entries.
- Search the catalog, click an entry to autofill its ID, and use the village/temple subtype filters.
- Choose Overworld, Nether, or End as the scan dimension.
- Set the maximum search distance from **128 to 10,000,000 blocks**.
- Structure scans use each structure's placement method. Random-spread structures are enumerated by their placement regions, and strongholds use the world's generated concentric-ring positions.
- Browse up to **32 nearby structure results** or **16 sampled biome regions** with the previous/next arrow buttons. Results are ordered by distance from the scan origin.
- Set **Shape Detail** from 1 to 8. Structures progressively reveal the generated structure-piece bounds; biomes use progressively finer sampled borders, from 48-block to 4-block cells.
- The optional target HUD shows the selected result's dimension and bounding coordinates.
- Toggle through-wall ESP, proximity alerts, and their trigger radius from the settings panel.

## Search and accuracy notes

- WorldScope is intentionally single-player only. Multiplayer sessions are rejected.
- Structure searches inspect the world's structure sets and placement rules, then request candidate chunks through the integrated server and inspect their generated structure starts. This is independent of /locate.
- Structure ESP detail is based on the generated pieces' bounding boxes. It gives a much closer outline than the full structure-start box, but it is still an approximation of each piece's occupied blocks, not a block-perfect voxel mesh.
- Biome searches sample biome data at multiple heights, which lets underground biomes be discovered even when the player is at the surface. Their outlines are sampled approximations rather than exact biome-volume meshes.
- A scan radius of 10,000,000 blocks is accepted, but scans are workload-bounded to protect the integrated server and client. Random-spread structure enumeration is capped at 1,000,000 region tests per placement, and biome sampling is capped at 750,000 samples. When a budget is reached, the status message says the result is partial; it does not claim that the entire requested radius was exhausted.
- Each scan checks a bounded set of nearest placement candidates and returns the nearest successful starts it found. Very rare structures or very distant targets can require another scan or a smaller radius.

## Installation

Place the worldscope-2.0.4.jar file in your Minecraft .minecraft/mods folder. Remove older WorldScope JARs first.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run gradle build --no-daemon. The JAR is written to build/libs/.
