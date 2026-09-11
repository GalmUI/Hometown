package dev.conner.hometown.item;

import dev.conner.hometown.Hometown;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HometownItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Hometown.MOD_ID);
    public static final DeferredItem<TownLedgerItem> TOWN_LEDGER = REGISTER.register("town_ledger",
            () -> new TownLedgerItem(new Item.Properties().stacksTo(1)));
    private HometownItems() {}
}
