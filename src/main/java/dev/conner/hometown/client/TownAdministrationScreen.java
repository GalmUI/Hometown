package dev.conner.hometown.client;

import dev.conner.hometown.network.TownAdministrationSnapshotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** R3 M1 physical Town Hall Administration UI. Navigation is client-local over one server snapshot. */
public final class TownAdministrationScreen extends Screen {
    private enum Page { OVERVIEW, PROGRESSION }

    private final TownAdministrationSnapshotPayload snapshot;
    private Page page = Page.OVERVIEW;
    private Button overviewButton;
    private Button progressionButton;

    public TownAdministrationScreen(TownAdministrationSnapshotPayload snapshot) {
        super(Component.literal("Town Administration"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, Math.max(260, width - 32));
        int x = (width - panelWidth) / 2;
        int top = Math.max(18, height / 2 - 105);
        int buttonWidth = (panelWidth - 12) / 2;

        overviewButton = addRenderableWidget(Button.builder(Component.literal("Overview"), b -> select(Page.OVERVIEW))
                .bounds(x + 4, top + 36, buttonWidth, 20).build());
        progressionButton = addRenderableWidget(Button.builder(Component.literal("Progression"), b -> select(Page.PROGRESSION))
                .bounds(x + 8 + buttonWidth, top + 36, buttonWidth, 20).build());
        updateButtons();
    }

    private void select(Page next) {
        page = next;
        updateButtons();
    }

    private void updateButtons() {
        if (overviewButton != null) overviewButton.active = page != Page.OVERVIEW;
        if (progressionButton != null) progressionButton.active = page != Page.PROGRESSION;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible but readable behind Administration. Do not call Screen#renderBackground here:
        // the 1.21.1 blur/background pass can soften the custom panel and text on some render paths.
        graphics.fill(0, 0, width, height, 0xA0000000);

        int panelWidth = Math.min(360, Math.max(260, width - 32));
        int panelHeight = Math.min(222, Math.max(196, height - 36));
        int x = (width - panelWidth) / 2;
        int y = Math.max(10, (height - panelHeight) / 2);

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xF61C1C1C);
        graphics.fill(x, y, x + panelWidth, y + 2, 0xFFB9A16E);
        graphics.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, 0xFF6D5B3A);
        graphics.fill(x, y, x + 2, y + panelHeight, 0xFFB9A16E);
        graphics.fill(x + panelWidth - 2, y, x + panelWidth, y + panelHeight, 0xFF6D5B3A);

        int primary = 0xFF000000 | TownColorUi.rgb(snapshot.primaryColor());
        int secondary = 0xFF000000 | TownColorUi.rgb(snapshot.secondaryColor());
        graphics.fill(x + 2, y + 2, x + panelWidth / 2, y + 7, primary);
        graphics.fill(x + panelWidth / 2, y + 2, x + panelWidth - 2, y + 7, secondary);

        graphics.drawCenteredString(font, Component.literal("Town Administration — " + snapshot.townName()),
                width / 2, y + 15, 0xFFF4E8CC);

        if (page == Page.OVERVIEW) renderOverview(graphics, x, y, panelWidth);
        else renderProgression(graphics, x, y, panelWidth);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderOverview(GuiGraphics graphics, int x, int y, int panelWidth) {
        int left = x + 18;
        int contentY = y + 72;
        graphics.drawString(font, Component.literal("Town Hall"), left, contentY, 0xFFF4E8CC, false);
        graphics.drawString(font, Component.literal(snapshot.townHallEstablished() ? "Established" : "Not established"),
                left, contentY + 16, snapshot.townHallEstablished() ? 0xFF9ED184 : 0xFFE08A8A, false);
        graphics.drawString(font, Component.literal(snapshot.townHallActive() ? "Active" : "Unavailable"),
                left, contentY + 30, snapshot.townHallActive() ? 0xFF9ED184 : 0xFFE0B16A, false);

        int swatchY = contentY + 58;
        drawSwatch(graphics, left, swatchY, TownColorUi.rgb(snapshot.primaryColor()));
        graphics.drawString(font, Component.literal("Primary: ").append(TownColorUi.label(snapshot.primaryColor())),
                left + 24, swatchY + 3, 0xFFE4E4E4, false);
        drawSwatch(graphics, left, swatchY + 24, TownColorUi.rgb(snapshot.secondaryColor()));
        graphics.drawString(font, Component.literal("Secondary: ").append(TownColorUi.label(snapshot.secondaryColor())),
                left + 24, swatchY + 27, 0xFFE4E4E4, false);

        graphics.drawWordWrap(font, Component.literal("Administration is available here while the registered Town Hall remains valid."),
                left, swatchY + 54, panelWidth - 36, 0xFFB8B8B8);
    }

    private void renderProgression(GuiGraphics graphics, int x, int y, int panelWidth) {
        int left = x + 18;
        int contentY = y + 70;
        graphics.drawString(font, Component.literal("Civic Progression"), left, contentY, 0xFFF4E8CC, false);

        progressionRow(graphics, left, contentY + 18, panelWidth - 36,
                "Town Hall", snapshot.townHallEstablished(), false);
        progressionRow(graphics, left, contentY + 42, panelWidth - 36,
                "Notice Board", snapshot.noticeBoardUnlocked(), true);
        progressionRow(graphics, left, contentY + 66, panelWidth - 36,
                "Civic Projects", snapshot.civicProjectsUnlocked(), true);
        progressionRow(graphics, left, contentY + 90, panelWidth - 36,
                "Storage", snapshot.storageUnlocked(), true);
        progressionRow(graphics, left, contentY + 114, panelWidth - 36,
                "Animal Farms", snapshot.animalFarmsUnlocked(), true);
    }

    private void progressionRow(GuiGraphics graphics, int x, int y, int width,
                                String name, boolean unlocked, boolean pendingImplementation) {
        graphics.fill(x, y, x + width, y + 20, 0x552F2F2F);
        graphics.drawString(font, Component.literal(name), x + 6, y + 6, 0xFFE6E0D2, false);
        String statusText = unlocked
                ? (pendingImplementation ? "Unlocked — not yet implemented" : "Established")
                : "Locked";
        Component status = Component.literal(statusText);
        int statusWidth = font.width(status);
        int available = Math.max(60, width / 2);
        if (statusWidth > available) {
            status = Component.literal(font.plainSubstrByWidth(statusText, Math.max(20, available - font.width("…"))) + "…");
            statusWidth = font.width(status);
        }
        graphics.drawString(font, status, x + width - statusWidth - 6, y + 6,
                unlocked ? 0xFF9ED184 : 0xFFB0B0B0, false);
    }

    private static void drawSwatch(GuiGraphics graphics, int x, int y, int rgb) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF000000 | rgb);
    }

    @Override public boolean isPauseScreen() { return false; }
}
