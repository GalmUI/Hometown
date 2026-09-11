package dev.conner.hometown.component;

import dev.conner.hometown.Hometown;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HometownDataComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTER = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Hometown.MOD_ID);
    public static final Supplier<DataComponentType<SettlementIdComponent>> SETTLEMENT_ID = REGISTER.register("settlement_id",
            () -> DataComponentType.<SettlementIdComponent>builder()
                    .persistent(SettlementIdComponent.CODEC).networkSynchronized(SettlementIdComponent.STREAM_CODEC).build());
    private HometownDataComponents() {}
}
