package dev.conner.hometown.interaction;

import dev.conner.hometown.settlement.SettlementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BellInteractionHandler {
    private BellInteractionHandler() {}
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !event.getEntity().getMainHandItem().is(Items.BOOK)
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.BELL)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (event.getEntity() instanceof ServerPlayer player) {
            SettlementManager.get(player.server).beginFounding(player, event.getPos())
                    .ifPresent(payload -> PacketDistributor.sendToPlayer(player, payload));
        }
    }
}
