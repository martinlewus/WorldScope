package dev.worldscope;

public record WorldScopeResult(
        String type,
        String id,
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerY() {
        return (minY + maxY) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }
}
