package dev.conner.hometown.network;

import dev.conner.hometown.settlement.SettlementManager;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class HometownNetworking {
    // Client installs these callbacks during its own setup. Common/server code never links GUI classes.
    private static Consumer<OpenTownNamingPayload> clientHandler = payload -> {};
    private static Consumer<net.minecraft.world.InteractionHand> ledgerOpener = hand -> {};
    private static Consumer<TownLedgerSnapshotPayload> ledgerHandler = payload -> {};
    private static Consumer<FoodM3SnapshotPayload> foodM3Handler = payload -> {};
    private static Consumer<CommerceSnapshotPayload> commerceHandler = payload -> {};
    private HometownNetworking() {}
    public static void setClientHandler(Consumer<OpenTownNamingPayload> handler) { clientHandler = handler; }
    public static void setLedgerHandlers(Consumer<net.minecraft.world.InteractionHand> opener, Consumer<TownLedgerSnapshotPayload> handler,
            Consumer<FoodM3SnapshotPayload> foodHandler, Consumer<CommerceSnapshotPayload> commerce) {
        ledgerOpener = opener;ledgerHandler = handler;foodM3Handler = foodHandler;commerceHandler=commerce;
    }
    public static void requestLedger(net.minecraft.world.InteractionHand hand) { ledgerOpener.accept(hand); }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("10");
        registrar.playToServer(RequestTownLedgerPayload.TYPE, RequestTownLedgerPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                var response=dev.conner.hometown.settlement.TownLedgerService.respond(player,payload);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,response);
                if(response.snapshot()!=null){
                    var settlementId=response.snapshot().settlementId();var generation=response.snapshot().metadata().requestGeneration();
                    dev.conner.hometown.settlement.TownLedgerService.foodM3(player,settlementId,generation,payload.requestId()).ifPresent(m3->
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,m3));
                    dev.conner.hometown.settlement.TownLedgerService.commerce(player,settlementId,generation,payload.requestId()).ifPresent(snapshot->
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,snapshot));
                }
            }
        });
        registrar.playToClient(TownLedgerSnapshotPayload.TYPE, TownLedgerSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ledgerHandler.accept(payload));
        registrar.playToClient(FoodM3SnapshotPayload.TYPE,FoodM3SnapshotPayload.STREAM_CODEC,
                (payload,context)->foodM3Handler.accept(payload));
        registrar.playToClient(CommerceSnapshotPayload.TYPE,CommerceSnapshotPayload.STREAM_CODEC,
                (payload,context)->commerceHandler.accept(payload));
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
