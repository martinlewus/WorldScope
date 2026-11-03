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

    private EditBox searchBox;
    private EditBox radiusBox;
    private String mode = "structure";
    private String dimension = OVERWORLD;
    private String status = "Select a registry entry, then scan.";
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
        panelWidth = Math.min(760, width - 24);
        panelHeight = Math.min(430, height - 24);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        searchBox = new EditBox(font, panelLeft + 16, panelTop + 78, Math.max(150, panelWidth - 260), 20, Component.literal("Search"));
        searchBox.setHint(Component.literal("Type a name or namespace:id"));
        searchBox.setResponder(value -> scrollOffset = 0);
        addRenderableWidget(searchBox);

        radiusBox = new EditBox(font, panelLeft + panelWidth - 224, panelTop + 78, 92, 20, Component.literal("Radius"));
        radiusBox.setValue("3000");
        addRenderableWidget(radiusBox);

        addRenderableWidget(Button.builder(Component.literal("SCAN"), button -> startScan())
                .bounds(panelLeft + panelWidth - 122, panelTop + 76, 106, 24).build());
        addRenderableWidget(Button.builder(Component.literal("STRUCTURES"), button -> setMode("structure"))
                .bounds(panelLeft + 16, panelTop + 40, 112, 22).build());
        addRenderableWidget(Button.builder(Component.literal("BIOMES"), button -> setMode("biome"))
                .bounds(panelLeft + 134, panelTop + 40, 92, 22).build());

        addRenderableWidget(Button.builder(Component.literal("OVERWORLD"), button -> setDimension(OVERWORLD))
                .bounds(panelLeft + 252, panelTop + 40, 102, 22).build());
        addRenderableWidget(Button.builder(Component.literal("NETHER"), button -> setDimension(NETHER))
                .bounds(panelLeft + 360, panelTop + 40, 82, 22).build());
        addRenderableWidget(Button.builder(Component.literal("END"), button -> setDimension(END))
                .bounds(panelLeft + 448, panelTop + 40, 62, 22).build());

        addRenderableWidget(Button.builder(Component.literal("COORDS: " + (WorldScopeClient.isShowingCoordinates() ? "ON" : "OFF")),
                button -> {
                    WorldScopeClient.toggleCoordinates();
                    clearWidgets();
                    init();
                }).bounds(panelLeft + panelWidth - 192, panelTop + 40, 94, 22).build());

        addRenderableWidget(Button.builder(Component.literal("CANCEL"), button -> WorldScopeScanner.cancel())
                .bounds(panelLeft + panelWidth - 92, panelTop + 40, 76, 22).build());

        addRenderableWidget(Button.builder(Component.literal("ESP: " + (WorldScopeClient.isEspEnabled() ? "ON" : "OFF")),
                button -> {
                    WorldScopeClient.toggleEsp();
                    clearWidgets();
                    init();
                }).bounds(panelLeft + panelWidth / 2 + 12, panelTop + 300, 108, 20).build());

        refreshCatalog();
    }

    private void setMode(String nextMode) {
        if (mode.equals(nextMode)) return;
        mode = nextMode;
        scrollOffset = 0;
        selectedId = "";
        refreshCatalog();
    }

    private void setDimension(String nextDimension) {
        dimension = nextDimension;
        status = "Search dimension: " + dimension;
    }

    private void refreshCatalog() {
        catalog = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        try {
            if (mode.equals("structure")) {
                var registry = client.level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
                registry.listElements().forEach(holder ->
                        holder.unwrapKey().ifPresent(key -> catalog.add(key.identifier().toString())));
            } else {
                var registry = client.level.registryAccess().lookupOrThrow(Registries.BIOME);
                registry.listElements().forEach(holder ->
                        holder.unwrapKey().ifPresent(key -> catalog.add(key.identifier().toString())));
            }
        } catch (Throwable error) {
            status = "Could not read world registry: " + error.getMessage();
        }

        catalog.sort(Comparator.naturalOrder());
        status = catalog.size() + " registered " + (mode.equals("structure") ? "structures" : "biomes") + " available.";
    }

    private List<String> filteredCatalog() {
        String query = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) return catalog;

        List<String> filtered = new ArrayList<>();
        for (String id : catalog) {
            String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
            if (id.toLowerCase(Locale.ROOT).contains(query) || path.contains(query)) filtered.add(id);
        }
        return filtered;
    }

    private void startScan() {
        String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> matches = filteredCatalog();
        String target = selectedId;

        if (!query.isEmpty() && !catalog.contains(query)) {
            if (!matches.isEmpty()) target = matches.get(0);
        } else if (!query.isEmpty()) {
            target = query.contains(":") ? query : "minecraft:" + query;
        }

        if (target == null || target.isBlank()) {
            status = "Choose a structure or biome from the list first.";
            return;
        }

        int radius;
        try {
            radius = Integer.parseInt(radiusBox.getValue());
        } catch (NumberFormatException exception) {
            radius = 3000;
        }
        radius = Math.max(128, Math.min(10_000, radius));
        radiusBox.setValue(Integer.toString(radius));
        selectedId = target;
        status = "Starting independent scan for " + target + "...";
        WorldScopeScanner.startScan(mode, target, dimension, radius);
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
        graphics.text(font, Component.literal("SINGLE-PLAYER WORLD SCANNER"), left + 116, top + 10, 0xFFB8B8B8);

        int listLeft = left + 16;
        int listTop = top + 116;
        int listWidth = Math.max(220, (w - 48) / 2);
        int listBottom = top + h - 52;
        int listHeight = listBottom - listTop;

        graphics.fill(listLeft, listTop, listLeft + listWidth, listBottom, 0xFF555555);
        graphics.fill(listLeft + 1, listTop + 1, listLeft + listWidth - 1, listBottom - 1, 0xFF171717);
        graphics.text(font, Component.literal("REGISTRY CATALOG  •  " + catalog.size()), listLeft + 8, listTop - 13, 0xFF404040);

        List<String> visible = filteredCatalog();
        int rowHeight = 19;
        int visibleRows = Math.max(1, listHeight / rowHeight);
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
            graphics.text(font, Component.literal(id), listLeft + 7, y + 5, 0xFFE4E4E4);
        }

        int infoLeft = listLeft + listWidth + 16;
        int infoRight = left + w - 16;
        graphics.fill(infoLeft, listTop, infoRight, listBottom, 0xFF555555);
        graphics.fill(infoLeft + 1, listTop + 1, infoRight - 1, listBottom - 1, 0xFF252525);
        graphics.text(font, Component.literal("SELECTED TARGET"), infoLeft + 10, listTop + 10, 0xFFFFD76A);
        graphics.text(font, Component.literal(selectedId.isEmpty() ? "Nothing selected" : selectedId), infoLeft + 10, listTop + 26, 0xFFFFFFFF);
        graphics.text(font, Component.literal("DIMENSION"), infoLeft + 10, listTop + 52, 0xFFFFD76A);
        graphics.text(font, Component.literal(dimension), infoLeft + 10, listTop + 67, 0xFFFFFFFF);
        graphics.text(font, Component.literal("SCAN LIMIT"), infoLeft + 10, listTop + 92, 0xFFFFD76A);
        graphics.text(font, Component.literal("Maximum radius: 10,000 blocks"), infoLeft + 10, listTop + 107, 0xFFE0E0E0);
        graphics.text(font, Component.literal("Structure scans test placement rules"), infoLeft + 10, listTop + 127, 0xFFBFBFBF);
        graphics.text(font, Component.literal("and inspect generated structure starts."), infoLeft + 10, listTop + 140, 0xFFBFBFBF);
        graphics.text(font, Component.literal("Biome scans sample generator biomes."), infoLeft + 10, listTop + 160, 0xFFBFBFBF);

        String scanStatus = WorldScopeScanner.getProgressText();
        graphics.fill(left + 16, top + h - 43, left + w - 16, top + h - 22, 0xFF333333);
        graphics.text(font, Component.literal(scanStatus), left + 23, top + h - 36, 0xFFFFFFFF);
        graphics.text(font, Component.literal("K: open   •   ESC: close   •   Scroll: browse registry"), left + 16, top + h - 16, 0xFF555555);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listLeft = panelLeft + 16;
        int listTop = panelTop + 116;
        int listWidth = Math.max(220, (panelWidth - 48) / 2);
        int listBottom = panelTop + panelHeight - 52;
        if (mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= listTop && mouseY <= listBottom) {
            int visibleRows = Math.max(1, (listBottom - listTop) / 19);
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
        int listTop = panelTop + 116;
        int listWidth = Math.max(220, (panelWidth - 48) / 2);
        int listBottom = panelTop + panelHeight - 52;
        if (button == 0 && mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= listTop && mouseY <= listBottom) {
            int row = (int) ((mouseY - listTop) / 19);
            int index = scrollOffset + row;
            List<String> visible = filteredCatalog();
            if (index >= 0 && index < visible.size()) {
                selectedId = visible.get(index);
                searchBox.setValue(selectedId);
                searchBox.setFocused(true);
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
