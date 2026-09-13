package dev.conner.hometown.client;

import dev.conner.hometown.network.SubmitTownColorsPayload;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

/** One-time color configuration for an existing/migrated Hometown. */
public final class TownColorSelectionScreen extends Screen {
    private final UUID settlementId;
    private final String townName;
    private final InteractionHand hand;
    private DyeColor primary = DyeColor.BLUE;
    private DyeColor secondary = DyeColor.WHITE;
    private Button primaryValue;
    private Button secondaryValue;
    private Button save;
    private boolean submitted;

    public TownColorSelectionScreen(UUID settlementId, String townName, InteractionHand hand) {
        super(Component.literal("Choose Town Colors"));
        this.settlementId = settlementId;
        this.townName = townName;
        this.hand = hand;
    }

    @Override
    protected void init() {
        int x = width / 2 - 130;
        int y = height / 2 - 34;

        addRenderableWidget(Button.builder(Component.literal("<"), b -> cyclePrimary(-1))
                .bounds(x + 82, y, 24, 20).build());
        primaryValue = addRenderableWidget(Button.builder(TownColorUi.label(primary), b -> cyclePrimary(1))
                .bounds(x + 110, y, 122, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cyclePrimary(1))
                .bounds(x + 236, y, 24, 20).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleSecondary(-1))
                .bounds(x + 82, y + 28, 24, 20).build());
        secondaryValue = addRenderableWidget(Button.builder(TownColorUi.label(secondary), b -> cycleSecondary(1))
                .bounds(x + 110, y + 28, 122, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleSecondary(1))
                .bounds(x + 236, y + 28, 24, 20).build());

        save = addRenderableWidget(Button.builder(Component.literal("Save Colors"), b -> submit())
                .bounds(x, y + 66, 126, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + 134, y + 66, 126, 20).build());
        updateValidity();
    }

    private void cyclePrimary(int delta) {
        primary = TownColorUi.step(primary, delta);
        primaryValue.setMessage(TownColorUi.label(primary));
        updateValidity();
    }

    private void cycleSecondary(int delta) {
        secondary = TownColorUi.step(secondary, delta);
        secondaryValue.setMessage(TownColorUi.label(secondary));
        updateValidity();
    }

    private void updateValidity() {
        if (save != null) save.active = !submitted && primary != secondary;
    }

    private void submit() {
        if (submitted || save == null || !save.active) return;
        submitted = true;
        save.active = false;
        PacketDistributor.sendToServer(new SubmitTownColorsPayload(settlementId, hand, primary, secondary));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 130;
        int y = height / 2 - 34;
        graphics.drawCenteredString(font, title, width / 2, y - 44, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(townName), width / 2, y - 30, 0xD0D0D0);
        graphics.drawString(font, Component.literal("Primary"), x, y + 6, 0xFFFFFF);
        graphics.drawString(font, Component.literal("Secondary"), x, y + 34, 0xFFFFFF);
        drawSwatch(graphics, x + 62, y + 4, primary);
        drawSwatch(graphics, x + 62, y + 32, secondary);
        if (primary == secondary) {
            graphics.drawCenteredString(font, Component.literal("Choose two different colors."), width / 2, y + 54, 0xFF8888);
        }
    }

    private static void drawSwatch(GuiGraphics graphics, int x, int y, DyeColor color) {
        graphics.fill(x - 1, y - 1, x + 15, y + 15, 0xFF000000);
        graphics.fill(x, y, x + 14, y + 14, 0xFF000000 | TownColorUi.rgb(color));
    }

    @Override public boolean isPauseScreen() { return false; }
}
