package dev.worldscope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

public final class WorldScopeScreen extends Screen {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";
    private static final int MAX_SEARCH_DISTANCE = 10_000;

    private EditBox searchBox;
    private EditBox radiusBox;
    private String mode = "structure";
    private String dimension = OVERWORLD;
    private String status = "Select a target, then scan.";
    private List<String> catalog = new ArrayList<>();
    private int scrollOffset;
    private String selectedId = "";
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public WorldScopeScreen() {
        super(Component.literal("WorldScope"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(760, Math.max(1, width - 24));
        panelHeight = Math.min(430, Math.max(1, height - 24));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int left = panelLeft;
        int top = panelTop;
        int w = panelWidth;

        // Mode, dimension and target-HUD controls use one non-overlapping row.
        addRenderableWidget(Button.builder(Component.literal("STRUCTURES"), button -> setMode("structure"))
                .bounds(left + 16, top + 38, 112, 22).build());
        addRenderableWidget(Button.builder(Component.literal("BIOMES"), button -> setMode("biome"))
                .bounds(left + 134, top + 38, 92, 22).build());

        addRenderableWidget(Button.builder(Component.literal("OVERWORLD"), button -> setDimension(OVERWORLD))
                .bounds(left + 242, top + 38, 98, 22).build());
        addRenderableWidget(Button.builder(Component.literal("NETHER"), button -> setDimension(NETHER))
                .bounds(left + 346, top + 38, 76, 22).build());
        addRenderableWidget(Button.builder(Component.literal("END"), button -> setDimension(END))
                .bounds(left + 428, top + 38, 54, 22).build());

        addRenderableWidget(Button.builder(Component.literal("TARGET HUD: " + (WorldScopeClient.isShowingCoordinates() ? "ON" : "OFF")),
                button -> {
                    WorldScopeClient.toggleCoordinates();
                    status = WorldScopeClient.isShowingCoordinates()
                            ? "Target HUD enabled. Coordinates appear after a successful scan."
                            : "Target HUD disabled.";
                    rebuildControls();
                }).bounds(left + w - 126, top + 38, 110, 22).build());

        searchBox = new EditBox(font, left + 16, top + 80, Math.max(110, w - 270), 22, Component.literal("Search registry"));
        searchBox.setHint(Component.literal("Search name or ID, e.g. ancient_city, village:snowy"));
        searchBox.setResponder(value -> scrollOffset = 0);
        addRenderableWidget(searchBox);

        radiusBox = new EditBox(font, left + w - 224, top + 80, 92, 22, Component.literal("Maximum distance"));
        radiusBox.setValue("3000");
        radiusBox.setMaxLength(5);
        radiusBox.setFilter(value -> {
            if (value.isEmpty()) return true;
            if (!value.matches("\\d{1,5}")) return false;
            try {
                return Integer.parseInt(value) <= MAX_SEARCH_DISTANCE;
            } catch (NumberFormatException ignored) {
                return false;
            }
        });
        addRenderableWidget(radiusBox);

        addRenderableWidget(Button.builder(Component.literal("SCAN"), button -> startScan())
                .bounds(left + w - 122, top + 78, 106, 26).build());

        refreshCatalog();

        // The right-hand panel has its own compact settings area. These controls
        // remain inside the panel even at the normal minimum GUI height.
        int listTop = top + 118;
        int listBottom = top + panelHeight - 52;
        int infoLeft = left + 16 + Math.max(220, (w - 48) / 2) + 16;
        int infoWidth = left + w - 16 - infoLeft;
        int buttonY = listBottom - 76;

        addRenderableWidget(Button.builder(Component.literal("ESP: " + (WorldScopeClient.isEspEnabled() ? "ON" : "OFF")),
                button -> {
                    WorldScopeClient.toggleEsp();
                    status = "Through-wall target box " + (WorldScopeClient.isEspEnabled() ? "enabled." : "disabled.");
                    rebuildControls();
                }).bounds(infoLeft + 10, buttonY, 78, 20).build());

        addRenderableWidget(Button.builder(Component.literal("ALERTS: " + (WorldScopeClient.areProximityAlertsEnabled() ? "ON" : "OFF")),
                button -> {
                    WorldScopeClient.toggleProximityAlerts();
                    status = "Proximity alerts " + (WorldScopeClient.areProximityAlertsEnabled() ? "enabled." : "disabled.");
                    rebuildControls();
                }).bounds(infoLeft + 94, buttonY, Math.min(92, Math.max(78, infoWidth - 104)), 20).build());

        EditBox alertRadiusBox = new EditBox(font, infoLeft + 10, listBottom - 25, 64, 20, Component.literal("Alert radius"));
        alertRadiusBox.setValue(Integer.toString(WorldScopeClient.getProximityRadius()));
        alertRadiusBox.setMaxLength(3);
        alertRadiusBox.setFilter(value -> value.isEmpty() || value.matches("\\d{1,3}"));
        addRenderableWidget(alertRadiusBox);

        addRenderableWidget(Button.builder(Component.literal("SET ALERT RANGE"), button -> {
            try {
                WorldScopeClient.setProximityRadius(Integer.parseInt(alertRadiusBox.getValue()));
                status = "Alert radius set to " + WorldScopeClient.getProximityRadius() + " blocks.";
            } catch (NumberFormatException exception) {
                WorldScopeClient.setProximityRadius(64);
                status = "Invalid alert radius; reset to 64 blocks.";
            }
            rebuildControls();
        }).bounds(infoLeft + 82, listBottom - 25, Math.min(112, Math.max(88, infoWidth - 92)), 20).build());
    }

    private void rebuildControls() {
        clearWidgets();
        init();
    }

    private void setMode(String nextMode) {
        if (mode.equals(nextMode)) return;
        mode = nextMode;
        scrollOffset = 0;
        selectedId = "";
        if (searchBox != null) searchBox.setValue("");
        refreshCatalog();
        rebuildControls();
    }

    private void setDimension(String nextDimension) {
        dimension = nextDimension;
        status = "Search dimension: " + dimension;
    }

    private void refreshCatalog() {
        catalog = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.getSingleplayerServer() == null) {
            status = "Open a single-player world to read its registries.";
            return;
        }

        try {
            if (mode.equals("structure")) {
                // Structure registries are server-side dynamic registries. Read
                // from the integrated server so vanilla and modded structures
                // (including world-generation datapacks) appear in the catalog.
                var serverRegistry = client.getSingleplayerServer().overworld()
                        .registryAccess().lookupOrThrow(Registries.STRUCTURE);
                serverRegistry.listElements().forEach(holder ->
                        holder.unwrapKey().ifPresent(key -> catalog.add(key.identifier().toString())));
            } else {
                var biomeRegistry = client.level.registryAccess().lookupOrThrow(Registries.BIOME);
                biomeRegistry.listElements().forEach(holder ->
                        holder.unwrapKey().ifPresent(key -> catalog.add(key.identifier().toString())));
            }
        } catch (Throwable error) {
            status = "Could not read " + mode + " registry: " + error.getClass().getSimpleName();
            return;
        }

        catalog.sort(Comparator.naturalOrder());
        status = catalog.size() + " registered " + (mode.equals("structure") ? "structures" : "biomes") + " available.";
        if (catalog.isEmpty()) {
            status = "The " + mode + " registry returned no entries. Check the world or mod registry setup.";
        }
    }

    private String selectedVariantLabel() {
        if (selectedId == null || selectedId.isBlank()) return "No selection";
        String path = selectedId.substring(selectedId.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        if (path.startsWith("village_")) return pretty(path.substring("village_".length())) + " village";
        if (path.endsWith("_village")) return pretty(path.substring(0, path.length() - "_village".length()) + " village");
        if (path.equals("desert_pyramid")) return "Desert temple";
        if (path.equals("jungle_pyramid")) return "Jungle temple";
        return pretty(path);
    }

    private String pretty(String value) {
        String[] words = value.replace('_', ' ').split(" ");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (label.length() > 0) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) label.append(word.substring(1));
        }
        return label.toString();
    }

