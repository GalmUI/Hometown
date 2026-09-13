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
        super(Component.translatable("hometown.administration.title"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(360, Math.max(260, width - 32));
        int x = (width - panelWidth) / 2;
        int top = Math.max(18, height / 2 - 105);
        int buttonWidth = (panelWidth - 12) / 2;

        overviewButton = addRenderableWidget(Button.builder(
                        Component.translatable("hometown.administration.tab.overview"), b -> select(Page.OVERVIEW))
                .bounds(x + 4, top + 36, buttonWidth, 20).build());
        progressionButton = addRenderableWidget(Button.builder(
                        Component.translatable("hometown.administration.tab.progression"), b -> select(Page.PROGRESSION))
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
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(360, Math.max(260, width - 32));
        int panelHeight = Math.min(222, Math.max(196, height - 36));
        int x = (width - panelWidth) / 2;
        int y = Math.max(10, (height - panelHeight) / 2);

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xEE1C1C1C);
        graphics.fill(x, y, x + panelWidth, y + 2, 0xFFB9A16E);
        graphics.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, 0xFF6D5B3A);
        graphics.fill(x, y, x + 2, y + panelHeight, 0xFFB9A16E);
        graphics.fill(x + panelWidth - 2, y, x + panelWidth, y + panelHeight, 0xFF6D5B3A);

        int primary = 0xFF000000 | TownColorUi.rgb(snapshot.primaryColor());
        int secondary = 0xFF000000 | TownColorUi.rgb(snapshot.secondaryColor());
        graphics.fill(x + 2, y + 2, x + panelWidth / 2, y + 7, primary);
        graphics.fill(x + panelWidth / 2, y + 2, x + panelWidth - 2, y + 7, secondary);

        graphics.drawCenteredString(font,
                Component.translatable("hometown.administration.town_title", snapshot.townName()),
                width / 2, y + 15, 0xFFF4E8CC);

        if (page == Page.OVERVIEW) renderOverview(graphics, x, y, panelWidth);
        else renderProgression(graphics, x, y, panelWidth);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderOverview(GuiGraphics graphics, int x, int y, int panelWidth) {
        int left = x + 18;
        int contentY = y + 72;
        graphics.drawString(font, Component.translatable("hometown.administration.hall.heading"),
                left, contentY, 0xFFF4E8CC, false);
        graphics.drawString(font,
                Component.translatable(snapshot.townHallEstablished()
                        ? "hometown.administration.hall.established" : "hometown.administration.hall.not_established"),
                left, contentY + 16, snapshot.townHallEstablished() ? 0xFF9ED184 : 0xFFE08A8A, false);
        graphics.drawString(font,
                Component.translatable(snapshot.townHallActive()
                        ? "hometown.administration.hall.active" : "hometown.administration.hall.inactive"),
                left, contentY + 30, snapshot.townHallActive() ? 0xFF9ED184 : 0xFFE0B16A, false);

        int swatchY = contentY + 58;
        drawSwatch(graphics, left, swatchY, TownColorUi.rgb(snapshot.primaryColor()));
        graphics.drawString(font,
                Component.translatable("hometown.administration.color.primary", TownColorUi.label(snapshot.primaryColor())),
                left + 24, swatchY + 3, 0xFFE4E4E4, false);
        drawSwatch(graphics, left, swatchY + 24, TownColorUi.rgb(snapshot.secondaryColor()));
        graphics.drawString(font,
                Component.translatable("hometown.administration.color.secondary", TownColorUi.label(snapshot.secondaryColor())),
                left + 24, swatchY + 27, 0xFFE4E4E4, false);

        graphics.drawWordWrap(font, Component.translatable("hometown.administration.overview.hint"),
                left, swatchY + 54, panelWidth - 36, 0xFFB8B8B8);
    }

    private void renderProgression(GuiGraphics graphics, int x, int y, int panelWidth) {
        int left = x + 18;
        int contentY = y + 70;
        graphics.drawString(font, Component.translatable("hometown.administration.progression.heading"),
                left, contentY, 0xFFF4E8CC, false);

        progressionRow(graphics, left, contentY + 18, panelWidth - 36,
                "hometown.administration.node.town_hall", snapshot.townHallEstablished(), false);
        progressionRow(graphics, left, contentY + 42, panelWidth - 36,
                "hometown.administration.node.notice_board", snapshot.noticeBoardUnlocked(), true);
        progressionRow(graphics, left, contentY + 66, panelWidth - 36,
                "hometown.administration.node.civic_projects", snapshot.civicProjectsUnlocked(), true);
        progressionRow(graphics, left, contentY + 90, panelWidth - 36,
                "hometown.administration.node.storage", snapshot.storageUnlocked(), true);
        progressionRow(graphics, left, contentY + 114, panelWidth - 36,
                "hometown.administration.node.animal_farms", snapshot.animalFarmsUnlocked(), true);
    }

    private void progressionRow(GuiGraphics graphics, int x, int y, int width,
                                String nameKey, boolean unlocked, boolean pendingImplementation) {
        graphics.fill(x, y, x + width, y + 20, 0x552F2F2F);
        graphics.drawString(font, Component.translatable(nameKey), x + 6, y + 6, 0xFFE6E0D2, false);
        Component status = Component.translatable(unlocked
                ? (pendingImplementation ? "hometown.administration.node.unlocked_pending" : "hometown.administration.node.established")
                : "hometown.administration.node.locked");
        int statusWidth = font.width(status);
        graphics.drawString(font, status, x + width - statusWidth - 6, y + 6,
                unlocked ? 0xFF9ED184 : 0xFFB0B0B0, false);
    }

    private static void drawSwatch(GuiGraphics graphics, int x, int y, int rgb) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF000000 | rgb);
    }

    @Override public boolean isPauseScreen() { return false; }
}
