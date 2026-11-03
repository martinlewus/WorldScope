package dev.worldscope;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.InputConstants;

public final class WorldScopeClient implements ClientModInitializer {
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                ResourceLocation.fromNamespaceAndPath("worldscope", "main")
        );
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldscope.open", InputConstants.Type.KEYSYM, InputConstants.KEY_K, category
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player != null && client.level != null) {
                    client.setScreen(new WorldScopeScreen());
                }
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                WorldScopeScreen.receiveGameMessage(message.getString()));
    }

    static void locate(String type, String id) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        String cleanId = id.trim().toLowerCase();
        if (cleanId.isEmpty()) return;
        if (!cleanId.contains(":")) cleanId = "minecraft:" + cleanId;
        if (!cleanId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            WorldScopeScreen.setStatus("Invalid ID. Use namespace:path");
            return;
        }
        WorldScopeScreen.setStatus("Searching for " + cleanId + "...");
        client.player.connection.sendCommand("locate " + type + " " + cleanId);
    }
}
