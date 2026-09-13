package dev.conner.hometown.interaction;

import dev.conner.hometown.civic.TownAdministrationService;
import dev.conner.hometown.item.HometownItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Physical R3 Administration access: present the linked Ledger to a lectern in the registered Hall. */
public final class TownAdministrationInteractionHandler {
    private TownAdministrationInteractionHandler() {}

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(HometownItems.TOWN_LEDGER.get())) return;
        if (!event.getState().is(Blocks.LECTERN)) return;

        // While holding a Town Ledger, the Hall lectern gesture belongs to Hometown rather than vanilla lectern UI.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (event.getEntity() instanceof ServerPlayer player) {
            TownAdministrationService.open(player, event.getHand(), event.getPos())
                    .ifPresent(snapshot -> PacketDistributor.sendToPlayer(player, snapshot));
        }
    }
}
