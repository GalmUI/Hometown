package dev.conner.hometown.client;

import dev.conner.hometown.network.SubmitTownNamePayload;
import dev.conner.hometown.settlement.TownNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class TownNamingScreen extends Screen {
    private final BlockPos bell;
    private final long nonce;
    private EditBox name;
    private Button primaryValue;
    private Button secondaryValue;
    private Button found;
    private Component error = Component.empty();
    private DyeColor primary = DyeColor.BLUE;
    private DyeColor secondary = DyeColor.WHITE;
    private boolean submitted;

    public TownNamingScreen(BlockPos bell, long nonce) {
        super(Component.translatable("hometown.screen.title"));
        this.bell = bell;
        this.nonce = nonce;
    }

    @Override
    protected void init() {
        String previous = name == null ? "" : name.getValue();
        int x = width / 2 - 130;
        int y = height / 2 - 42;
        name = addRenderableWidget(new EditBox(font, x, y - 24, 260, 20, Component.translatable("hometown.screen.name")));
        name.setMaxLength(SubmitTownNamePayload.WIRE_NAME_LIMIT);

        addRenderableWidget(Button.builder(Component.literal("<"), b -> cyclePrimary(-1))
                .bounds(x + 82, y + 8, 24, 20).build());
        primaryValue = addRenderableWidget(Button.builder(TownColorUi.label(primary), b -> cyclePrimary(1))
                .bounds(x + 110, y + 8, 122, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cyclePrimary(1))
                .bounds(x + 236, y + 8, 24, 20).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleSecondary(-1))
                .bounds(x + 82, y + 36, 24, 20).build());
        secondaryValue = addRenderableWidget(Button.builder(TownColorUi.label(secondary), b -> cycleSecondary(1))
                .bounds(x + 110, y + 36, 122, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleSecondary(1))
                .bounds(x + 236, y + 36, 24, 20).build());

        found = addRenderableWidget(Button.builder(Component.translatable("hometown.screen.found"), b -> submit())
                .bounds(x, y + 76, 126, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + 134, y + 76, 126, 20).build());
        name.setResponder(value -> updateValidity());
        name.setValue(previous);
        updateValidity();
        setInitialFocus(name);
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
        boolean nameValid;
        try {
            TownNames.validate(name.getValue());
            error = Component.empty();
            nameValid = true;
        } catch (IllegalArgumentException ex) {
            error = name.getValue().isEmpty() ? Component.empty() : Component.translatable(ex.getMessage());
            nameValid = false;
        }
        if (nameValid && primary == secondary) error = Component.literal("Choose two different town colors.");
        found.active = nameValid && primary != secondary && !submitted;
    }

    private void submit() {
        if (!found.active || submitted) return;
        submitted = true;
        found.active = false;
        PacketDistributor.sendToServer(new SubmitTownNamePayload(bell, nonce, name.getValue(), primary, secondary));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && found.active) {
            submit();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 130;
        int y = height / 2 - 42;
        graphics.drawCenteredString(font, title, width / 2, y - 60, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("hometown.screen.name"), x, y - 38, 0xFFFFFF);
        graphics.drawString(font, Component.literal("Primary"), x, y + 14, 0xFFFFFF);
        graphics.drawString(font, Component.literal("Secondary"), x, y + 42, 0xFFFFFF);
        drawSwatch(graphics, x + 62, y + 12, primary);
        drawSwatch(graphics, x + 62, y + 40, secondary);
        graphics.drawWordWrap(font, error, x, y + 60, 260, 0xFF8888);
    }

    private static void drawSwatch(GuiGraphics graphics, int x, int y, DyeColor color) {
        graphics.fill(x - 1, y - 1, x + 15, y + 15, 0xFF000000);
        graphics.fill(x, y, x + 14, y + 14, 0xFF000000 | TownColorUi.rgb(color));
    }

    @Override public boolean isPauseScreen() { return false; }
}
