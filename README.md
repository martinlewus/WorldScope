# WorldScope 2.0.6 — Fabric 26.2

WorldScope is a single-player world scanner. It searches registered structure and biome targets without chat commands or operator permissions.

## Structure search engine

- Enumerates possible structure positions using each registered structure set's actual placement strategy, including random-spread regions and concentric stronghold rings.
- Before loading a candidate chunk, asks Minecraft's native StructureManager presence checker whether a start is already stored, whether the chunk needs generation, or whether Minecraft rules the candidate out.
- Loads only saved or potentially valid candidate chunks to STRUCTURE_STARTS, then verifies the target's actual StructureStart and extracts generated piece bounds for ESP.
- Reports native presence-check counts, generated candidates without a matching start, and workload-limit status when a scan does not complete the requested radius.
- Keeps multi-result navigation, up to 128 displayed structure hits, Shape Detail 1–8, the 10,000,000-block maximum search radius, and persisted target/radius preferences.

## Accuracy and limits

- Candidate enumeration is workload bounded: up to 1,000,000 placement-region tests per placement, 4,096 candidates per placement, 8,192 aggregate candidate chunks, and 128 displayed structure results.
- A maximum radius of 10,000,000 blocks is accepted, but no one-pass completeness claim is made when workload limits are reached.
- Structure outlines use Minecraft's generated piece bounding boxes, not exact block occupancy meshes.
- Biome searches remain multi-height sampled searches with a 750,000-sample budget and 128 displayed regions.
- WorldScope remains single-player only.

## Preferences

Search radius and selected structure/biome IDs are saved in `config/worldscope/settings.properties`.

## Installation

Place `worldscope-2.0.6.jar` in `.minecraft/mods` and remove older WorldScope JARs.

## Build

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Java 25
- Fabric Loom 1.17.1

Run `gradle build --no-daemon`. The JAR is written to `build/libs/`.
