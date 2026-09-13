package dev.conner.hometown.interaction;

import dev.conner.hometown.civic.TownHallService;
import dev.conner.hometown.item.HometownItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Deliberate R3 registration gesture: present a linked Town Ledger to a vanilla civic sign. */
public final class TownHallInteractionHandler {
    private TownHallInteractionHandler() {}

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(HometownItems.TOWN_LEDGER.get())) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof SignBlockEntity)) return;

        // A Ledger-on-sign interaction belongs to Hometown on both sides; do not let vanilla sign editing race it.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (event.getEntity() instanceof ServerPlayer player) {
            TownHallService.register(player, event.getHand(), event.getPos());
        }
    }
}
