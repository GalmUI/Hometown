package dev.conner.hometown.client;

import dev.conner.hometown.civic.FacilityType;
import dev.conner.hometown.civic.LivestockPolicy;
import dev.conner.hometown.civic.LivestockSpecies;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload.Line;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload.LineKind;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload.Tone;
import dev.conner.hometown.network.HometownNetworking;
import dev.conner.hometown.network.UpdateAnimalFarmPolicyPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only building detail plus server-authoritative controls for facilities that expose policy. */
public final class FacilityDetailScreen extends Screen {
    private final FacilityDetailSnapshotPayload snapshot;
    private int scrollOffset;

    public FacilityDetailScreen(FacilityDetailSnapshotPayload snapshot) {
        super(Component.literal("Facility Detail"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        if (snapshot.animalFarmControls() == null) return;
        var controls = snapshot.animalFarmControls();
        int x = panelX();
        int y = panelY();
        int panelHeight = panelHeight();
        int cowY = y + panelHeight - 47;
        int pigY = cowY + 22;
        addPolicyButtons(cowY, LivestockSpecies.COW,
                controls.cowBreedingPairs(), controls.cowCullAbove());
        addPolicyButtons(pigY, LivestockSpecies.PIG,
                controls.pigBreedingPairs(), controls.pigCullAbove());
    }

    private void addPolicyButtons(int y, LivestockSpecies species, int pairs, int cullAbove) {
        int x = panelX();
        Button pairMinus = addRenderableWidget(Button.builder(Component.literal("-"),
                b -> adjust(species, UpdateAnimalFarmPolicyPayload.Setting.BREEDING_PAIRS, -1))
                .bounds(x + 103, y, 18, 18).build());
        Button pairPlus = addRenderableWidget(Button.builder(Component.literal("+"),
                b -> adjust(species, UpdateAnimalFarmPolicyPayload.Setting.BREEDING_PAIRS, 1))
                .bounds(x + 123, y, 18, 18).build());
        Button cullMinus = addRenderableWidget(Button.builder(Component.literal("-"),
                b -> adjust(species, UpdateAnimalFarmPolicyPayload.Setting.CULL_ABOVE, -1))
                .bounds(x + 210, y, 18, 18).build());
        Button cullPlus = addRenderableWidget(Button.builder(Component.literal("+"),
                b -> adjust(species, UpdateAnimalFarmPolicyPayload.Setting.CULL_ABOVE, 1))
                .bounds(x + 230, y, 18, 18).build());
        pairMinus.active = pairs > 1;
        pairPlus.active = pairs < LivestockPolicy.MAX_BREEDING_PAIRS;
        cullMinus.active = cullAbove > pairs * 2;
        cullPlus.active = cullAbove < LivestockPolicy.MAX_CULL_ABOVE;
    }

    private void adjust(LivestockSpecies species, UpdateAnimalFarmPolicyPayload.Setting setting, int delta) {
        HometownNetworking.updateAnimalFarmPolicy(snapshot.settlementId(), species, setting, delta);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Own background; avoid the vanilla blur pass used by 1.21.1 screens.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int x = panelX();
        int y = panelY();

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xF61C1C1C);
        graphics.fill(x, y, x + panelWidth, y + 2, 0xFFB9A16E);
        graphics.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, 0xFF6D5B3A);
        graphics.fill(x, y, x + 2, y + panelHeight, 0xFFB9A16E);
        graphics.fill(x + panelWidth - 2, y, x + panelWidth, y + panelHeight, 0xFF6D5B3A);

        int primary = 0xFF000000 | TownColorUi.rgb(snapshot.primaryColor());
        int secondary = 0xFF000000 | TownColorUi.rgb(snapshot.secondaryColor());
        graphics.fill(x + 2, y + 2, x + panelWidth / 2, y + 7, primary);
        graphics.fill(x + panelWidth / 2, y + 2, x + panelWidth - 2, y + 7, secondary);

        String title = snapshot.townName() + " — " + facilityName(snapshot.facilityType());
        graphics.drawCenteredString(font, Component.literal(title), width / 2, y + 15, 0xFFF4E8CC);
        graphics.drawCenteredString(font, Component.literal(snapshot.active() ? "ACTIVE" : "UNAVAILABLE"),
                width / 2, y + 29, snapshot.active() ? 0xFF9ED184 : 0xFFE0B16A);

        int left = x + 16;
        int right = x + panelWidth - 22;
        int contentTop = y + 49;
        int contentBottom = y + panelHeight - 14 - controlReserve();
        int viewportHeight = Math.max(1, contentBottom - contentTop);
        int contentHeight = contentHeight(snapshot.lines());
        int maxScroll = scrollLimit(contentHeight, viewportHeight);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);

