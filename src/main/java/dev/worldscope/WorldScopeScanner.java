package dev.worldscope;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public final class WorldScopeScanner {
    private static final int HARD_MAX_RADIUS = 10_000;
    private static final int MAX_STRUCTURE_CANDIDATES = 1024;
    private static final int MAX_BIOME_SAMPLES = 750_000;
    private static final int VERTICAL_BIOME_SAMPLE_STEP = 8;
    private static final int MAX_BIOME_REGION_CELLS = 512;
    private static final int BIOME_SAMPLE_STEP = 48;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WorldScope-Scanner");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicBoolean CANCELLED = new AtomicBoolean(false);
    private static final AtomicReference<WorldScopeResult> ACTIVE_RESULT = new AtomicReference<>();
    private static final AtomicReference<List<WorldScopeBiomeCell>> ACTIVE_BIOME_CELLS = new AtomicReference<>(List.of());
    private static volatile boolean running;
    private static volatile String status = "Ready.";
    private static volatile int completed;
    private static volatile int total;

    private WorldScopeScanner() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static String getStatus() {
        return status;
    }

    public static String getProgressText() {
        if (!running) return status;
        if (total <= 0) return status;
        return status + "  " + completed + "/" + total;
    }

    public static WorldScopeResult getActiveResult() {
        return ACTIVE_RESULT.get();
    }

    public static List<WorldScopeBiomeCell> getActiveBiomeCells() {
        return ACTIVE_BIOME_CELLS.get();
    }

    public static void clearResult() {
        ACTIVE_RESULT.set(null);
        ACTIVE_BIOME_CELLS.set(List.of());
        status = "Result cleared.";
    }

    public static void cancel() {
        CANCELLED.set(true);
        status = "Cancelling scan...";
    }

    public static void startScan(String type, String id, String dimensionId, int requestedRadius) {
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();

        if (server == null || client.player == null || client.level == null) {
            status = "WorldScope scanning is single-player only.";
            running = false;
            return;
        }

        if (running) {
            status = "A scan is already running. Cancel it before starting another.";
            return;
        }

        int radius = Math.max(128, Math.min(HARD_MAX_RADIUS, requestedRadius));
        ResourceKey<Level> dimension = parseDimension(dimensionId);
        if (dimension == null) {
            status = "Unknown dimension: " + dimensionId;
            return;
        }

        ServerLevel targetLevel = server.getLevel(dimension);
        if (targetLevel == null) {
            status = "That dimension is not available in this world.";
            return;
        }

        double[] center = getSearchCenter(client, dimension);
        int originX = (int) Math.floor(center[0]);
        int originZ = (int) Math.floor(center[1]);
        int originY = Math.max(targetLevel.getMinY(), Math.min(targetLevel.getMaxY() - 1, client.player.blockPosition().getY()));
        long worldSeed = server.getWorldGenSettings().options().seed();

        CANCELLED.set(false);
        ACTIVE_RESULT.set(null);
        ACTIVE_BIOME_CELLS.set(List.of());
        running = true;
        completed = 0;
        total = 0;
        status = "Checking saved scan cache...";

        WORKER.execute(() -> {
            WorldScopeResult cached = WorldScopeCache.load(worldSeed, dimensionId, type, id);
            if (cached != null && isInsideRadius(cached, originX, originZ, radius)) {
                if ("biome".equals(type)) {
                    ACTIVE_BIOME_CELLS.set(traceBiomeRegion(
                            targetLevel, id, cached.centerX(), cached.centerZ(),
                            originX, originZ, cached.centerY(), radius
                    ));
                }
                client.execute(() -> finish(cached, "Loaded saved location."));
                return;
            }

            if ("biome".equals(type)) {
                scanBiome(targetLevel, id, dimensionId, originX, originY, originZ, radius);
            } else {
                server.execute(() -> prepareStructureScan(client, targetLevel, id, dimensionId, originX, originZ, radius));
            }
        });
    }

    private static ResourceKey<Level> parseDimension(String id) {
        return switch (id) {
            case "minecraft:overworld" -> Level.OVERWORLD;
            case "minecraft:the_nether" -> Level.NETHER;
            case "minecraft:the_end" -> Level.END;
            default -> null;
        };
    }

    private static double[] getSearchCenter(Minecraft client, ResourceKey<Level> targetDimension) {
        double x = client.player.getX();
        double z = client.player.getZ();
        ResourceKey<Level> current = client.level.dimension();

        if (current.equals(targetDimension)) return new double[]{x, z};
        if (current.equals(Level.OVERWORLD) && targetDimension.equals(Level.NETHER)) return new double[]{x / 8.0, z / 8.0};
        if (current.equals(Level.NETHER) && targetDimension.equals(Level.OVERWORLD)) return new double[]{x * 8.0, z * 8.0};

        return new double[]{0.0, 0.0};
    }

    private static boolean isInsideRadius(WorldScopeResult result, int originX, int originZ, int radius) {
        double dx = Math.max(Math.max(result.minX() - originX, 0.0), originX - (result.maxX() + 1.0));
        double dz = Math.max(Math.max(result.minZ() - originZ, 0.0), originZ - (result.maxZ() + 1.0));
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    private static void scanBiome(ServerLevel level, String wantedId, String dimensionId,
                                  int originX, int originY, int originZ, int radius) {
        final int sampleStep = BIOME_SAMPLE_STEP;
        final long radiusSquared = (long) radius * radius;
        final int[] verticalSamples = buildBiomeHeightSamples(
                level.getMinY(), level.getMaxY() - 1, originY
        );
        AtomicInteger samples = new AtomicInteger();
        WorldScopeResult firstMatch = null;
        boolean sampleLimitReached = false;

        // Search outward horizontally, but sample the full build-height range at
        // each horizontal point. This allows underground biomes such as Deep Dark
        // to be found even when the player is standing at the surface.
        outer:
        for (int ring = 0; ring <= radius; ring += sampleStep) {
            if (CANCELLED.get()) break;
            for (int dx = -ring; dx <= ring; dx += sampleStep) {
                for (int dz = -ring; dz <= ring; dz += sampleStep) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if ((long) dx * dx + (long) dz * dz > radiusSquared) continue;

                    int x = originX + dx;
                    int z = originZ + dz;
                    for (int sampleY : verticalSamples) {
                        if (CANCELLED.get()) break outer;
                        int checked = samples.incrementAndGet();
                        if (checked > MAX_BIOME_SAMPLES) {
                            sampleLimitReached = true;
                            break outer;
                        }

                        Holder<?> biome = level.getUncachedNoiseBiome(x >> 2, sampleY >> 2, z >> 2);
                        String actualId = biome.unwrapKey()
                                .map(key -> key.identifier().toString()).orElse("");
                        completed = checked;
                        total = MAX_BIOME_SAMPLES;

                        if (wantedId.equals(actualId)) {
                            int halfHeight = VERTICAL_BIOME_SAMPLE_STEP;
                            firstMatch = new WorldScopeResult(
                                    "biome", wantedId, dimensionId,
                                    x - sampleStep / 2, Math.max(level.getMinY(), sampleY - halfHeight),
                                    z - sampleStep / 2,
                                    x + sampleStep / 2, Math.min(level.getMaxY() - 1, sampleY + halfHeight),
                                    z + sampleStep / 2
                            );
                            break outer;
                        }
                    }
                }
            }
        }

        if (CANCELLED.get() || firstMatch == null) {
            String message = CANCELLED.get()
                    ? "Scan cancelled."
                    : sampleLimitReached
                        ? "Biome not found before the scan safety limit. Try a smaller radius."
                        : "Biome not found in the selected radius at any sampled height.";
            Minecraft.getInstance().execute(() -> {
                ACTIVE_BIOME_CELLS.set(List.of());
                finish(null, message);
            });
            return;
        }

        status = "Tracing sampled biome border...";
        int matchedY = firstMatch.centerY();
        List<WorldScopeBiomeCell> cells = traceBiomeRegion(
                level, wantedId, firstMatch.centerX(), firstMatch.centerZ(),
                originX, originZ, matchedY, radius
        );
        ACTIVE_BIOME_CELLS.set(cells);

        int minX = cells.stream().mapToInt(WorldScopeBiomeCell::minX).min().orElse(firstMatch.minX());
        int minZ = cells.stream().mapToInt(WorldScopeBiomeCell::minZ).min().orElse(firstMatch.minZ());
        int maxX = cells.stream().mapToInt(WorldScopeBiomeCell::maxX).max().orElse(firstMatch.maxX());
        int maxZ = cells.stream().mapToInt(WorldScopeBiomeCell::maxZ).max().orElse(firstMatch.maxZ());

        WorldScopeResult result = new WorldScopeResult(
                "biome", wantedId, dimensionId,
                minX, firstMatch.minY(), minZ,
                maxX, firstMatch.maxY(), maxZ
        );
        Minecraft.getInstance().execute(() -> finish(result, "Biome region found across build height; sampled border ready."));
    }

    private static int[] buildBiomeHeightSamples(int minY, int maxY, int preferredY) {
        int centerY = Math.max(minY, Math.min(maxY, preferredY));
        ArrayList<Integer> samples = new ArrayList<>();
        for (int offset = 0; centerY - offset >= minY || centerY + offset <= maxY;
             offset += VERTICAL_BIOME_SAMPLE_STEP) {
            int below = centerY - offset;
            int above = centerY + offset;
            if (below >= minY && !samples.contains(below)) samples.add(below);
            if (offset != 0 && above <= maxY && !samples.contains(above)) samples.add(above);
        }
        if (!samples.contains(minY)) samples.add(minY);
        if (!samples.contains(maxY)) samples.add(maxY);
        return samples.stream().mapToInt(Integer::intValue).toArray();
    }

    private static List<WorldScopeBiomeCell> traceBiomeRegion(
            ServerLevel level,
            String wantedId,
            int seedX,
            int seedZ,
            int originX,
            int originZ,
            int sampleY,
            int radius
    ) {
        ArrayList<WorldScopeBiomeCell> cells = new ArrayList<>();
        ArrayDeque<BiomeGridPoint> queue = new ArrayDeque<>();
        HashSet<Long> visited = new HashSet<>();
        int quartY = sampleY >> 2;
        long radiusSquared = (long) radius * radius;

        queue.add(new BiomeGridPoint(seedX, seedZ));
        while (!queue.isEmpty() && cells.size() < MAX_BIOME_REGION_CELLS && !CANCELLED.get()) {
            BiomeGridPoint point = queue.removeFirst();
            long dx = (long) point.x() - originX;
            long dz = (long) point.z() - originZ;
            if (dx * dx + dz * dz > radiusSquared) continue;

            long key = (((long) point.x()) << 32) ^ (point.z() & 0xffffffffL);
            if (!visited.add(key)) continue;

            Holder<?> biome = level.getUncachedNoiseBiome(point.x() >> 2, quartY, point.z() >> 2);
            String actualId = biome.unwrapKey().map(resourceKey -> resourceKey.identifier().toString()).orElse("");
            completed++;
            if (!wantedId.equals(actualId)) continue;

            int half = BIOME_SAMPLE_STEP / 2;
            cells.add(new WorldScopeBiomeCell(
                    point.x() - half, point.z() - half,
                    point.x() + half, point.z() + half,
                    sampleY
            ));

            queue.addLast(new BiomeGridPoint(point.x() - BIOME_SAMPLE_STEP, point.z()));
            queue.addLast(new BiomeGridPoint(point.x() + BIOME_SAMPLE_STEP, point.z()));
            queue.addLast(new BiomeGridPoint(point.x(), point.z() - BIOME_SAMPLE_STEP));
            queue.addLast(new BiomeGridPoint(point.x(), point.z() + BIOME_SAMPLE_STEP));
        }

        return List.copyOf(cells);
    }

    private static void prepareStructureScan(Minecraft client, ServerLevel level, String wantedId,
                                             String dimensionId, int originX, int originZ, int radius) {
        try {
            Structure target = findStructure(level, wantedId);
            if (target == null) {
                client.execute(() -> finish(null, "Structure ID not found in this world's registry: " + wantedId));
                return;
            }

            List<StructurePlacement> placements = findPlacements(level, target);
            if (placements.isEmpty()) {
                client.execute(() -> finish(null, "No structure placement rules were registered for " + wantedId));
                return;
            }

            Object generatorState = level.getChunkSource().getGeneratorState();

            // Candidate enumeration may cover more than a million chunk coordinates
            // at the maximum radius. Keep that CPU work off the integrated-server tick.
            WORKER.execute(() -> {
                try {
                    List<ChunkCandidate> candidates = findCandidates(
                            placements, generatorState, originX, originZ, radius
                    );
                    client.execute(() -> {
                        if (CANCELLED.get()) {
                            running = false;
                            status = "Scan cancelled.";
                        } else if (candidates.isEmpty()) {
                            finish(null, "No candidate chunks for " + wantedId + " were found in that radius.");
                        } else {
                            total = candidates.size();
                            completed = 0;
                            status = "Checking candidate chunks...";
                            checkCandidateChunks(client, level, target, wantedId, dimensionId,
                                    candidates, 0, new AtomicInteger());
                        }
                    });
                } catch (Throwable error) {
                    client.execute(() -> finish(null,
                            "Candidate search failed: " + error.getClass().getSimpleName()
                                    + ": " + error.getMessage()));
                }
            });
        } catch (Throwable error) {
            client.execute(() -> finish(null, "Scan error: " + error.getClass().getSimpleName() + ": " + error.getMessage()));
        }
    }

    private static void checkCandidateChunks(
            Minecraft client,
            ServerLevel level,
            Structure target,
            String wantedId,
            String dimensionId,
            List<ChunkCandidate> candidates,
            int index,
            AtomicInteger checked
    ) {
        if (CANCELLED.get()) {
            running = false;
            status = "Scan cancelled.";
            return;
        }

        if (index >= candidates.size()) {
            finish(null, "Checked " + candidates.size() + " candidate chunks; no generated start for " + wantedId + " was found in the selected radius.");
            return;
        }

        var server = client.getSingleplayerServer();
        if (server == null) {
            running = false;
            status = "Integrated server stopped.";
            return;
        }

        ChunkCandidate candidate = candidates.get(index);
        server.execute(() -> {
            if (CANCELLED.get()) {
                running = false;
                status = "Scan cancelled.";
                return;
            }

            level.getChunkSource()
                    .getChunkFuture(candidate.x(), candidate.z(), ChunkStatus.STRUCTURE_STARTS, true)
                    .whenComplete((chunkResult, error) -> server.execute(() -> {
                        if (error != null) {
                            finish(null, "Chunk scan failed: " + error.getClass().getSimpleName());
                            return;
                        }

                        if (!CANCELLED.get() && chunkResult != null && chunkResult.isSuccess()) {
                            var chunk = chunkResult.orElse(null);
                            if (chunk != null) {
                                StructureStart start = chunk.getStartForStructure(target);
                                if (start != null && start.isValid()) {
                                    var box = start.getBoundingBox();
                                    WorldScopeResult result = new WorldScopeResult(
                                            "structure", wantedId, dimensionId,
                                            box.minX(), box.minY(), box.minZ(),
                                            box.maxX(), box.maxY(), box.maxZ()
                                    );
                                    client.execute(() -> finish(result, "Structure found."));
                                    return;
                                }
                            }
                        }

                        completed = checked.incrementAndGet();
                        status = "Checking candidate chunks...";
                        checkCandidateChunks(client, level, target, wantedId, dimensionId,
                                candidates, index + 1, checked);
                    }));
        });
    }

    private static Structure findStructure(ServerLevel level, String wantedId) {
        Identifier identifier = Identifier.parse(wantedId);
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (var holder : registry.listElements().toList()) {
            if (holder.unwrapKey().map(key -> key.identifier().equals(identifier)).orElse(false)) {
                return holder.value();
            }
        }
        return null;
    }

    private static List<StructurePlacement> findPlacements(ServerLevel level, Structure target) {
        List<StructurePlacement> placements = new ArrayList<>();
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        for (var holder : sets.listElements().toList()) {
            StructureSet set = holder.value();
            boolean contains = set.structures().stream().anyMatch(entry -> entry.structure().value() == target);
            if (contains && !placements.contains(set.placement())) {
                placements.add(set.placement());
            }
        }
        return placements;
    }

    private static List<ChunkCandidate> findCandidates(List<StructurePlacement> placements, Object stateObject,
                                                       int originX, int originZ, int radius) {
        @SuppressWarnings("unchecked")
        var state = (net.minecraft.world.level.chunk.ChunkGeneratorStructureState) stateObject;

        int chunkRadius = (radius + 15) >> 4;
        long radiusSquared = (long) radius * radius;
        Comparator<ChunkCandidate> nearestFirst = Comparator.comparingLong(ChunkCandidate::distanceSquared);
        PriorityQueue<ChunkCandidate> nearest = new PriorityQueue<>(MAX_STRUCTURE_CANDIDATES, nearestFirst.reversed());
        Set<Long> seen = new HashSet<>();

        int originChunkX = Math.floorDiv(originX, 16);
        int originChunkZ = Math.floorDiv(originZ, 16);

        for (int dx = -chunkRadius; dx <= chunkRadius && !CANCELLED.get(); dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int blockDx = dx * 16;
                int blockDz = dz * 16;
                long distanceSquared = (long) blockDx * blockDx + (long) blockDz * blockDz;
                if (distanceSquared > radiusSquared) continue;

                int chunkX = originChunkX + dx;
                int chunkZ = originChunkZ + dz;
                boolean candidate = placements.stream().anyMatch(placement ->
                        placement.isStructureChunk(state, chunkX, chunkZ));
                if (!candidate) continue;

                long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                if (!seen.add(key)) continue;

                ChunkCandidate entry = new ChunkCandidate(chunkX, chunkZ, distanceSquared);
                if (nearest.size() < MAX_STRUCTURE_CANDIDATES) {
                    nearest.add(entry);
                } else if (nearestFirst.compare(entry, nearest.peek()) < 0) {
                    nearest.poll();
                    nearest.add(entry);
                }
            }
        }

        List<ChunkCandidate> result = new ArrayList<>(nearest);
        result.sort(nearestFirst);
        return result;
    }

    private static void finish(WorldScopeResult result, String message) {
        if (result != null) {
            ACTIVE_RESULT.set(result);
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && client.getSingleplayerServer() != null) {
                long seed = client.getSingleplayerServer().getWorldGenSettings().options().seed();
                WORKER.execute(() -> WorldScopeCache.save(seed, result));
            }
        }
        running = false;
        status = result == null ? message : message + " " + result.id();
    }

    private record ChunkCandidate(int x, int z, long distanceSquared) {
    }

    private record BiomeGridPoint(int x, int z) {
    }
}
