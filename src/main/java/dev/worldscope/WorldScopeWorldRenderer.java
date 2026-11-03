package dev.worldscope;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class WorldScopeWorldRenderer {
    private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("worldscope", "pipeline/target_box_through_walls"))
                    .withDepthStencilState(Optional.empty())
                    .withCull(false)
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer BUFFER =
            new StagedVertexBuffer(() -> "WorldScope target box", RenderType.SMALL_BUFFER_SIZE);

    private static volatile BoxState boxState;

    private WorldScopeWorldRenderer() {
    }

    public static void initialize() {
        LevelExtractionEvents.END_EXTRACTION.register(WorldScopeWorldRenderer::extractTarget);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WorldScopeWorldRenderer::drawTarget);
    }

    private static void extractTarget(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        WorldScopeResult result = WorldScopeScanner.getActiveResult();

        boolean espEnabled = WorldScopeClient.isEspEnabled();
        boolean tracerEnabled = WorldScopeUserSettings.isTracerEnabled();
        if ((!espEnabled && !tracerEnabled) || client.level == null
                || client.getSingleplayerServer() == null || result == null) {
            boxState = null;
            return;
        }

        String currentDimension = client.level.dimension().identifier().toString();
        if (!currentDimension.equals(result.dimension())) {
            boxState = null;
            return;
        }

        boolean biomeTarget = "biome".equals(result.type());
        List<WorldScopeBiomeCell> biomeCells = biomeTarget
                ? WorldScopeScanner.getActiveBiomeCells() : List.of();
        List<WorldScopeBox> structureBoxes = biomeTarget
                ? List.of() : WorldScopeScanner.getActiveStructureBoxes();
        boxState = new BoxState(
                result.minX(), result.minY(), result.minZ(),
                result.maxX() + 1, result.maxY(), result.maxZ() + 1,
                structureBoxes, biomeCells, WorldScopeScanner.getShapeDetail(),
                WorldScopeUserSettings.getBoxColor(),
                espEnabled,
                tracerEnabled
        );
    }

    private static void drawTarget(LevelRenderContext context) {
        BoxState state = boxState;
        if (state == null) return;

        VertexFormat format = THROUGH_WALLS.getVertexFormatBinding(0);
        if (format == null) return;

        PrimitiveTopology topology = THROUGH_WALLS.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = BUFFER.appendDraw(
                format,
                topology,
                topology == PrimitiveTopology.QUADS
                        ? RenderSystem.getProjectionType().vertexSorting()
                        : null
        );

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer vertices = BUFFER.getVertexBuilder(draw);
        float minX = state.minX();
        float minY = state.minY();
        float minZ = state.minZ();
        float maxX = state.maxX();
        float maxY = state.maxY();
        float maxZ = state.maxZ();
        int rgb = state.colorRgb();
        float red = ((rgb >> 16) & 0xFF) / 255.0f;
        float green = ((rgb >> 8) & 0xFF) / 255.0f;
        float blue = (rgb & 0xFF) / 255.0f;

        if (state.espEnabled()) {
        if (state.biomeCells().isEmpty()) {
            // Detail 1 is the original single bounding box. Higher settings fade
            // that broad box back and progressively reveal the generated room/piece
            // bounds from StructureStart#getPieces().
            // Keep a faint overall boundary even at detail 8. Some vanilla
            // structures can expose an incomplete or empty piece list from their
            // reference chunk; the aggregate start box is the reliable fallback
            // and also makes the selected target easy to identify.
            float aggregateAlpha = state.structureBoxes().isEmpty()
                    ? 1.0f
                    : Math.max(0.22f, 0.92f * (8 - state.shapeDetail()) / 7.0f);
            if (state.shapeDetail() == 1) aggregateAlpha = 0.92f;
            if (aggregateAlpha > 0.01f) {
                addCuboid(matrices.last().pose(), vertices,
                        minX, minY, minZ, maxX, maxY, maxZ,
                        red, green, blue, 0.06f * aggregateAlpha);
                addFrame(matrices.last().pose(), vertices,
                        minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, aggregateAlpha);
            }

            List<WorldScopeBox> pieces = state.structureBoxes();
            int detail = state.shapeDetail();
            int pieceCount = detail <= 1 ? 0
                    : Math.min(pieces.size(), (int) Math.ceil(pieces.size() * (detail - 1) / 7.0));
            for (int i = 0; i < pieceCount; i++) {
                WorldScopeBox piece = pieces.get(i);
                float pMinX = piece.minX();
                float pMinY = piece.minY();
                float pMinZ = piece.minZ();
                float pMaxX = piece.maxX() + 1;
                float pMaxY = piece.maxY() + 1;
                float pMaxZ = piece.maxZ() + 1;
                addCuboid(matrices.last().pose(), vertices,
                        pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ,
                        red, green, blue, 0.025f);
                addFrame(matrices.last().pose(), vertices,
                        pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ, red, green, blue, 0.95f);
            }
        } else {
            // Biome targets are rendered from their traced boundary columns only.
            // Do not draw one enclosing rectangular box: it hides the actual
            // sampled footprint and makes a biome look like a single chunk marker.
            addBiomeBorders(matrices.last().pose(), vertices, state.biomeCells(), red, green, blue);
        }
        }
        if (state.tracerEnabled()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                Vec3 look = client.player.getLookAngle().normalize();
                Vec3 tracerStart = camera.add(look.scale(0.35));
                Vec3 targetCenter = new Vec3(
                        (state.minX() + state.maxX()) * 0.5,
                        (state.minY() + state.maxY()) * 0.5,
                        (state.minZ() + state.maxZ()) * 0.5
                );
                addTracer(matrices.last().pose(), vertices, tracerStart, targetCenter,
                        red, green, blue);
            }
        }

        matrices.popPose();

        BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = BUFFER.getExecuteInfo(draw);
        if (info != null) draw(Minecraft.getInstance(), info, THROUGH_WALLS);
        BUFFER.endFrame();
    }

    private static void addTracer(Matrix4fc matrix, VertexConsumer vertices,
                                   Vec3 from, Vec3 to,
                                   float red, float green, float blue) {
        Vec3 direction = to.subtract(from);
        double lengthSquared = direction.lengthSqr();
        if (lengthSquared < 1.0) return;
        direction = direction.normalize();

        Vec3 referenceUp = Math.abs(direction.y) > 0.92
                ? new Vec3(1.0, 0.0, 0.0)
                : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(referenceUp).normalize().scale(0.035);
        Vec3 vertical = direction.cross(side).normalize().scale(0.035);

        // Two perpendicular ribbons form a thin, camera-readable 3D tracer.
        addTracerRibbon(matrix, vertices, from, to, side, red, green, blue);
        addTracerRibbon(matrix, vertices, from, to, vertical, red, green, blue);
    }

    private static void addTracerRibbon(Matrix4fc matrix, VertexConsumer vertices,
                                        Vec3 from, Vec3 to, Vec3 offset,
                                        float red, float green, float blue) {
        Vec3 a = from.add(offset);
        Vec3 b = from.subtract(offset);
        Vec3 c = to.subtract(offset);
        Vec3 d = to.add(offset);
        vertices.addVertex(matrix, (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, 0.95f);
        vertices.addVertex(matrix, (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, 0.95f);
        vertices.addVertex(matrix, (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, 0.95f);
        vertices.addVertex(matrix, (float) d.x, (float) d.y, (float) d.z).setColor(red, green, blue, 0.95f);
    }

    private static void addBiomeBorders(Matrix4fc matrix, VertexConsumer vertices,
                                        List<WorldScopeBiomeCell> cells,
                                        float red, float green, float blue) {
        Set<Long> occupied = new HashSet<>();
        for (WorldScopeBiomeCell cell : cells) {
            occupied.add(gridKey(cell.minX(), cell.minZ()));
        }

        // Gold perimeter segments make the sampled biome transition visible through terrain.
        for (WorldScopeBiomeCell cell : cells) {
            float x0 = cell.minX();
            float z0 = cell.minZ();
            float x1 = cell.maxX();
            float z1 = cell.maxZ();
            // The scanner now traces the biome's vertical extent at each
            // horizontal sample, so this is a real column boundary rather than
            // a nearly-flat line around one sampled Y coordinate.
            float y0 = cell.minY();
            float y1 = cell.maxY();
            float thickness = 0.10f;
            float alpha = 0.98f;

            int cellWidth = Math.max(1, cell.maxX() - cell.minX());
            int cellDepth = Math.max(1, cell.maxZ() - cell.minZ());
            if (!occupied.contains(gridKey(cell.minX() - cellWidth, cell.minZ()))) {
                addCuboid(matrix, vertices, x0 - thickness, y0, z0, x0 + thickness, y1, z1,
                        red, green, blue, alpha);
            }
            if (!occupied.contains(gridKey(cell.minX() + cellWidth, cell.minZ()))) {
                addCuboid(matrix, vertices, x1 - thickness, y0, z0, x1 + thickness, y1, z1,
                        red, green, blue, alpha);
            }
            if (!occupied.contains(gridKey(cell.minX(), cell.minZ() - cellDepth))) {
                addCuboid(matrix, vertices, x0, y0, z0 - thickness, x1, y1, z0 + thickness,
                        red, green, blue, alpha);
            }
            if (!occupied.contains(gridKey(cell.minX(), cell.minZ() + cellDepth))) {
                addCuboid(matrix, vertices, x0, y0, z1 - thickness, x1, y1, z1 + thickness,
                        red, green, blue, alpha);
            }
        }
    }

    private static long gridKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static void addFrame(Matrix4fc matrix, VertexConsumer vertices,
                                 float minX, float minY, float minZ,
                                 float maxX, float maxY, float maxZ,
                                 float r, float g, float b, float alpha) {
        float t = 0.13f;
        float a = alpha;

        // Bottom rectangle.
        addCuboid(matrix, vertices, minX, minY, minZ, maxX, minY + t, minZ + t, r, g, b, a);
        addCuboid(matrix, vertices, minX, minY, maxZ - t, maxX, minY + t, maxZ, r, g, b, a);
        addCuboid(matrix, vertices, minX, minY, minZ, minX + t, minY + t, maxZ, r, g, b, a);
        addCuboid(matrix, vertices, maxX - t, minY, minZ, maxX, minY + t, maxZ, r, g, b, a);

        // Top rectangle.
        addCuboid(matrix, vertices, minX, maxY - t, minZ, maxX, maxY, minZ + t, r, g, b, a);
        addCuboid(matrix, vertices, minX, maxY - t, maxZ - t, maxX, maxY, maxZ, r, g, b, a);
        addCuboid(matrix, vertices, minX, maxY - t, minZ, minX + t, maxY, maxZ, r, g, b, a);
        addCuboid(matrix, vertices, maxX - t, maxY - t, minZ, maxX, maxY, maxZ, r, g, b, a);

        // Four vertical corners.
        addCuboid(matrix, vertices, minX, minY, minZ, minX + t, maxY, minZ + t, r, g, b, a);
        addCuboid(matrix, vertices, maxX - t, minY, minZ, maxX, maxY, minZ + t, r, g, b, a);
        addCuboid(matrix, vertices, minX, minY, maxZ - t, minX + t, maxY, maxZ, r, g, b, a);
        addCuboid(matrix, vertices, maxX - t, minY, maxZ - t, maxX, maxY, maxZ, r, g, b, a);
    }

    private static void addCuboid(Matrix4fc matrix, VertexConsumer buffer,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  float red, float green, float blue, float alpha) {
        // Front face.
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        // Back face.
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        // Left face.
        buffer.addVertex(matrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
        // Right face.
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        // Top face.
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
        // Bottom face.
        buffer.addVertex(matrix, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
    }

    private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX
        );

        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTextureView colorTexture = target.getColorTextureView();
        if (colorTexture == null) return;

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "WorldScope target ESP",
                        colorTexture,
                        Optional.empty(),
                        target.getDepthTextureView(),
                        OptionalDouble.empty()
                )) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    private record BoxState(
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            List<WorldScopeBox> structureBoxes,
            List<WorldScopeBiomeCell> biomeCells,
            int shapeDetail,
            int colorRgb,
            boolean espEnabled,
            boolean tracerEnabled
    ) {
    }
}
