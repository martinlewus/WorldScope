package dev.worldscope;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

import net.minecraft.client.Minecraft;

public final class WorldScopeCache {
    private static final String CACHE_VERSION = "2";

    private WorldScopeCache() {
    }

    private static Path filePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("worldscope")
                .resolve("locations.properties");
    }

    private static String propertyKey(long seed, String dimension, String type, String id) {
        String raw = CACHE_VERSION + "|" + seed + "|" + dimension + "|" + type + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static synchronized WorldScopeResult load(long seed, String dimension, String type, String id) {
        Path file = filePath();
        if (!Files.isRegularFile(file)) return null;

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            return null;
        }

        String value = properties.getProperty(propertyKey(seed, dimension, type, id));
        if (value == null) return null;

        String[] parts = value.split(",");
        if (parts.length != 6) return null;

        try {
            return new WorldScopeResult(
                    type, id, dimension,
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5])
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static synchronized void save(long seed, WorldScopeResult result) {
        Path file = filePath();
        Properties properties = new Properties();

        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) {
                try (InputStream input = Files.newInputStream(file)) {
                    properties.load(input);
                }
            }

            String value = result.minX() + "," + result.minY() + "," + result.minZ()
                    + "," + result.maxX() + "," + result.maxY() + "," + result.maxZ();
            properties.setProperty(
                    propertyKey(seed, result.dimension(), result.type(), result.id()),
                    value
            );

            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "WorldScope positive scan cache; keyed by world seed, dimension and registry ID");
            }
        } catch (IOException ignored) {
            // A cache write failure must never interrupt a world or prevent a scan result.
        }
    }
}
