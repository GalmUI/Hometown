package dev.conner.hometown.client;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.network.HometownNetworking;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Hometown.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HometownClient {
    private HometownClient() {}
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            HometownNetworking.setClientHandler(payload ->
                    Minecraft.getInstance().setScreen(new TownNamingScreen(payload.bellPosition(), payload.nonce())));
            HometownNetworking.setLedgerHandlers(hand -> {
                var screen = new TownLedgerScreen(hand);
                Minecraft.getInstance().setScreen(screen);
                screen.requestPage(0);
            }, payload -> {
                // A response cannot reopen a closed screen or replace a newer request.
                if (Minecraft.getInstance().screen instanceof TownLedgerScreen screen) screen.receive(payload);
            }, payload -> {
                if (Minecraft.getInstance().screen instanceof TownLedgerScreen screen) screen.receiveFoodM3(payload);
            }, payload -> {
                if (Minecraft.getInstance().screen instanceof TownLedgerScreen screen) screen.receiveCommerce(payload);
            });
        });
    }
}
