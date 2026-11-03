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

        if (!WorldScopeClient.isEspEnabled() || client.level == null
                || client.getSingleplayerServer() == null || result == null) {
            boxState = null;
            return;
        }

        String currentDimension = client.level.dimension().identifier().toString();
        if (!currentDimension.equals(result.dimension())) {
            boxState = null;
            return;
        }

        List<WorldScopeBiomeCell> biomeCells = "biome".equals(result.type())
                ? WorldScopeScanner.getActiveBiomeCells() : List.of();
        boxState = new BoxState(
                result.minX(), result.minY(), result.minZ(),
                result.maxX() + 1, result.maxY() + 1, result.maxZ() + 1,
                biomeCells
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

        addCuboid(matrices.last().pose(), vertices,
                minX, minY, minZ, maxX, maxY, maxZ,
                0.05f, 0.62f, 0.88f, 0.10f);
        addFrame(matrices.last().pose(), vertices, minX, minY, minZ, maxX, maxY, maxZ);
        if (!state.biomeCells().isEmpty()) {
            addBiomeBorders(matrices.last().pose(), vertices, state.biomeCells());
        }
        matrices.popPose();

        BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = BUFFER.getExecuteInfo(draw);
        if (info != null) draw(Minecraft.getInstance(), info, THROUGH_WALLS);
        BUFFER.endFrame();
    }

    private static void addBiomeBorders(Matrix4fc matrix, VertexConsumer vertices, List<WorldScopeBiomeCell> cells) {
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
            float y0 = cell.y() - 0.18f;
            float y1 = cell.y() + 0.18f;
            float thickness = 0.10f;
            float red = 1.0f, green = 0.72f, blue = 0.12f, alpha = 0.98f;

            if (!occupied.contains(gridKey(cell.minX() - 48, cell.minZ()))) {
                addCuboid(matrix, vertices, x0 - thickness, y0, z0, x0 + thickness, y1, z1,
                        red, green, blue, alpha);
            }
            if (!occupied.contains(gridKey(cell.minX() + 48, cell.minZ()))) {
                addCuboid(matrix, vertices, x1 - thickness, y0, z0, x1 + thickness, y1, z1,
                        red, green, blue, alpha);
            }
            if (!occupied.contains(gridKey(cell.minX(), cell.minZ() - 48))) {
                addCuboid(matrix, vertices, x0, y0, z0 - thickness, x1, y1, z0 + thickness,
                        red, green, blue, alpha);
            }
            if (!occupied.contains(gridKey(cell.minX(), cell.minZ() + 48))) {
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
                                 float maxX, float maxY, float maxZ) {
        float t = 0.13f;
        float r = 0.08f, g = 0.88f, b = 1f, a = 0.98f;

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

    private record BoxState(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, List<WorldScopeBiomeCell> biomeCells) {
    }
}
