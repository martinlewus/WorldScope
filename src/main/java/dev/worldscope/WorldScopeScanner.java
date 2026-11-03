package dev.worldscope;

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
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public final class WorldScopeScanner {
    private static final int HARD_MAX_RADIUS = 10_000;
    private static final int MAX_STRUCTURE_CANDIDATES = 384;
    private static final int MAX_BIOME_SAMPLES = 100_000;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WorldScope-Scanner");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicBoolean CANCELLED = new AtomicBoolean(false);
    private static final AtomicReference<WorldScopeResult> ACTIVE_RESULT = new AtomicReference<>();
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

    public static void clearResult() {
        ACTIVE_RESULT.set(null);
        status = "Result cleared.";
    }

    public static void cancel() {
        CANCELLED.set(true);
        status = "Cancelling scan...";
    }

    public static void startScan(String type, String id, String dimensionId, int requestedRadius) {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();

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

        CANCELLED.set(false);
        ACTIVE_RESULT.set(null);
        running = true;
        completed = 0;
        total = 0;
        status = "Preparing " + type + " scan...";

        if ("biome".equals(type)) {
            WORKER.execute(() -> scanBiome(targetLevel, id, dimensionId, originX, originY, originZ, radius));
        } else {
            server.execute(() -> prepareStructureScan(client, targetLevel, id, dimensionId, originX, originZ, radius));
        }
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

    private static void scanBiome(ServerLevel level, String wantedId, String dimensionId,
                                  int originX, int originY, int originZ, int radius) {
        final int sampleStep = 48;
        final int quartY = originY >> 2;
        final int radiusSquared = radius * radius;
        AtomicInteger samples = new AtomicInteger();
        WorldScopeResult found = null;

        outer:
        for (int ring = 0; ring <= radius; ring += sampleStep) {
            if (CANCELLED.get()) break;
            for (int dx = -ring; dx <= ring; dx += sampleStep) {
                for (int dz = -ring; dz <= ring; dz += sampleStep) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if ((long) dx * dx + (long) dz * dz > radiusSquared) continue;
                    if (samples.incrementAndGet() > MAX_BIOME_SAMPLES) {
                        status = "Sample safety limit reached; result coverage is partial.";
                        break outer;
                    }

                    int x = originX + dx;
                    int z = originZ + dz;
                    Holder<?> biome = level.getUncachedNoiseBiome(x >> 2, quartY, z >> 2);
                    String actualId = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
                    completed = samples.get();
                    total = Math.min(MAX_BIOME_SAMPLES, ((radius * 2 / sampleStep) + 1) * ((radius * 2 / sampleStep) + 1));

                    if (wantedId.equals(actualId)) {
                        found = new WorldScopeResult(
                                "biome", wantedId, dimensionId,
                                x - sampleStep / 2, level.getMinY(), z - sampleStep / 2,
                                x + sampleStep / 2, level.getMaxY() - 1, z + sampleStep / 2
                        );
                        break outer;
                    }
                }
            }
        }

        WorldScopeResult result = found;
        Minecraft.getInstance().execute(() -> finish(result, "biome"));
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
            List<ChunkCandidate> candidates = findCandidates(placements, generatorState, originX, originZ, radius);
            if (candidates.isEmpty()) {
                client.execute(() -> finish(null, "No candidate chunks for " + wantedId + " were found in that radius."));
                return;
            }

            total = candidates.size();
            status = "Checking candidate chunks...";
            AtomicBoolean found = new AtomicBoolean(false);
            AtomicInteger checked = new AtomicInteger();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

            for (ChunkCandidate candidate : candidates) {
                chain = chain.thenCompose(ignored -> {
                    if (CANCELLED.get() || found.get()) return CompletableFuture.completedFuture(null);

                    return level.getChunkSource()
                            .getChunkFuture(candidate.x(), candidate.z(), ChunkStatus.STRUCTURE_STARTS, true)
                            .thenAccept(optionalChunk -> {
                                if (!CANCELLED.get() && optionalChunk.isSuccess()) {
                                    var chunk = optionalChunk.orElse(null);
                                    if (chunk != null) {
                                        StructureStart start = chunk.getStartForStructure(target);
                                        if (start != null && start.isValid() && found.compareAndSet(false, true)) {
                                            var box = start.getBoundingBox();
                                            WorldScopeResult result = new WorldScopeResult(
                                                    "structure", wantedId, dimensionId,
                                                    box.minX(), box.minY(), box.minZ(),
                                                    box.maxX(), box.maxY(), box.maxZ()
                                            );
                                            ACTIVE_RESULT.set(result);
                                            client.execute(() -> finish(result, "Structure found."));
                                        }
                                    }
                                }
                                completed = checked.incrementAndGet();
                                status = "Checking candidate chunks...";
                            });
                });
            }

            chain.whenComplete((ignored, error) -> client.execute(() -> {
                if (error != null) {
                    finish(null, "Scan failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
                } else if (CANCELLED.get()) {
                    running = false;
                    status = "Scan cancelled.";
                } else if (!found.get()) {
                    finish(null, "No generated start found in the selected radius.");
                }
            }));
        } catch (Throwable error) {
            client.execute(() -> finish(null, "Scan error: " + error.getClass().getSimpleName() + ": " + error.getMessage()));
        }
    }

    private static Structure findStructure(ServerLevel level, String wantedId) {
        Identifier identifier = Identifier.parse(wantedId);
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (var holder : registry.listElements().toList()) {
            if (holder.unwrapKey().map(key -> key.location().equals(identifier)).orElse(false)) {
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
        if (result != null) ACTIVE_RESULT.set(result);
        running = false;
        status = result == null ? message : message + " " + result.id();
    }

    private record ChunkCandidate(int x, int z, long distanceSquared) {
    }
}
