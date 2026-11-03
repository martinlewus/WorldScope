# WorldScope 2.0 — Fabric 26.2

WorldScope is being rebuilt as a **single-player world scanner**. It does not issue vanilla chat commands and does not require cheats or operator permissions.

## Current 2.0 foundation

- Press **K** in a single-player world to open the paused WorldScope screen.
- Browse the world's synced structure and biome registries, including modded registry entries.
- Scroll the catalog and click an entry to autofill its full registry ID.
- Choose Overworld, Nether, or End as the scan dimension.
- Set a scan radius from 128 to 10,000 blocks.
- Structure searches inspect registered structure-set placement rules, then request candidate chunks only up to the structure-start stage and inspect their generated structure starts.
- Biome searches sample the generator's biome data at the player's vertical level.
- The optional HUD readout shows the selected result's dimension and bounding coordinates.
- Toggle the through-wall ESP box from the right-hand settings panel. Structure bounds come from generated structure starts; biome bounds are the current approximate sample region.

## Important implementation notes

- WorldScope is intentionally single-player only. The integrated server is used directly; multiplayer sessions are rejected.
- Structure scans use structure placement data and generated structure starts, not `/locate` or the locate command's nearest-structure API.
- Candidate structure chunks are checked sequentially and capped per scan to limit chunk-generation pressure.
- Biome results are approximate sample cells, not exact biome polygons yet.
- The through-wall ESP box is implemented for the active result. Detailed biome border meshes, subtype inference, persistent seed cache, proximity alerts, and alert audio remain follow-up layers.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
