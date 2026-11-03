package dev.worldscope;

public record WorldScopeBiomeCell(
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int minY,
        int maxY
) {
}