        graphics.enableScissor(left, contentTop, x + panelWidth - 10, contentBottom);
        int cursor = contentTop - scrollOffset;
        for (Line line : snapshot.lines()) {
            int needed = lineHeight(line);
            if (line.kind() == LineKind.SECTION) {
                graphics.drawString(font, Component.literal(line.label()), left, cursor + 3, 0xFFF4E8CC, false);
                graphics.fill(left, cursor + 14, right, cursor + 15, 0x443F3A31);
            } else if (line.kind() == LineKind.ROW) {
                graphics.fill(left, cursor, right, cursor + 18, 0x552F2F2F);
                graphics.drawString(font, Component.literal(line.label()), left + 6, cursor + 5, 0xFFE6E0D2, false);
                String value = fit(line.value(), Math.max(70, (right - left) / 2 - 12));
                int valueWidth = font.width(value);
                graphics.drawString(font, Component.literal(value), right - valueWidth - 6, cursor + 5, color(line.tone()), false);
            } else {
                graphics.drawWordWrap(font, Component.literal(line.value()), left + 6, cursor + 2,
                        right - left - 12, color(line.tone()));
            }
            cursor += needed;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = x + panelWidth - 8;
            graphics.fill(trackX, contentTop, trackX + 2, contentBottom, 0x553F3A31);
            int thumbHeight = Math.max(12, viewportHeight * viewportHeight / Math.max(viewportHeight, contentHeight));
            int travel = Math.max(0, viewportHeight - thumbHeight);
            int thumbY = contentTop + (maxScroll == 0 ? 0 : travel * scrollOffset / maxScroll);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFB9A16E);
            String hint = scrollOffset < maxScroll ? "Scroll for more" : "Scroll up for earlier details";
            graphics.drawString(font, Component.literal(hint), left, contentBottom + 2, 0xFF9F9F9F, false);
        }

        if (snapshot.animalFarmControls() != null) drawAnimalFarmControls(graphics, x, y, panelHeight);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawAnimalFarmControls(GuiGraphics graphics, int x, int y, int panelHeight) {
        var controls = snapshot.animalFarmControls();
        int top = y + panelHeight - 55;
        graphics.fill(x + 12, top, x + panelWidth() - 12, top + 1, 0x443F3A31);
        drawControlLabels(graphics, x, y + panelHeight - 47, "Cows",
                controls.cowBreedingPairs(), controls.cowCullAbove());
        drawControlLabels(graphics, x, y + panelHeight - 25, "Pigs",
                controls.pigBreedingPairs(), controls.pigCullAbove());
    }

    private void drawControlLabels(GuiGraphics graphics, int x, int y, String species, int pairs, int cullAbove) {
        graphics.drawString(font, Component.literal(species), x + 16, y + 5, 0xFFE6E0D2, false);
        graphics.drawString(font, Component.literal("Pairs " + pairs), x + 52, y + 5, 0xFFD8D8D8, false);
        graphics.drawString(font, Component.literal("Cull " + cullAbove), x + 155, y + 5, 0xFFD8D8D8, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewportHeight = Math.max(1, panelHeight() - 63 - controlReserve());
        int maxScroll = scrollLimit(contentHeight(snapshot.lines()), viewportHeight);
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int next = scrollOffset - (int)Math.round(scrollY * 24.0);
        scrollOffset = Math.clamp(next, 0, maxScroll);
        return true;
    }

    static int lineHeight(Line line) {
        return line.kind() == LineKind.NOTE ? 28 : line.kind() == LineKind.SECTION ? 18 : 20;
    }

    static int contentHeight(java.util.List<Line> lines) {
        int height = 0;
        for (Line line : lines) height += lineHeight(line);
        return height;
    }

    static int scrollLimit(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - Math.max(0, viewportHeight));
    }

    private int panelWidth() { return Math.min(420, Math.max(280, width - 32)); }
    private int panelHeight() { return Math.min(310, Math.max(220, height - 28)); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return Math.max(8, (height - panelHeight()) / 2); }
    private int controlReserve() { return snapshot.animalFarmControls() == null ? 0 : 58; }

    private String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private static String facilityName(FacilityType type) {
        return switch (type) {
            case TOWN_HALL -> "Town Hall";
            case STORAGE -> "Town Storage";
            case ANIMAL_FARM -> "Animal Farm";
        };
    }

    private static int color(Tone tone) {
        return switch (tone) {
            case GOOD -> 0xFF9ED184;
            case WARNING -> 0xFFE0B16A;
            case MUTED -> 0xFF9F9F9F;
            case NORMAL -> 0xFFD8D8D8;
        };
    }

    @Override public boolean isPauseScreen() { return false; }
}
