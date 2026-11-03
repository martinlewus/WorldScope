package dev.worldscope;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public final class WorldScopeScanner {
    private static final int HARD_MAX_RADIUS = 10_000_000;
    private static final int MAX_STRUCTURE_CANDIDATES = 8192;
    private static final int MAX_STRUCTURE_CANDIDATES_PER_PLACEMENT = 4096;
    private static final int MAX_STRUCTURE_RESULTS = 128;
    private static final int MAX_PLACEMENT_REGION_TESTS = 1_000_000;
    private static final int MAX_BIOME_SAMPLES = 750_000;
    private static final int MAX_BIOME_RESULTS = 128;
    private static final int MAX_BIOME_REGION_CELLS = 8192;
    private static final int VERTICAL_BIOME_SAMPLE_STEP = 8;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "WorldScope-Scanner");
        thread.setDaemon(true);
        return thread;
    });

    private static final AtomicBoolean CANCELLED = new AtomicBoolean(false);
    private static final AtomicReference<List<WorldScopeHit>> ACTIVE_HITS =
            new AtomicReference<>(List.of());
    private static final AtomicInteger ACTIVE_RESULT_INDEX = new AtomicInteger();
    private static volatile boolean running;
    private static volatile String status = "Ready.";
    private static volatile int completed;
    private static volatile int total;
    private static volatile int shapeDetail = 1;
    private static volatile int searchOriginX;
    private static volatile int searchOriginZ;

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
        List<WorldScopeHit> hits = ACTIVE_HITS.get();
        if (hits.isEmpty()) return null;
        int index = Math.max(0, Math.min(ACTIVE_RESULT_INDEX.get(), hits.size() - 1));
        return hits.get(index).result();
    }

    public static List<WorldScopeBiomeCell> getActiveBiomeCells() {
        List<WorldScopeHit> hits = ACTIVE_HITS.get();
        if (hits.isEmpty()) return List.of();
        int index = Math.max(0, Math.min(ACTIVE_RESULT_INDEX.get(), hits.size() - 1));
        return hits.get(index).biomeCells();
    }

    public static List<WorldScopeBox> getActiveStructureBoxes() {
        List<WorldScopeHit> hits = ACTIVE_HITS.get();
        if (hits.isEmpty()) return List.of();
        int index = Math.max(0, Math.min(ACTIVE_RESULT_INDEX.get(), hits.size() - 1));
        return hits.get(index).structureBoxes();
    }

    public static int getResultCount() {
        return ACTIVE_HITS.get().size();
    }

    public static int getSelectedResultNumber() {
        return ACTIVE_HITS.get().isEmpty() ? 0 : ACTIVE_RESULT_INDEX.get() + 1;
    }

    public static void nextResult() {
        int count = getResultCount();
        if (count <= 1) return;
        ACTIVE_RESULT_INDEX.updateAndGet(index -> (index + 1) % count);
        status = "Showing result " + getSelectedResultNumber() + " of " + count + ".";
    }

    public static void previousResult() {
        int count = getResultCount();
        if (count <= 1) return;
        ACTIVE_RESULT_INDEX.updateAndGet(index -> (index - 1 + count) % count);
        status = "Showing result " + getSelectedResultNumber() + " of " + count + ".";
    }

    public static int getShapeDetail() {
        return shapeDetail;
    }

    public static void setShapeDetail(int detail) {
        shapeDetail = Math.max(1, Math.min(8, detail));
    }

    private static int biomeSampleStep(int detail) {
        return switch (Math.max(1, Math.min(8, detail))) {
            case 1 -> 48;
            case 2 -> 40;
            case 3 -> 32;
            case 4 -> 24;
            case 5 -> 16;
            case 6 -> 12;
            case 7 -> 8;
            default -> 4;
        };
    }

    public static void clearResult() {
        ACTIVE_HITS.set(List.of());
        ACTIVE_RESULT_INDEX.set(0);
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
        searchOriginX = originX;
        searchOriginZ = originZ;
        int originY = Math.max(targetLevel.getMinY(),
                Math.min(targetLevel.getMaxY() - 1, client.player.blockPosition().getY()));
        long worldSeed = server.getWorldGenSettings().options().seed();

        CANCELLED.set(false);
        ACTIVE_HITS.set(List.of());
        ACTIVE_RESULT_INDEX.set(0);
        running = true;
        completed = 0;
        total = 0;

        if ("biome".equals(type)) {
            status = "Scanning biome samples at detail " + shapeDetail + "...";
            WORKER.execute(() -> scanBiome(
                    targetLevel, id, dimensionId, originX, originY, originZ, radius
            ));
        } else {
            status = "Preparing structure placement search...";
            server.execute(() -> prepareStructureScan(
                    client, targetLevel, id, dimensionId, originX, originZ, radius, worldSeed
            ));
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
        if (current.equals(Level.OVERWORLD) && targetDimension.equals(Level.NETHER)) {
            return new double[]{x / 8.0, z / 8.0};
        }
        if (current.equals(Level.NETHER) && targetDimension.equals(Level.OVERWORLD)) {
            return new double[]{x * 8.0, z * 8.0};
        }
        return new double[]{0.0, 0.0};
    }

    private static void scanBiome(ServerLevel level, String wantedId, String dimensionId,
                                  int originX, int originY, int originZ, int radius) {
        final int sampleStep = biomeSampleStep(shapeDetail);
        final long radiusSquared = (long) radius * radius;
        final int[] verticalSamples = buildBiomeHeightSamples(
                level.getMinY(), level.getMaxY() - 1, originY
        );
        final Set<BiomeScanKey> coveredCells = new HashSet<>();
        final List<WorldScopeHit> found = new ArrayList<>();
        AtomicInteger samples = new AtomicInteger();
        boolean sampleLimitReached = false;

        // Rings are visited from the player's position outward. A biome result is
        // recorded once per distinct sampled patch, then its traced cells are marked
        // so the next navigation result does not simply point into the same patch.
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

                        BiomeScanKey sampleKey = new BiomeScanKey(x, sampleY >> 2, z);
                        if (coveredCells.contains(sampleKey)) continue;

                        Holder<?> biome = level.getUncachedNoiseBiome(x >> 2, sampleY >> 2, z >> 2);
                        String actualId = biome.unwrapKey()
                                .map(key -> key.identifier().toString()).orElse("");
                        completed = checked;
                        total = MAX_BIOME_SAMPLES;
                        if (!wantedId.equals(actualId)) continue;

                        int biomeMinY = (sampleY >> 2) << 2;
                        int biomeMaxY = Math.min(level.getMaxY() - 1, biomeMinY + 3);
                        List<WorldScopeBiomeCell> cells = traceBiomeRegion(
                                level, wantedId, x, z, originX, originZ,
                                biomeMinY + 2, radius, sampleStep
                        );
                        if (cells.isEmpty()) {
                            cells = List.of(new WorldScopeBiomeCell(
                                    x - sampleStep / 2, z - sampleStep / 2,
                                    x + sampleStep / 2, z + sampleStep / 2, sampleY
                            ));
                        }

                        for (WorldScopeBiomeCell cell : cells) {
                            coveredCells.add(new BiomeScanKey(
                                    cell.minX() + (cell.maxX() - cell.minX()) / 2,
                                    sampleY >> 2,
                                    cell.minZ() + (cell.maxZ() - cell.minZ()) / 2
                            ));
                        }

                        int minX = cells.stream().mapToInt(WorldScopeBiomeCell::minX).min().orElse(x - sampleStep / 2);
                        int minZ = cells.stream().mapToInt(WorldScopeBiomeCell::minZ).min().orElse(z - sampleStep / 2);
                        int maxX = cells.stream().mapToInt(WorldScopeBiomeCell::maxX).max().orElse(x + sampleStep / 2);
                        int maxZ = cells.stream().mapToInt(WorldScopeBiomeCell::maxZ).max().orElse(z + sampleStep / 2);

                        WorldScopeResult result = new WorldScopeResult(
                                "biome", wantedId, dimensionId,
                                minX, biomeMinY, minZ,
                                maxX, biomeMaxY, maxZ
                        );
                        found.add(new WorldScopeHit(result, List.of(), cells));

                        if (found.size() >= MAX_BIOME_RESULTS) break outer;
                        status = "Found " + found.size() + " biome region(s); continuing outward...";
                    }
                }
            }
        }

        if (CANCELLED.get()) {
            Minecraft.getInstance().execute(() -> finishHits(found, "Biome scan cancelled."));
            return;
        }
        if (found.isEmpty()) {
            String message = sampleLimitReached
                    ? "Biome not found before the sample budget was reached. Try a smaller radius or lower shape detail."
                    : "Biome not found in the selected radius at any sampled height.";
            Minecraft.getInstance().execute(() -> finishHits(List.of(), message));
            return;
        }

        String message = sampleLimitReached
                ? "Biome sample budget reached; showing the nearest regions found in the covered area."
                : "Biome scan complete; showing the nearest sampled regions.";
        Minecraft.getInstance().execute(() -> finishHits(found, message));
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
            int radius,
            int sampleStep
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

            long key = gridKey(point.x(), point.z());
            if (!visited.add(key)) continue;

            Holder<?> biome = level.getUncachedNoiseBiome(point.x() >> 2, quartY, point.z() >> 2);
            String actualId = biome.unwrapKey()
                    .map(resourceKey -> resourceKey.identifier().toString()).orElse("");
            completed++;
            if (!wantedId.equals(actualId)) continue;

            int half = sampleStep / 2;
            cells.add(new WorldScopeBiomeCell(
                    point.x() - half, point.z() - half,
                    point.x() + half, point.z() + half,
                    sampleY
            ));

            queue.addLast(new BiomeGridPoint(point.x() - sampleStep, point.z()));
            queue.addLast(new BiomeGridPoint(point.x() + sampleStep, point.z()));
            queue.addLast(new BiomeGridPoint(point.x(), point.z() - sampleStep));
            queue.addLast(new BiomeGridPoint(point.x(), point.z() + sampleStep));
        }

        return List.copyOf(cells);
    }

    private static long gridKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static void prepareStructureScan(Minecraft client, ServerLevel level, String wantedId,
                                             String dimensionId, int originX, int originZ, int radius,
                                             long worldSeed) {
        try {
            Structure target = findStructure(level, wantedId);
            if (target == null) {
                client.execute(() -> finishHits(List.of(),
                        "Structure ID not found in this world's registry: " + wantedId));
                return;
            }

            List<StructurePlacement> placements = findPlacements(level, target);
            if (placements.isEmpty()) {
                client.execute(() -> finishHits(List.of(),
                        "No structure placement rules were registered for " + wantedId));
                return;
            }

            ChunkGeneratorStructureState generatorState =
                    level.getChunkSource().getGeneratorState();

            // Placement-specific enumeration avoids walking every chunk in a huge
            // square. Random-spread structures use their region grid; strongholds
            // use the generator's precomputed concentric-ring positions.
            WORKER.execute(() -> {
                try {
                    status = "Finding nearest structure placement candidates...";
                    CandidateSearch search = findCandidates(
                            placements, generatorState, originX, originZ, radius, worldSeed
                    );
                    client.execute(() -> {
                        if (CANCELLED.get()) {
                            finishHits(List.of(), "Structure scan cancelled.");
                        } else if (search.candidates().isEmpty()) {
                            finishHits(List.of(), "No structure placement candidates were found inside that distance.");
                        } else {
                            total = search.candidates().size();
                            completed = 0;
                            status = "Checking nearest candidate structures...";
                            checkCandidateChunks(
                                    client, level, target, wantedId, dimensionId,
                                    search.candidates(), 0, originX, originZ,
                                    new ArrayList<>(), search.budgetReached()
                            );
                        }
                    });
                } catch (Throwable error) {
                    client.execute(() -> finishHits(List.of(),
                            "Candidate search failed: " + error.getClass().getSimpleName()
                                    + ": " + error.getMessage()));
                }
            });
        } catch (Throwable error) {
            client.execute(() -> finishHits(List.of(),
                    "Scan error: " + error.getClass().getSimpleName() + ": " + error.getMessage()));
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
            int originX,
            int originZ,
            List<WorldScopeHit> found,
            boolean placementBudgetReached
    ) {
        if (CANCELLED.get()) {
            finishHits(found, "Structure scan cancelled.");
            return;
        }

        if (index >= candidates.size() || found.size() >= MAX_STRUCTURE_RESULTS) {
            String message;
            if (found.isEmpty()) {
                message = "No generated starts were found among the " + candidates.size()
                        + " nearest placement candidates.";
            } else if (placementBudgetReached) {
                message = "Found " + found.size() + " results; placement search reached its workload limit.";
            } else if (found.size() >= MAX_STRUCTURE_RESULTS) {
                message = "Found " + found.size()
                        + " results (display limit reached; more may exist in the search radius).";
            } else {
                message = "Found " + found.size() + " nearest structure result(s).";
            }
            finishHits(found, message);
            return;
        }

        var server = client.getSingleplayerServer();
        if (server == null) {
            finishHits(found, "Integrated server stopped.");
            return;
        }

        ChunkCandidate candidate = candidates.get(index);
        server.execute(() -> {
            if (CANCELLED.get()) {
                finishHits(found, "Structure scan cancelled.");
                return;
            }

            level.getChunkSource()
                    .getChunkFuture(candidate.x(), candidate.z(), ChunkStatus.STRUCTURE_STARTS, true)
                    .whenComplete((chunkResult, error) -> server.execute(() -> {
                        if (error != null) {
                            finishHits(found, "Chunk scan failed: " + error.getClass().getSimpleName());
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
                                    List<WorldScopeBox> pieces = extractPieceBounds(start);
                                    found.add(new WorldScopeHit(result, pieces, List.of()));
                                }
                            }
                        }

                        completed = index + 1;
                        status = "Checking nearest candidate structures...";
                        checkCandidateChunks(
                                client, level, target, wantedId, dimensionId,
                                candidates, index + 1, originX, originZ,
                                found, placementBudgetReached
                        );
                    }));
        });
    }

    private static List<WorldScopeBox> extractPieceBounds(StructureStart start) {
        ArrayList<WorldScopeBox> boxes = new ArrayList<>();
        for (var piece : start.getPieces()) {
            var box = piece.getBoundingBox();
            boxes.add(new WorldScopeBox(
                    box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()
            ));
        }
        boxes.sort(Comparator.comparingLong(WorldScopeBox::volume).reversed());
        if (boxes.size() > 512) {
            return List.copyOf(boxes.subList(0, 512));
        }
        return List.copyOf(boxes);
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
            boolean contains = set.structures().stream()
                    .anyMatch(entry -> entry.structure().value() == target);
            if (contains && !placements.contains(set.placement())) {
                placements.add(set.placement());
            }
        }
        return placements;
    }

    private static CandidateSearch findCandidates(
            List<StructurePlacement> placements,
            ChunkGeneratorStructureState state,
            int originX,
            int originZ,
            int radius,
            long worldSeed
    ) {
        Map<Long, ChunkCandidate> unique = new HashMap<>();
        boolean budgetReached = false;
        long radiusSquared = (long) radius * radius;

        for (StructurePlacement placement : placements) {
            if (CANCELLED.get()) break;
            if (placement instanceof RandomSpreadStructurePlacement randomSpread) {
                CandidateSlice slice = findRandomSpreadCandidates(
                        randomSpread, state, originX, originZ, radius, worldSeed
                );
                budgetReached |= slice.budgetReached();
                for (ChunkCandidate candidate : slice.candidates()) {
                    long key = chunkKey(candidate.x(), candidate.z());
                    unique.merge(key, candidate, (a, b) ->
                            a.distanceSquared() <= b.distanceSquared() ? a : b);
                }
            } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                state.ensureStructuresGenerated();
                List<ChunkPos> ringPositions = state.getRingPositionsFor(rings);
                if (ringPositions != null) {
                    for (ChunkPos pos : ringPositions) {
                        if (!placement.isStructureChunk(state, pos.x(), pos.z())) continue;
                        ChunkCandidate candidate = makeCandidate(
                                placement, pos, originX, originZ, radiusSquared
                        );
                        if (candidate != null) {
                            unique.put(chunkKey(candidate.x(), candidate.z()), candidate);
                        }
                    }
                }
            } else {
                // The dimension-origin placement is a single explicit candidate.
                int chunkX = Math.floorDiv(originX, 16);
                int chunkZ = Math.floorDiv(originZ, 16);
                if (placement.isStructureChunk(state, chunkX, chunkZ)) {
                    ChunkCandidate candidate = new ChunkCandidate(
                            chunkX, chunkZ,
                            squaredDistance((long) chunkX * 16 + 8 - originX,
                                    (long) chunkZ * 16 + 8 - originZ)
                    );
                    if (candidate.distanceSquared() <= radiusSquared) {
                        unique.put(chunkKey(chunkX, chunkZ), candidate);
                    }
                }
            }
        }

        ArrayList<ChunkCandidate> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong(ChunkCandidate::distanceSquared));
        if (result.size() > MAX_STRUCTURE_CANDIDATES) {
            result.subList(MAX_STRUCTURE_CANDIDATES, result.size()).clear();
            budgetReached = true;
        }
        return new CandidateSearch(List.copyOf(result), budgetReached);
    }

    private static CandidateSlice findRandomSpreadCandidates(
            RandomSpreadStructurePlacement placement,
            ChunkGeneratorStructureState state,
            int originX,
            int originZ,
            int radius,
            long worldSeed
    ) {
        int spacing = placement.spacing();
        int originChunkX = Math.floorDiv(originX, 16);
        int originChunkZ = Math.floorDiv(originZ, 16);
        int originRegionX = Math.floorDiv(originChunkX, spacing);
        int originRegionZ = Math.floorDiv(originChunkZ, spacing);
        long radiusSquared = (long) radius * radius;

        PriorityQueue<RegionNode> frontier = new PriorityQueue<>(
                Comparator.comparingLong(RegionNode::minimumDistanceSquared)
        );
        Set<Long> visited = new HashSet<>();
        enqueueRegion(frontier, visited, originRegionX, originRegionZ,
                spacing, originX, originZ, radiusSquared);

        ArrayList<ChunkCandidate> candidates = new ArrayList<>();
        int tests = 0;
        boolean budgetReached = false;

        while (!frontier.isEmpty() && !CANCELLED.get()) {
            RegionNode node = frontier.poll();
            if (node.minimumDistanceSquared() > radiusSquared) break;

            if (candidates.size() >= MAX_STRUCTURE_CANDIDATES_PER_PLACEMENT) {
                long farthest = candidates.stream()
                        .mapToLong(ChunkCandidate::distanceSquared).max().orElse(Long.MAX_VALUE);
                if (node.minimumDistanceSquared() > farthest) {
                    budgetReached = true;
                    break;
                }
            }

            if (++tests > MAX_PLACEMENT_REGION_TESTS) {
                budgetReached = true;
                break;
            }

            ChunkPos potential = placement.getPotentialStructureChunk(
                    worldSeed, node.regionX(), node.regionZ()
            );
            if (placement.isStructureChunk(state, potential.x(), potential.z())) {
                ChunkCandidate candidate = makeCandidate(
                        placement, potential, originX, originZ, radiusSquared
                );
                if (candidate != null) candidates.add(candidate);
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    enqueueRegion(frontier, visited,
                            node.regionX() + dx, node.regionZ() + dz,
                            spacing, originX, originZ, radiusSquared);
                }
            }
        }

        candidates.sort(Comparator.comparingLong(ChunkCandidate::distanceSquared));
        if (candidates.size() > MAX_STRUCTURE_CANDIDATES_PER_PLACEMENT) {
            candidates.subList(MAX_STRUCTURE_CANDIDATES_PER_PLACEMENT, candidates.size()).clear();
        }
        return new CandidateSlice(List.copyOf(candidates), budgetReached);
    }

    private static void enqueueRegion(
            PriorityQueue<RegionNode> frontier,
            Set<Long> visited,
            int regionX,
            int regionZ,
            int spacing,
            int originX,
            int originZ,
            long radiusSquared
    ) {
        long key = (((long) regionX) << 32) ^ (regionZ & 0xffffffffL);
        if (!visited.add(key)) return;

        long width = (long) spacing * 16L;
        long minX = (long) regionX * width;
        long minZ = (long) regionZ * width;
        long maxX = minX + width - 1;
        long maxZ = minZ + width - 1;
        long dx = originX < minX ? minX - originX : originX > maxX ? originX - maxX : 0;
        long dz = originZ < minZ ? minZ - originZ : originZ > maxZ ? originZ - maxZ : 0;
        long lowerBound = squaredDistance(dx, dz);
        if (lowerBound <= radiusSquared) {
            frontier.add(new RegionNode(regionX, regionZ, lowerBound));
        }
    }

    private static ChunkCandidate makeCandidate(
            StructurePlacement placement,
            ChunkPos pos,
            int originX,
            int originZ,
            long radiusSquared
    ) {
        var locate = placement.getLocatePos(pos);
        long dx = (long) locate.getX() - originX;
        long dz = (long) locate.getZ() - originZ;
        long distance = squaredDistance(dx, dz);
        if (distance > radiusSquared) return null;
        return new ChunkCandidate(pos.x(), pos.z(), distance);
    }

    private static long squaredDistance(long dx, long dz) {
        return dx * dx + dz * dz;
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static long distanceToResultSquared(WorldScopeResult result, int originX, int originZ) {
        long dx = Math.max(Math.max((long) result.minX() - originX, 0L),
                (long) originX - (result.maxX() + 1L));
        long dz = Math.max(Math.max((long) result.minZ() - originZ, 0L),
                (long) originZ - (result.maxZ() + 1L));
        return squaredDistance(dx, dz);
    }

    private static void finish(WorldScopeResult result, String message) {
        if (result == null) {
            finishHits(List.of(), message);
        } else {
            List<WorldScopeBiomeCell> cells = "biome".equals(result.type())
                    ? getActiveBiomeCells() : List.of();
            finishHits(List.of(new WorldScopeHit(result, getActiveStructureBoxes(), cells)), message);
        }
    }

    private static void finishHits(List<WorldScopeHit> hits, String message) {
        ArrayList<WorldScopeHit> orderedHits = new ArrayList<>(hits);
        orderedHits.sort(Comparator.comparingLong(hit ->
                distanceToResultSquared(hit.result(), searchOriginX, searchOriginZ)));
        List<WorldScopeHit> immutableHits = List.copyOf(orderedHits);
        ACTIVE_HITS.set(immutableHits);
        ACTIVE_RESULT_INDEX.set(0);
        running = false;

        if (!immutableHits.isEmpty()) {
            WorldScopeResult first = immutableHits.get(0).result();
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && client.getSingleplayerServer() != null) {
                long seed = client.getSingleplayerServer().getWorldGenSettings().options().seed();
                WORKER.execute(() -> WorldScopeCache.save(seed, first));
            }
            status = message + " Showing " + immutableHits.size() + " result(s).";
        } else {
            status = message;
        }
    }

    private record ChunkCandidate(int x, int z, long distanceSquared) {
    }

    private record BiomeGridPoint(int x, int z) {
    }

    private record BiomeScanKey(int x, int y, int z) {
    }

    private record RegionNode(int regionX, int regionZ, long minimumDistanceSquared) {
    }

    private record CandidateSlice(List<ChunkCandidate> candidates, boolean budgetReached) {
    }

    private record CandidateSearch(List<ChunkCandidate> candidates, boolean budgetReached) {
    }
}
