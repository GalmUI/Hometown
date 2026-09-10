package dev.conner.hometown.client;

import dev.conner.hometown.network.SubmitTownNamePayload;
import dev.conner.hometown.settlement.TownNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class TownNamingScreen extends Screen {
    private final BlockPos bell;
    private final long nonce;
    private EditBox name;
    private Button found;
    private Component error = Component.empty();
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
        int y = height / 2 - 20;
        name = addRenderableWidget(new EditBox(font, x, y, 260, 20, Component.translatable("hometown.screen.name")));
        name.setMaxLength(SubmitTownNamePayload.WIRE_NAME_LIMIT);
        found = addRenderableWidget(Button.builder(Component.translatable("hometown.screen.found"), b -> submit())
                .bounds(x, y + 50, 126, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(x + 134, y + 50, 126, 20).build());
        name.setResponder(value -> updateValidity());
        name.setValue(previous);
        updateValidity();
        setInitialFocus(name);
    }

    private void updateValidity() {
        try {
            TownNames.validate(name.getValue());
            error = Component.empty();
            found.active = !submitted;
        } catch (IllegalArgumentException ex) {
            error = name.getValue().isEmpty() ? Component.empty() : Component.translatable(ex.getMessage());
            found.active = false;
        }
    }

    private void submit() {
        if (!found.active || submitted) return;
        submitted = true;
        found.active = false;
        PacketDistributor.sendToServer(new SubmitTownNamePayload(bell, nonce, name.getValue()));
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
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 64, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("hometown.screen.name"), width / 2 - 130, height / 2 - 34, 0xFFFFFF);
        graphics.drawWordWrap(font, error, width / 2 - 130, height / 2 + 5, 260, 0xFF8888);
    }

    @Override public boolean isPauseScreen() { return false; }
}
