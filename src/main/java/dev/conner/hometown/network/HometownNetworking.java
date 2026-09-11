package dev.conner.hometown.network;

import dev.conner.hometown.settlement.SettlementManager;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class HometownNetworking {
    // Client installs this callback during its own setup. Common/server code never links GUI classes.
    private static Consumer<OpenTownNamingPayload> clientHandler = payload -> {};
    private static Consumer<net.minecraft.world.InteractionHand> ledgerOpener = hand -> {};
    private static Consumer<TownLedgerSnapshotPayload> ledgerHandler = payload -> {};
    private HometownNetworking() {}
    public static void setClientHandler(Consumer<OpenTownNamingPayload> handler) { clientHandler = handler; }
    public static void setLedgerHandlers(Consumer<net.minecraft.world.InteractionHand> opener, Consumer<TownLedgerSnapshotPayload> handler) {
        ledgerOpener = opener;
        ledgerHandler = handler;
    }
    public static void requestLedger(net.minecraft.world.InteractionHand hand) { ledgerOpener.accept(hand); }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("8");
        registrar.playToServer(RequestTownLedgerPayload.TYPE, RequestTownLedgerPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        dev.conner.hometown.settlement.TownLedgerService.respond(player, payload));
            }
        });
        registrar.playToClient(TownLedgerSnapshotPayload.TYPE, TownLedgerSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ledgerHandler.accept(payload));
        registrar.playToClient(OpenTownNamingPayload.TYPE, OpenTownNamingPayload.STREAM_CODEC,
                (payload, context) -> clientHandler.accept(payload));
        // NeoForge defaults to the main thread; validation and creation are one synchronous operation.
        registrar.playToServer(SubmitTownNamePayload.TYPE, SubmitTownNamePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SettlementManager.get(player.server).createSettlement(player, payload.bellPosition(), payload.nonce(), payload.name());
            }
        });
    }
}
