package dev.worldscope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
    private static final int MAX_SEARCH_DISTANCE = 10_000_000;

    private EditBox searchBox;
    private EditBox radiusBox;
    private String mode = "structure";
    private String dimension = OVERWORLD;
    private String status = "Select a target, then scan.";
    private List<String> catalog = new ArrayList<>();
    private final List<DimensionButtonLayout> dimensionButtons = new ArrayList<>();
    private int scrollOffset;
    private String selectedId = "";
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listBottom;
    private int infoLeft;
    private int infoRight;
    private boolean compactUi;

    public WorldScopeScreen() {
        super(Component.literal("WorldScope"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(760, Math.max(1, width - 16));
        panelHeight = Math.min(430, Math.max(1, height - 16));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        compactUi = panelWidth < 680 || panelHeight < 380;

        int left = panelLeft;
        int top = panelTop;
        int w = panelWidth;
        dimensionButtons.clear();

        if (compactUi) {
            int modeY = top + 31;
            int modeWidth = Math.max(60, (w - 36) / 2);
            addRenderableWidget(Button.builder(Component.literal("STRUCTURES"), button -> setMode("structure"))
                    .bounds(left + 12, modeY, modeWidth, 20).build());
            addRenderableWidget(Button.builder(Component.literal("BIOMES"), button -> setMode("biome"))
                    .bounds(left + 20 + modeWidth, modeY, modeWidth, 20).build());

            int dimY = top + 55;
            int x = left + 12;
            dimensionButtons.add(new DimensionButtonLayout(x, dimY, 88, 20, "OVERWORLD", OVERWORLD));
            x += 92;
            dimensionButtons.add(new DimensionButtonLayout(x, dimY, 68, 20, "NETHER", NETHER));
            x += 72;
            dimensionButtons.add(new DimensionButtonLayout(x, dimY, 42, 20, "END", END));

            int hudWidth = 76;
            addRenderableWidget(Button.builder(Component.literal("HUD: " + (WorldScopeClient.isShowingCoordinates() ? "ON" : "OFF")),
                    button -> {
                        WorldScopeClient.toggleCoordinates();
                        status = WorldScopeClient.isShowingCoordinates() ? "Target HUD enabled." : "Target HUD disabled.";
                        rebuildControls();
                    }).bounds(left + w - 12 - hudWidth, dimY, hudWidth, 20).build());

            int searchY = top + 84;
            int scanWidth = 76;
            int radiusWidth = 68;
            int radiusX = left + w - 12 - scanWidth - 6 - radiusWidth;
            searchBox = new EditBox(font, left + 12, searchY, Math.max(90, radiusX - left - 20), 20,
                    Component.literal("Search registry"));
            searchBox.setHint(Component.literal("Search ID or name..."));
            searchBox.setResponder(value -> scrollOffset = 0);
            addRenderableWidget(searchBox);

            radiusBox = createRadiusBox(radiusX, searchY, radiusWidth, 20);
            addRenderableWidget(radiusBox);
            addRenderableWidget(Button.builder(Component.literal("SCAN"), button -> startScan())
                    .bounds(left + w - 12 - scanWidth, searchY, scanWidth, 20).build());

            listLeft = left + 12;
            listTop = top + 126;
            listBottom = top + panelHeight - 32;
            listWidth = Math.max(130, (w - 42) * 48 / 100);
        } else {
            int modeY = top + 36;
            addRenderableWidget(Button.builder(Component.literal("STRUCTURES"), button -> setMode("structure"))
                    .bounds(left + 16, modeY, 112, 22).build());
            addRenderableWidget(Button.builder(Component.literal("BIOMES"), button -> setMode("biome"))
                    .bounds(left + 134, modeY, 92, 22).build());

            int dimY = modeY;
            dimensionButtons.add(new DimensionButtonLayout(left + 242, dimY, 98, 22, "OVERWORLD", OVERWORLD));
            dimensionButtons.add(new DimensionButtonLayout(left + 346, dimY, 76, 22, "NETHER", NETHER));
            dimensionButtons.add(new DimensionButtonLayout(left + 428, dimY, 54, 22, "END", END));

            addRenderableWidget(Button.builder(Component.literal("TARGET HUD: " + (WorldScopeClient.isShowingCoordinates() ? "ON" : "OFF")),
                    button -> {
                        WorldScopeClient.toggleCoordinates();
                        status = WorldScopeClient.isShowingCoordinates()
                                ? "Target HUD enabled. Coordinates appear after a successful scan."
                                : "Target HUD disabled.";
                        rebuildControls();
                    }).bounds(left + w - 126, modeY, 110, 22).build());

            int searchY = top + 80;
            searchBox = new EditBox(font, left + 16, searchY, Math.max(110, w - 270), 22,
                    Component.literal("Search registry"));
            searchBox.setHint(Component.literal("Search name or ID, e.g. ancient_city, village:snowy"));
            searchBox.setResponder(value -> scrollOffset = 0);
            addRenderableWidget(searchBox);

            int radiusX = left + w - 224;
            radiusBox = createRadiusBox(radiusX, searchY, 92, 22);
            addRenderableWidget(radiusBox);

            addRenderableWidget(Button.builder(Component.literal("SCAN"), button -> startScan())
                    .bounds(left + w - 122, searchY - 2, 106, 26).build());

            listLeft = left + 16;
            listTop = top + 118;
            listBottom = top + panelHeight - 52;
            listWidth = Math.max(220, (w - 48) / 2);
        }

        infoLeft = listLeft + listWidth + (compactUi ? 10 : 16);
        infoRight = left + w - (compactUi ? 12 : 16);

        refreshCatalog();
        restoreSavedTarget();
        addResultNavigationControls();
        createInfoControls();
    }

    private EditBox createRadiusBox(int x, int y, int boxWidth, int boxHeight) {
        EditBox box = new EditBox(font, x, y, boxWidth, boxHeight, Component.literal("Maximum search distance"));
        box.setValue(Integer.toString(WorldScopeUserSettings.getSearchRadius()));
        box.setMaxLength(8);
        box.setResponder(value -> {
            String digits = value.replaceAll("[^0-9]", "");
            if (digits.length() > 8) digits = digits.substring(0, 8);
            try {
                if (!digits.isEmpty() && Integer.parseInt(digits) > MAX_SEARCH_DISTANCE) {
                    digits = Integer.toString(MAX_SEARCH_DISTANCE);
                }
            } catch (NumberFormatException ignored) {
                digits = "3000";
            }
            if (!digits.equals(value)) box.setValue(digits);
            try {
                int parsed = Integer.parseInt(digits);
                if (parsed >= 128) WorldScopeUserSettings.setSearchRadius(parsed);
            } catch (NumberFormatException ignored) {
                // An incomplete edit is saved once it becomes a valid radius.
            }
        });
        return box;
    }

    private void addResultNavigationControls() {
        int arrowY = listTop + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> WorldScopeScanner.previousResult())
                .bounds(infoRight - 48, arrowY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> WorldScopeScanner.nextResult())
                .bounds(infoRight - 24, arrowY, 20, 18).build());
    }

    private void createInfoControls() {
        int infoWidth = Math.max(1, infoRight - infoLeft);
        if (compactUi) {
            addRenderableWidget(new ShapeDetailSlider(
                    infoLeft + 8, listBottom - 64, Math.max(70, infoWidth - 16), 16,
                    WorldScopeScanner.getShapeDetail()
            ));

            int toggleY = listBottom - 42;
            addRenderableWidget(Button.builder(Component.literal("ESP " + (WorldScopeClient.isEspEnabled() ? "ON" : "OFF")),
                    button -> {
                        WorldScopeClient.toggleEsp();
                        status = "Through-wall target box " + (WorldScopeClient.isEspEnabled() ? "enabled." : "disabled.");
                        rebuildControls();
                    }).bounds(infoLeft + 8, toggleY, 62, 16).build());

            addRenderableWidget(Button.builder(Component.literal("ALERTS " + (WorldScopeClient.areProximityAlertsEnabled() ? "ON" : "OFF")),
                    button -> {
                        WorldScopeClient.toggleProximityAlerts();
                        status = "Proximity alerts " + (WorldScopeClient.areProximityAlertsEnabled() ? "enabled." : "disabled.");
                        rebuildControls();
                    }).bounds(infoLeft + 74, toggleY, Math.min(76, Math.max(58, infoWidth - 82)), 16).build());

            EditBox alertRadiusBox = createAlertRadiusBox(infoLeft + 8, listBottom - 20, 46, 16);
            addRenderableWidget(alertRadiusBox);
            addRenderableWidget(Button.builder(Component.literal("SET"), button -> setAlertRange(alertRadiusBox))
                    .bounds(infoLeft + 112, listBottom - 20, Math.min(42, Math.max(32, infoWidth - 120)), 16).build());
        } else {
            addRenderableWidget(new ShapeDetailSlider(
                    infoLeft + 10, listBottom - 90, Math.max(90, infoWidth - 20), 20,
                    WorldScopeScanner.getShapeDetail()
            ));

            int toggleY = listBottom - 58;
            addRenderableWidget(Button.builder(Component.literal("ESP: " + (WorldScopeClient.isEspEnabled() ? "ON" : "OFF")),
                    button -> {
                        WorldScopeClient.toggleEsp();
                        status = "Through-wall target box " + (WorldScopeClient.isEspEnabled() ? "enabled." : "disabled.");
                        rebuildControls();
                    }).bounds(infoLeft + 10, toggleY, 78, 20).build());

            addRenderableWidget(Button.builder(Component.literal("ALERTS: " + (WorldScopeClient.areProximityAlertsEnabled() ? "ON" : "OFF")),
                    button -> {
                        WorldScopeClient.toggleProximityAlerts();
                        status = "Proximity alerts " + (WorldScopeClient.areProximityAlertsEnabled() ? "enabled." : "disabled.");
                        rebuildControls();
                    }).bounds(infoLeft + 94, toggleY, 92, 20).build());

            EditBox alertRadiusBox = createAlertRadiusBox(infoLeft + 10, listBottom - 27, 64, 20);
            addRenderableWidget(alertRadiusBox);
            addRenderableWidget(Button.builder(Component.literal("SET ALERT RANGE"), button -> setAlertRange(alertRadiusBox))
                    .bounds(infoLeft + 82, listBottom - 27, Math.min(112, Math.max(88, infoWidth - 92)), 20).build());
        }
    }

    private EditBox createAlertRadiusBox(int x, int y, int boxWidth, int boxHeight) {
        EditBox box = new EditBox(font, x, y, boxWidth, boxHeight, Component.literal("Alert radius"));
        box.setValue(Integer.toString(WorldScopeClient.getProximityRadius()));
        box.setMaxLength(3);
        box.setResponder(value -> {
            String digits = value.replaceAll("[^0-9]", "");
            if (digits.length() > 3) digits = digits.substring(0, 3);
            try {
                if (!digits.isEmpty() && Integer.parseInt(digits) > 256) digits = "256";
            } catch (NumberFormatException ignored) {
                digits = "64";
            }
            if (!digits.equals(value)) box.setValue(digits);
        });
        return box;
    }

    private void setAlertRange(EditBox alertRadiusBox) {
        try {
            WorldScopeClient.setProximityRadius(Integer.parseInt(alertRadiusBox.getValue()));
            status = "Alert radius set to " + WorldScopeClient.getProximityRadius() + " blocks.";
        } catch (NumberFormatException exception) {
            WorldScopeClient.setProximityRadius(64);
            status = "Invalid alert radius; reset to 64 blocks.";
        }
        rebuildControls();
    }

    private void rebuildControls() {
        String previousSearch = searchBox == null ? "" : searchBox.getValue();
        String previousRadius = radiusBox == null ? "3000" : radiusBox.getValue();
        String previousStatus = status;
        clearWidgets();
        init();
        if (searchBox != null) searchBox.setValue(previousSearch);
        if (radiusBox != null) radiusBox.setValue(previousRadius);
        status = previousStatus;
    }

    private void restoreSavedTarget() {
        String remembered = WorldScopeUserSettings.getSelectedTarget(mode);
        if (remembered != null && !remembered.isBlank() && catalog.contains(remembered)) {
            selectedId = remembered;
            if (searchBox != null) searchBox.setValue(remembered);
        } else if (selectedId != null && !selectedId.isBlank() && !catalog.contains(selectedId)) {
            selectedId = "";
        }
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
        WorldScopeUserSettings.setSearchRadius(radius);
        WorldScopeUserSettings.setSelectedTarget(mode, target);

        selectedId = target;
        status = "Starting scan for " + target + "...";
        WorldScopeScanner.startScan(mode, target, dimension, radius);
    }

    private String resultCounterText() {
        int count = WorldScopeScanner.getResultCount();
        int selected = WorldScopeScanner.getSelectedResultNumber();
        return count == 0 ? "NO RESULTS YET" : "RESULT " + selected + " / " + count;
    }

    private String fitText(String value, int maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    private void drawDimensionButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (DimensionButtonLayout button : dimensionButtons) {
            boolean selected = dimension.equals(button.dimensionId());
            boolean hovered = mouseX >= button.x() && mouseX <= button.x() + button.width()
                    && mouseY >= button.y() && mouseY <= button.y() + button.height();
            int outer = selected ? 0xFF70551A : 0xFF373737;
            int fill = selected ? 0xFFFFD34E : (hovered ? 0xFF686868 : 0xFF555555);
            int textColor = selected ? 0xFF28200C : 0xFFFFFFFF;
            graphics.fill(button.x() - 1, button.y() - 1,
                    button.x() + button.width() + 1, button.y() + button.height() + 1, outer);
            graphics.fill(button.x(), button.y(), button.x() + button.width(),
                    button.y() + button.height(), fill);
            int textX = button.x() + Math.max(2, (button.width() - font.width(button.label())) / 2);
            graphics.text(font, Component.literal(button.label()), textX, button.y() + 6, textColor);
        }
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
        graphics.text(font, Component.literal("WORLDSCOPE"), left + 12, top + 10, 0xFFFFFFFF);
        graphics.text(font, Component.literal(fitText("SINGLE-PLAYER WORLD SCANNER", w - 170)),
                left + 154, top + 10, 0xFFB8B8B8);

        drawDimensionButtons(graphics, mouseX, mouseY);

        if (compactUi) {
            int radiusX = left + w - 12 - 76 - 6 - 68;
            graphics.text(font, Component.literal("128–10,000,000 BLOCKS"), radiusX, top + 108, 0xFF555555);
        } else {
            graphics.text(font, Component.literal("MAX DISTANCE (BLOCKS)"),
                    left + w - 224, top + 65, 0xFF404040);
            graphics.text(font, Component.literal("128 to 10,000,000"),
                    left + w - 224, top + 105, 0xFF555555);
        }

        int listHeight = Math.max(1, listBottom - listTop);
        graphics.fill(listLeft, listTop, listLeft + listWidth, listBottom, 0xFF555555);
        graphics.fill(listLeft + 1, listTop + 1, listLeft + listWidth - 1, listBottom - 1, 0xFF171717);
        graphics.text(font, Component.literal("REGISTRY CATALOG • " + catalog.size()),
                listLeft + 8, listTop - 13, 0xFF404040);

        List<String> visible = filteredCatalog();
        int rowHeight = compactUi ? 17 : 19;
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
            graphics.text(font, Component.literal(fitText(id, listWidth - 16)),
                    listLeft + 7, y + (compactUi ? 4 : 5), 0xFFE4E4E4);
        }

        graphics.fill(infoLeft, listTop, infoRight, listBottom, 0xFF555555);
        graphics.fill(infoLeft + 1, listTop + 1, infoRight - 1, listBottom - 1, 0xFF252525);
        int infoWidth = infoRight - infoLeft;

        if (compactUi) {
            String compactTarget = (selectedId.isEmpty() ? "Nothing selected" : selectedId)
                    + "  •  " + resultCounterText();
            graphics.text(font, Component.literal(fitText(compactTarget, infoWidth - 16)),
                    infoLeft + 8, listTop + 8, 0xFFFFFFFF);
        } else {
            graphics.text(font, Component.literal("SELECTED TARGET"), infoLeft + 10, listTop + 10, 0xFFFFD76A);
            graphics.text(font, Component.literal(fitText(selectedId.isEmpty() ? "Nothing selected" : selectedId, infoWidth - 20)),
                    infoLeft + 10, listTop + 26, 0xFFFFFFFF);
            graphics.text(font, Component.literal(resultCounterText()), infoLeft + 10, listTop + 42, 0xFFB8B8B8);

            graphics.text(font, Component.literal("DIMENSION"), infoLeft + 10, listTop + 66, 0xFFFFD76A);
            graphics.text(font, Component.literal(dimension), infoLeft + 10, listTop + 81, 0xFFFFFFFF);

            graphics.text(font, Component.literal("SEARCH RANGE"), infoLeft + 10, listTop + 103, 0xFFFFD76A);
            graphics.text(font, Component.literal("128 to 10,000,000 blocks"), infoLeft + 10, listTop + 118, 0xFFE0E0E0);

            if (mode.equals("structure")) {
                graphics.text(font, Component.literal("STRUCTURE SHAPE"), infoLeft + 10, listTop + 141, 0xFFFFD76A);
                graphics.text(font, Component.literal("Piece bounds approximate the layout."),
                        infoLeft + 10, listTop + 156, 0xFFBFBFBF);
            } else {
                graphics.text(font, Component.literal("BIOME BORDER"), infoLeft + 10, listTop + 141, 0xFFFFD76A);
                graphics.text(font, Component.literal("Higher detail samples a finer edge."),
                        infoLeft + 10, listTop + 156, 0xFFBFBFBF);
            }
        }

        String scanStatus = WorldScopeScanner.getProgressText();
        if (!WorldScopeScanner.isRunning() && "Ready.".equals(scanStatus)) scanStatus = status;
        graphics.fill(left + 12, top + h - 27, left + w - 12, top + h - 8, 0xFF333333);
        graphics.text(font, Component.literal(fitText(scanStatus, w - 38)),
                left + 19, top + h - 21, 0xFFFFFFFF);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listLeft && mouseX <= listLeft + listWidth
                && mouseY >= listTop && mouseY <= listBottom) {
            int rowHeight = compactUi ? 17 : 19;
            int visibleRows = Math.max(1, (listBottom - listTop - 4) / rowHeight);
            int maxScroll = Math.max(0, filteredCatalog().size() - visibleRows);
            scrollOffset = Math.max(0, Math.min(maxScroll,
                    scrollOffset - (int) Math.signum(verticalAmount) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0) {
            for (DimensionButtonLayout dimensionButton : dimensionButtons) {
                if (dimensionButton.contains(mouseX, mouseY)) {
                    setDimension(dimensionButton.dimensionId());
                    return true;
                }
            }

            if (mouseX >= listLeft && mouseX <= listLeft + listWidth
                    && mouseY >= listTop && mouseY <= listBottom) {
                int rowHeight = compactUi ? 17 : 19;
                int row = (int) ((mouseY - listTop - 2) / rowHeight);
                int index = scrollOffset + row;
                List<String> visible = filteredCatalog();
                if (row >= 0 && index >= 0 && index < visible.size()) {
                    selectedId = visible.get(index);
                    WorldScopeUserSettings.setSelectedTarget(mode, selectedId);
                    searchBox.setValue(selectedId);
                    searchBox.setFocused(true);
                    status = "Selected " + selectedId + ". Press SCAN to search.";
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private final class ShapeDetailSlider extends AbstractSliderButton {
        private ShapeDetailSlider(int x, int y, int width, int height, int initialDetail) {
            super(x, y, width, height,
                    Component.literal("SHAPE DETAIL: " + initialDetail + "/8"),
                    (Math.max(1, Math.min(8, initialDetail)) - 1) / 7.0);
            updateMessage();
        }

        private int detail() {
            return 1 + (int) Math.round(value * 7.0);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("SHAPE DETAIL: " + detail() + "/8"));
        }

        @Override
        protected void applyValue() {
            WorldScopeScanner.setShapeDetail(detail());
            WorldScopeResult active = WorldScopeScanner.getActiveResult();
            if (active != null && "biome".equals(active.type())) {
                status = "Shape detail changed. Run the biome scan again to rebuild its edge at this resolution.";
            } else {
                status = "Structure shape detail set to " + detail() + "/8.";
            }
        }
    }

    private record DimensionButtonLayout(int x, int y, int width, int height, String label, String dimensionId) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
