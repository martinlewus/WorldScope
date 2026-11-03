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
- Proximity alerts can be toggled and their structure trigger radius adjusted from 8 to 256 blocks. Structures trigger when the player approaches their box; biome alerts trigger on entering the approximate sampled region. The alert uses a pitched vanilla UI click sound.

## Important implementation notes

- WorldScope is intentionally single-player only. The integrated server is used directly; multiplayer sessions are rejected.
- Structure scans use structure placement data and generated structure starts, not `/locate` or the locate command's nearest-structure API.
- Candidate placement enumeration runs on a dedicated worker; actual structure-start chunk requests are issued one at a time through the integrated server. Candidate chunks are capped per scan to limit chunk-generation pressure.
- Successful target locations are saved under `config/worldscope/locations.properties`, keyed by world seed, dimension, target type, and registry ID. A cached result is reused only when it remains inside the requested radius.
- Biome results use a bounded flood-fill of 48-block samples to outline the contiguous sampled region. The gold border is an approximation at the sampled Y level, not an exact polygon of every biome volume.
- The through-wall ESP box is implemented for the active result. Detailed biome border meshes, subtype inference, persistent seed cache, proximity alerts, and alert audio remain follow-up layers.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
