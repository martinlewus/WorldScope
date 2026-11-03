package dev.worldscope;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;

public final class WorldScopeUserSettings {
    private static final int DEFAULT_RADIUS = 3000;
    private static final int MIN_RADIUS = 128;
    private static final int MAX_RADIUS = 10_000_000;
    private static final Properties VALUES = new Properties();
    private static boolean loaded;

    private WorldScopeUserSettings() {
    }

    private static Path filePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("worldscope")
                .resolve("settings.properties");
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path file = filePath();
        if (!Files.isRegularFile(file)) return;
        try (InputStream input = Files.newInputStream(file)) {
            VALUES.load(input);
        } catch (IOException ignored) {
            // Settings are optional; retain safe defaults if the file is unreadable.
        }
    }

    private static synchronized void save() {
        Path file = filePath();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                VALUES.store(output, "WorldScope user preferences");
            }
        } catch (IOException ignored) {
            // A settings write failure must not interrupt scanning.
        }
    }

    public static synchronized int getSearchRadius() {
        load();
        try {
            int radius = Integer.parseInt(VALUES.getProperty("searchRadius", Integer.toString(DEFAULT_RADIUS)));
            return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        } catch (NumberFormatException ignored) {
            return DEFAULT_RADIUS;
        }
    }

    public static synchronized void setSearchRadius(int radius) {
        load();
        int safeRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        VALUES.setProperty("searchRadius", Integer.toString(safeRadius));
        save();
    }

    public static synchronized String getSelectedTarget(String mode) {
        load();
        return VALUES.getProperty("selectedTarget." + mode, "");
    }

    public static synchronized void setSelectedTarget(String mode, String id) {
        load();
        if (id == null || id.isBlank()) {
            VALUES.remove("selectedTarget." + mode);
        } else {
            VALUES.setProperty("selectedTarget." + mode, id);
        }
        save();
    }
}
