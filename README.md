# WorldScope — Fabric 26.2

WorldScope is a client-side searchable GUI for locating Minecraft structures and biomes.

## Controls
- Press **K** in a world to open WorldScope.
- Choose **STRUCTURES** or **BIOMES**.
- Select a preset or enter a registry ID such as `minecraft:shipwreck` or `modid:path`, then press **FIND**.

## Compatibility
WorldScope delegates searches to Minecraft's vanilla `/locate structure` and `/locate biome` commands. This uses the active world's registries and configured world generation, so it is designed to work with Terralith, Tectonic, and other mods that register structures or biomes. Use the exact registry ID exposed by the installed mod version.

## Limitations
- Commands must be permitted. In single-player, enable cheats; multiplayer servers must allow `/locate`.
- This first version displays the vanilla command result in the WorldScope window.
- No custom map or live compass waypoint is included yet.
- The source project has not been compiled or tested here. Build with JDK 25 and Gradle 9.5.1 using `gradle build`.

## Configured versions
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Fabric Loom 1.17.0

The built JAR will be in `build/libs/`.
