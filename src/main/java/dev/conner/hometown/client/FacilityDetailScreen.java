package dev.conner.hometown.client;

import dev.conner.hometown.civic.FacilityType;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload.Line;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload.LineKind;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload.Tone;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only building-level view opened from a registered Hometown facility sign. */
public final class FacilityDetailScreen extends Screen {
    private final FacilityDetailSnapshotPayload snapshot;

    public FacilityDetailScreen(FacilityDetailSnapshotPayload snapshot) {
        super(Component.literal("Facility Detail"));
        this.snapshot = snapshot;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Own background; avoid the vanilla blur pass used by 1.21.1 screens.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
        int panelWidth = Math.min(420, Math.max(280, width - 32));
        int panelHeight = Math.min(310, Math.max(220, height - 28));
        int x = (width - panelWidth) / 2;
        int y = Math.max(8, (height - panelHeight) / 2);

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
        int right = x + panelWidth - 16;
        int cursor = y + 49;
        int bottom = y + panelHeight - 14;
        boolean clipped = false;
        for (Line line : snapshot.lines()) {
            int needed = line.kind() == LineKind.NOTE ? 28 : line.kind() == LineKind.SECTION ? 18 : 20;
            if (cursor + needed > bottom) { clipped = true; break; }
            if (line.kind() == LineKind.SECTION) {
                graphics.drawString(font, Component.literal(line.label()), left, cursor + 3, 0xFFF4E8CC, false);
                graphics.fill(left, cursor + 14, right, cursor + 15, 0x443F3A31);
                cursor += 18;
            } else if (line.kind() == LineKind.ROW) {
                graphics.fill(left, cursor, right, cursor + 18, 0x552F2F2F);
                graphics.drawString(font, Component.literal(line.label()), left + 6, cursor + 5, 0xFFE6E0D2, false);
                String value = fit(line.value(), Math.max(70, (right - left) / 2 - 12));
                int valueWidth = font.width(value);
                graphics.drawString(font, Component.literal(value), right - valueWidth - 6, cursor + 5, color(line.tone()), false);
                cursor += 20;
            } else {
                graphics.drawWordWrap(font, Component.literal(line.value()), left + 6, cursor + 2,
                        right - left - 12, color(line.tone()));
                cursor += 28;
            }
        }
        if (clipped) graphics.drawString(font, Component.literal("Additional detail omitted from this first facility view."),
                left, bottom - 9, 0xFF9F9F9F, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

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
