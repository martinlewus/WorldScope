package dev.worldscope;

public record WorldScopeBox(
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
) {
    public long volume() {
        return (long) Math.max(1, maxX - minX + 1)
                * Math.max(1, maxY - minY + 1)
                * Math.max(1, maxZ - minZ + 1);
    }
}