    private List<String> filteredCatalog() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) return catalog;

        String subtype = "";
        String term = query;
        if (mode.equals("structure") && query.startsWith("village:")) {
            subtype = "village";
            term = query.substring("village:".length());
        } else if (mode.equals("structure") && query.startsWith("temple:")) {
            subtype = "temple";
            term = query.substring("temple:".length());
        }

        List<String> filtered = new ArrayList<>();
        for (String id : catalog) {
            String path = id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT);
            String readable = path.replace('_', ' ');
            if (subtype.equals("village") && !path.contains("village")) continue;
            if (subtype.equals("temple") && !(path.contains("temple") || path.contains("pyramid"))) continue;
            if (term.isEmpty() || id.toLowerCase(Locale.ROOT).contains(term) || readable.contains(term)) {
                filtered.add(id);
            }
        }
        return filtered;
    }

    private void startScan() {
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> matches = filteredCatalog();
        String target = selectedId;

        if (!query.isEmpty()) {
            if (catalog.contains(query)) {
                target = query;
            } else if (!matches.isEmpty()) {
                target = matches.get(0);
            } else {
                target = "";
            }
        }

        if (target == null || target.isBlank() || !catalog.contains(target)) {
            status = "Choose a valid " + mode + " from the registry list before scanning.";
            return;
        }

        int radius;
        try {
            radius = Integer.parseInt(radiusBox.getValue());
        } catch (NumberFormatException exception) {
            radius = 3000;
        }
        radius = Math.max(128, Math.min(MAX_SEARCH_DISTANCE, radius));
        radiusBox.setValue(Integer.toString(radius));

        selectedId = target;
        status = "Starting scan for " + target + "...";
        WorldScopeScanner.startScan(mode, target, dimension, radius);
    }

    private String fitText(String value, int maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);

        int left = panelLeft;
        int top = panelTop;
        int w = panelWidth;
        int h = panelHeight;

        graphics.fill(left - 2, top - 2, left + w + 2, top + h + 2, 0xFF373737);
        graphics.fill(left, top, left + w, top + h, 0xFFC6C6C6);
        graphics.fill(left + 2, top + 2, left + w - 2, top + h - 2, 0xFF3C3C3C);
        graphics.fill(left + 4, top + 4, left + w - 4, top + h - 4, 0xFFC6C6C6);
        graphics.fill(left + 4, top + 4, left + w - 4, top + 25, 0xFF202020);
        graphics.text(font, Component.literal("WORLDSCOPE"), left + 14, top + 10, 0xFFFFFFFF);
        graphics.text(font, Component.literal("SINGLE-PLAYER WORLD SCANNER"), left + 192, top + 10, 0xFFB8B8B8);

        graphics.text(font, Component.literal("MAX SEARCH DISTANCE (BLOCKS)"), left + w - 224, top + 65, 0xFF404040);
        graphics.text(font, Component.literal("128–10,000"), left + w - 224, top + 105, 0xFF555555);

        int listLeft = left + 16;
        int listTop = top + 118;
        int listWidth = Math.max(220, (w - 48) / 2);
        int listBottom = top + h - 52;
        int listHeight = Math.max(1, listBottom - listTop);

        graphics.fill(listLeft, listTop, listLeft + listWidth, listBottom, 0xFF555555);
        graphics.fill(listLeft + 1, listTop + 1, listLeft + listWidth - 1, listBottom - 1, 0xFF171717);
        graphics.text(font, Component.literal("REGISTRY CATALOG  •  " + catalog.size()), listLeft + 8, listTop - 13, 0xFF404040);

        List<String> visible = filteredCatalog();
        int rowHeight = 19;
        int visibleRows = Math.max(1, (listHeight - 4) / rowHeight);
        int maxScroll = Math.max(0, visible.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= visible.size()) break;
            int y = listTop + 2 + row * rowHeight;
            String id = visible.get(index);
            if (id.equals(selectedId)) {
                graphics.fill(listLeft + 2, y, listLeft + listWidth - 2, y + rowHeight - 1, 0xFF385B82);
            } else if ((row & 1) == 0) {
                graphics.fill(listLeft + 2, y, listLeft + listWidth - 2, y + rowHeight - 1, 0xFF222222);
            }
            graphics.text(font, Component.literal(fitText(id, listWidth - 16)), listLeft + 7, y + 5, 0xFFE4E4E4);
        }

        int infoLeft = listLeft + listWidth + 16;
        int infoRight = left + w - 16;
        int infoWidth = infoRight - infoLeft;
        graphics.fill(infoLeft, listTop, infoRight, listBottom, 0xFF555555);
        graphics.fill(infoLeft + 1, listTop + 1, infoRight - 1, listBottom - 1, 0xFF252525);

        graphics.text(font, Component.literal("SELECTED TARGET"), infoLeft + 10, listTop + 10, 0xFFFFD76A);
        graphics.text(font, Component.literal(fitText(selectedId.isEmpty() ? "Nothing selected" : selectedId, infoWidth - 20)),
                infoLeft + 10, listTop + 26, 0xFFFFFFFF);

        graphics.text(font, Component.literal("VARIANT / SUBTYPE"), infoLeft + 10, listTop + 49, 0xFFFFD76A);
        graphics.text(font, Component.literal(fitText(selectedVariantLabel(), infoWidth - 20)), infoLeft + 10, listTop + 64, 0xFFFFFFFF);

        graphics.text(font, Component.literal("DIMENSION"), infoLeft + 10, listTop + 84, 0xFFFFD76A);
        graphics.text(font, Component.literal(dimension), infoLeft + 10, listTop + 99, 0xFFFFFFFF);

        graphics.text(font, Component.literal("MAX SEARCH DISTANCE"), infoLeft + 10, listTop + 119, 0xFFFFD76A);
        graphics.text(font, Component.literal("Up to 10,000 blocks"), infoLeft + 10, listTop + 134, 0xFFE0E0E0);

        if (mode.equals("structure")) {
            graphics.text(font, Component.literal("LOOT INFORMATION"), infoLeft + 10, listTop + 154, 0xFFFFD76A);
            graphics.text(font, Component.literal("Loot is not guessed from a structure ID."), infoLeft + 10, listTop + 169, 0xFFBFBFBF);
        } else {
            graphics.text(font, Component.literal("BIOME BORDER"), infoLeft + 10, listTop + 154, 0xFFFFD76A);
            graphics.text(font, Component.literal("Gold outline = sampled biome edge."), infoLeft + 10, listTop + 169, 0xFFBFBFBF);
        }

        String scanStatus = WorldScopeScanner.getProgressText();
        if (!WorldScopeScanner.isRunning() && "Ready.".equals(scanStatus)) {
            scanStatus = status;
        }
        graphics.fill(left + 16, top + h - 43, left + w - 16, top + h - 22, 0xFF333333);
        graphics.text(font, Component.literal(fitText(scanStatus, w - 46)), left + 23, top + h - 36, 0xFFFFFFFF);
        graphics.text(font, Component.literal("K: open  •  ESC: close  •  Scroll: browse registry"), left + 16, top + h - 16, 0xFF555555);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listLeft = panelLeft + 16;
        int listTop = panelTop + 118;
        int listWidth = Math.max(220, (panelWidth - 48) / 2);
        int listBottom = panelTop + panelHeight - 52;
        if (mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= listTop && mouseY <= listBottom) {
            int visibleRows = Math.max(1, (listBottom - listTop - 4) / 19);
            int maxScroll = Math.max(0, filteredCatalog().size() - visibleRows);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(verticalAmount) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int listLeft = panelLeft + 16;
        int listTop = panelTop + 118;
        int listWidth = Math.max(220, (panelWidth - 48) / 2);
        int listBottom = panelTop + panelHeight - 52;
        if (button == 0 && mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= listTop && mouseY <= listBottom) {
            int row = (int) ((mouseY - listTop - 2) / 19);
            int index = scrollOffset + row;
            List<String> visible = filteredCatalog();
            if (row >= 0 && index >= 0 && index < visible.size()) {
                selectedId = visible.get(index);
                searchBox.setValue(selectedId);
                searchBox.setFocused(true);
                status = "Selected " + selectedId + ". Press SCAN to search.";
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
