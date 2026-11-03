package dev.worldscope;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WorldScopeScreen extends Screen {
    private static final Pattern COORDS = Pattern.compile("\\[(-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\]");
    private static WorldScopeScreen active;
    private EditBox searchBox;
    private String mode = "structure";
    private String status = "Search your world's registered structures or biomes.";
    private final List<String> results = new ArrayList<>();

    private static final String[][] STRUCTURES = {
        {"Village", "village_plains"}, {"Shipwreck", "shipwreck"},
        {"Ancient City", "ancient_city"}, {"Trial Chambers", "trial_chambers"},
        {"Ocean Monument", "ocean_monument"}, {"Ocean Ruins", "ocean_ruin_cold"},
        {"Mansion", "mansion"}, {"Pillager Outpost", "pillager_outpost"},
        {"Desert Pyramid", "desert_pyramid"}, {"Jungle Temple", "jungle_pyramid"},
        {"Stronghold", "stronghold"}, {"Mineshaft", "mineshaft"},
        {"Trail Ruins", "trail_ruins"}, {"Nether Fortress", "fortress"},
        {"Bastion Remnant", "bastion_remnant"}, {"End City", "end_city"},
        {"Ruined Portal", "ruined_portal"}
    };
    private static final String[][] BIOMES = {
        {"Deep Dark", "deep_dark"}, {"Lush Caves", "lush_caves"},
        {"Dripstone Caves", "dripstone_caves"}, {"Mushroom Fields", "mushroom_fields"},
        {"Cherry Grove", "cherry_grove"}, {"Pale Garden", "pale_garden"},
        {"Badlands", "badlands"}, {"Mangrove Swamp", "mangrove_swamp"},
        {"Ice Spikes", "ice_spikes"}, {"Any biome ID", ""}
    };

    public WorldScopeScreen() {
        super(Component.literal("WorldScope"));
        active = this;
    }

    @Override
    protected void init() {
        int panelW = Math.min(520, width - 30);
        int left = (width - panelW) / 2;
        int top = Math.max(20, (height - 350) / 2);
        searchBox = new EditBox(font, left + 18, top + 56, panelW - 150, 22, Component.literal("Search ID"));
        searchBox.setHint(Component.literal("e.g. village, shipwreck, modid:structure"));
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("FIND"), b -> search())
            .bounds(left + panelW - 120, top + 55, 102, 24).build());
        addRenderableWidget(Button.builder(Component.literal("STRUCTURES"), b -> {
            mode = "structure"; status = "Structure search selected."; clearAndRebuild();
        }).bounds(left + 18, top + 91, 145, 22).build());
        addRenderableWidget(Button.builder(Component.literal("BIOMES"), b -> {
            mode = "biome"; status = "Biome search selected."; clearAndRebuild();
        }).bounds(left + 170, top + 91, 120, 22).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), b -> onClose())
            .bounds(left + panelW - 105, top + 10, 87, 22).build());
        addPresetButtons(left, top, panelW);
    }

    private void clearAndRebuild() {
        clearWidgets();
        init();
    }

    private void addPresetButtons(int left, int top, int panelW) {
        int listTop = top + 126;
        int buttonW = (panelW - 48) / 2;
        String[][] presets = mode.equals("biome") ? BIOMES : STRUCTURES;
        for (int i = 0; i < presets.length; i++) {
            final String[] preset = presets[i];
            int col = i % 2, row = i / 2;
            addRenderableWidget(Button.builder(Component.literal(preset[0]), b -> {
                if (preset[1].isEmpty()) { searchBox.setValue(""); searchBox.setFocused(true); }
                else { searchBox.setValue(preset[1]); search(); }
            }).bounds(left + 18 + col * (buttonW + 12), listTop + row * 27, buttonW, 22).build());
        }
    }

    private void search() {
        String value = searchBox.getValue().trim();
        if (value.isEmpty()) { status = "Enter a structure or biome ID first."; return; }
        WorldScopeClient.locate(mode, value);
    }

    public static void setStatus(String text) { if (active != null) active.status = text; }

    public static void receiveGameMessage(String message) {
        if (active == null) return;
        String lower = message.toLowerCase();
        if (!(lower.contains("locat") || lower.contains("found") || lower.contains("couldn't") ||
              lower.contains("cannot") || lower.contains("no structure") || lower.contains("no biome"))) return;
        Matcher matcher = COORDS.matcher(message);
        if (matcher.find()) {
            active.status = "LOCATION FOUND  •  X " + matcher.group(1) + "   Y " + matcher.group(2) + "   Z " + matcher.group(3);
            active.results.add(0, message);
            if (active.results.size() > 5) active.results.remove(active.results.size() - 1);
        } else active.status = message;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99050910);
        int panelW = Math.min(520, width - 30), panelH = 350;
        int left = (width - panelW) / 2, top = Math.max(20, (height - panelH) / 2);
        graphics.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, 0xFF26364A);
        graphics.fill(left, top, left + panelW, top + panelH, 0xF00D1420);
        graphics.fill(left, top, left + panelW, top + 3, 0xFF54B7D8);
        graphics.text(font, Component.literal("WORLDSCOPE"), left + 18, top + 15, 0xFFFFFFFF);
        graphics.text(font, Component.literal("STRUCTURE & BIOME LOCATOR"), left + 18, top + 31, 0xFF8DA9BD);
        graphics.text(font, Component.literal("SEARCH"), left + 18, top + 45, 0xFF70CBEA);
        graphics.fill(left + 18, top + 119, left + panelW - 18, top + 120, 0xFF314255);
        if (!results.isEmpty()) {
            graphics.text(font, Component.literal("LATEST RESULT"), left + 18, top + 274, 0xFF70CBEA);
            String latest = results.get(0);
            if (latest.length() > 72) latest = latest.substring(0, 69) + "...";
            graphics.text(font, Component.literal(latest), left + 18, top + 288, 0xFFCFD9E2);
        }
        graphics.text(font, Component.literal("STATUS"), left + 18, top + 302, 0xFF70CBEA);
        graphics.text(font, Component.literal(status), left + 18, top + 316, 0xFFE0E8EF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { active = null; super.onClose(); }
}
