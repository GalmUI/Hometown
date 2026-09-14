package dev.conner.hometown;

import com.mojang.logging.LogUtils;
import dev.conner.hometown.command.HometownDebugCommands;
import dev.conner.hometown.command.M5DebugCommands;
import dev.conner.hometown.command.M6DebugCommands;
import dev.conner.hometown.component.HometownDataComponents;
import dev.conner.hometown.config.HometownServerConfig;
import dev.conner.hometown.interaction.BellInteractionHandler;
import dev.conner.hometown.interaction.TownAdministrationInteractionHandler;
import dev.conner.hometown.interaction.TownHallInteractionHandler;
import dev.conner.hometown.item.HometownItems;
import dev.conner.hometown.network.HometownNetworking;
import dev.conner.hometown.settlement.SettlementManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(Hometown.MOD_ID)
public final class Hometown {
    public static final String MOD_ID = "hometown";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Hometown(IEventBus bus, ModContainer container) {
        HometownDataComponents.REGISTER.register(bus);
        HometownItems.REGISTER.register(bus);
        bus.addListener(HometownNetworking::register);
        container.registerConfig(ModConfig.Type.SERVER, HometownServerConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(BellInteractionHandler::onRightClick);
        NeoForge.EVENT_BUS.addListener(TownHallInteractionHandler::onRightClick);
        NeoForge.EVENT_BUS.addListener(TownAdministrationInteractionHandler::onRightClick);
        NeoForge.EVENT_BUS.addListener(dev.conner.hometown.settlement.TownCensusService::onServerTick);
        NeoForge.EVENT_BUS.addListener(dev.conner.hometown.civic.AnimalFarmOperationsService::onServerTick);
        NeoForge.EVENT_BUS.addListener(dev.conner.hometown.food.DailyMealService::onServerTick);
        NeoForge.EVENT_BUS.addListener(HometownDebugCommands::register);
        NeoForge.EVENT_BUS.addListener(M5DebugCommands::register);
        NeoForge.EVENT_BUS.addListener(M6DebugCommands::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent event) -> {
            event.addListener(new dev.conner.hometown.comfort.ComfortRules());
            event.addListener(new dev.conner.hometown.food.CropRules());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            SettlementManager.release(event.getServer());
            dev.conner.hometown.settlement.TownLedgerService.release(event.getServer());
            dev.conner.hometown.settlement.TownCensusService.release(event.getServer());
            dev.conner.hometown.civic.AnimalFarmOperationsService.release(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                dev.conner.hometown.settlement.TownLedgerService.release(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                SettlementManager.get(player.server).cancel(player.getUUID());
                dev.conner.hometown.settlement.TownLedgerService.release(player);
            }
        });
    }
}
