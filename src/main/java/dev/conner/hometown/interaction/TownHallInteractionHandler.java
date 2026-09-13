package dev.conner.hometown.interaction;

import dev.conner.hometown.civic.StorageService;
import dev.conner.hometown.civic.StorageSignGrammar;
import dev.conner.hometown.civic.TownHallService;
import dev.conner.hometown.civic.TownHallSignGrammar;
import dev.conner.hometown.item.HometownItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Deliberate civic registration gesture: present a linked Town Ledger to a vanilla Hometown sign. */
public final class TownHallInteractionHandler {
    private TownHallInteractionHandler() {}

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(HometownItems.TOWN_LEDGER.get())) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof SignBlockEntity sign)) return;

        // A Ledger-on-sign interaction belongs to Hometown on both sides; do not let vanilla sign editing race it.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        boolean front = sign.isFacingFrontText(player);
        var text = sign.getText(front);
        if (TownHallSignGrammar.matches(text)) {
            TownHallService.register(player, event.getHand(), event.getPos());
        } else if (StorageSignGrammar.matches(text)) {
            StorageService.register(player, event.getHand(), event.getPos());
        } else {
            player.sendSystemMessage(Component.literal(
                    "Write [Hometown] on line 1 and a recognized civic facility (Town Hall or Storage) on line 2."));
        }
    }
}
