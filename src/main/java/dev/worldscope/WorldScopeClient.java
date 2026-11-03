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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class WorldScopeClient implements ClientModInitializer {
    private static KeyMapping openKey;
    private static boolean showCoordinates = true;
    private static boolean espEnabled = true;

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
        if (!showCoordinates || client.player == null || result == null) return;

        String line = result.id() + "  [" + result.minX() + ", " + result.minY() + ", " + result.minZ() + "]";
        graphics.fill(7, 7, 310, 31, 0xA9000000);
        graphics.text(client.font, Component.literal("WORLDSCOPE  " + result.dimension()), 13, 11, 0xFF80D8FF);
        graphics.text(client.font, Component.literal(line), 13, 21, 0xFFFFFFFF);
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
