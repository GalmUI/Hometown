package dev.conner.hometown.item;

import dev.conner.hometown.component.HometownDataComponents;
import dev.conner.hometown.component.SettlementIdComponent;
import dev.conner.hometown.settlement.Settlement;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

public final class TownLedgerItem extends Item {
    public TownLedgerItem(Properties properties) { super(properties); }

    public static ItemStack create(Settlement settlement) {
        ItemStack stack = new ItemStack(HometownItems.TOWN_LEDGER.get());
        stack.set(HometownDataComponents.SETTLEMENT_ID.get(), new SettlementIdComponent(settlement.id()));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.hometown.town_ledger.named", settlement.name()));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("hometown.ledger.founder", settlement.founderName()),
                Component.translatable("hometown.ledger.day", settlement.foundedDay()))));
        return stack;
    }

    public static boolean deliver(ServerPlayer player, ItemStack stack) {
        // Inventory.add may discard an overflow stack in Creative and report success.
        // A non-stackable ledger can only occupy an empty main-inventory slot.
        int slot = player.getInventory().getFreeSlot();
        if (slot >= 0) {
            player.getInventory().setItem(slot, stack.copy());
            player.getInventory().setChanged();
            stack.setCount(0);
            return true;
        }
        // Explicit addFreshEntity return value lets founding roll back if the drop is rejected.
        ItemEntity entity = new ItemEntity(player.serverLevel(), player.getX(), player.getY() + 0.5, player.getZ(), stack.copy(), 0, 0.1, 0);
        entity.setDefaultPickUpDelay();
        entity.setTarget(player.getUUID());
        boolean delivered = player.serverLevel().addFreshEntity(entity);
        if (delivered) stack.setCount(0);
        return delivered;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            if (player.isShiftKeyDown()) dev.conner.hometown.network.HometownNetworking.requestTownColors(hand);
            else dev.conner.hometown.network.HometownNetworking.requestLedger(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
