# WorldScope 2.0.12 — Fabric 26.2

WorldScope is a single-player structure and biome scanner for Minecraft 26.2.

## 2.0.12 changes

- The bottom status marquee is blue and clickable. Clicking it copies the complete current status/progress message to the system clipboard.
- Added a persistent **TRACER ON/OFF** option, disabled by default. It can be enabled independently of the ESP boxes.
- The tracer is a through-wall 3D beam from the camera's crosshair direction to the center of the selected structure or biome bounds.
- Added an ESP colour swatch in the top-right of the WorldScope header. Clicking it opens a Minecraft-style hue/saturation colour wheel.
- The selected colour applies to the ESP box, detail-piece outlines, biome borders, and tracer so they always match.
- Tracer enablement and ESP colour are saved in `config/worldscope/settings.properties`.

## Features

- Browse registered structures and biomes, including modded entries.
- Search structures using dimension-specific generator placements, native presence checks, and direct/referenced structure-start verification.
- Up to 128 structure and biome results with menu arrows and gameplay Left/Right keybinds.
- Shape Detail 1–8, structure-piece outlines and sampled biome borders.
- Maximum search range 128 to 10,000,000 blocks, with workload caps and partial-scan reporting.
- Persistent target selection, search radius, marquee speed, tracer setting, and ESP colour.
- Proximity alerts and coordinates HUD.
- Single-player only. WorldScope does not issue `/locate`.

## Accuracy and limits

- Structure starts are verified against generated chunk data and neighboring structure references.
- Structure ESP uses generated piece bounding boxes, not exact block-occupancy meshes.
- Biome borders are sampled approximations.
- The maximum search radius does not guarantee the entire area can be completed in one pass when workload limits are reached.

## Installation

Place `worldscope-2.0.12.jar` in `.minecraft/mods` and remove older WorldScope JARs.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
