package dev.worldscope;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class WorldScopeClient implements ClientModInitializer {
    private static KeyMapping openKey;
    private static boolean showCoordinates = true;
    private static boolean espEnabled = true;
    private static boolean proximityAlertsEnabled = true;
    private static int proximityRadius = 64;
    private static boolean wasNearTarget = false;
    private static String lastAlertKey = "";

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("worldscope", "main")
        );
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldscope.open", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player != null && client.level != null && client.getSingleplayerServer() != null) {
                    client.gui.setScreen(new WorldScopeScreen());
                }
            }
            updateProximityAlert(client);
        });

        WorldScopeWorldRenderer.initialize();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("worldscope", "target_coordinates"),
                WorldScopeClient::renderTargetHud
        );
    }

    private static void renderTargetHud(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        WorldScopeResult result = WorldScopeScanner.getActiveResult();
        if (!showCoordinates || client.player == null || client.level == null
                || client.getSingleplayerServer() == null || result == null) return;

        String line = result.id() + "  X " + result.centerX() + "  Y " + result.centerY() + "  Z " + result.centerZ();
        graphics.fill(7, 7, 310, 31, 0xA9000000);
        graphics.text(client.font, Component.literal("WORLDSCOPE  " + result.dimension()), 13, 11, 0xFF80D8FF);
        graphics.text(client.font, Component.literal(line), 13, 21, 0xFFFFFFFF);
    }

    private static void updateProximityAlert(Minecraft client) {
        WorldScopeResult result = WorldScopeScanner.getActiveResult();
        if (!proximityAlertsEnabled || result == null || client.player == null
                || client.level == null || client.getSingleplayerServer() == null) {
            wasNearTarget = false;
            lastAlertKey = "";
            return;
        }

        String currentDimension = client.level.dimension().identifier().toString();
        if (!currentDimension.equals(result.dimension())) {
            wasNearTarget = false;
            return;
        }

        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();

        boolean near;
        if ("biome".equals(result.type())) {
            near = x >= result.minX() && x <= result.maxX() + 1
                    && z >= result.minZ() && z <= result.maxZ() + 1;
        } else {
            double dx = Math.max(Math.max(result.minX() - x, 0.0), x - (result.maxX() + 1));
            double dy = Math.max(Math.max(result.minY() - y, 0.0), y - (result.maxY() + 1));
            double dz = Math.max(Math.max(result.minZ() - z, 0.0), z - (result.maxZ() + 1));
            near = dx * dx + dy * dy + dz * dz <= (double) proximityRadius * proximityRadius;
        }

        String key = result.type() + "|" + result.dimension() + "|" + result.id()
                + "|" + result.minX() + "|" + result.minZ();
        if (near && (!wasNearTarget || !key.equals(lastAlertKey))) {
            client.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.15F)
            );
        }
        wasNearTarget = near;
        lastAlertKey = key;
    }

    public static boolean areProximityAlertsEnabled() {
        return proximityAlertsEnabled;
    }

    public static void toggleProximityAlerts() {
        proximityAlertsEnabled = !proximityAlertsEnabled;
        wasNearTarget = false;
    }

    public static int getProximityRadius() {
        return proximityRadius;
    }

    public static void setProximityRadius(int radius) {
        proximityRadius = Math.max(8, Math.min(256, radius));
        wasNearTarget = false;
    }

    public static boolean isEspEnabled() {
        return espEnabled;
    }

    public static void toggleEsp() {
        espEnabled = !espEnabled;
    }

    public static boolean isShowingCoordinates() {
        return showCoordinates;
    }

    public static void toggleCoordinates() {
        showCoordinates = !showCoordinates;
    }

    public static void setCoordinatesVisible(boolean visible) {
        showCoordinates = visible;
    }
}
