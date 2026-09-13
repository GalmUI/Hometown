package dev.conner.hometown.network;

import dev.conner.hometown.settlement.SettlementManager;
import dev.conner.hometown.settlement.TownColorService;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class HometownNetworking {
    // Client installs these callbacks during its own setup. Common/server code never links GUI classes.
    private static Consumer<OpenTownNamingPayload> clientHandler = payload -> {};
    private static Consumer<OpenTownColorsPayload> colorHandler = payload -> {};
    private static Consumer<net.minecraft.world.InteractionHand> ledgerOpener = hand -> {};
    private static Consumer<TownLedgerSnapshotPayload> ledgerHandler = payload -> {};
    private static Consumer<FoodM3SnapshotPayload> foodM3Handler = payload -> {};
    private static Consumer<CommerceSnapshotPayload> commerceHandler = payload -> {};
    private static Consumer<ProsperitySnapshotPayload> prosperityHandler = payload -> {};
    private static Consumer<HistorySnapshotPayload> historyHandler = payload -> {};
    private static Consumer<TownAdministrationSnapshotPayload> administrationHandler = payload -> {};
    private HometownNetworking() {}
    public static void setClientHandler(Consumer<OpenTownNamingPayload> handler) { clientHandler = handler; }
    public static void setColorHandler(Consumer<OpenTownColorsPayload> handler) { colorHandler = handler; }
    public static void setLedgerHandlers(Consumer<net.minecraft.world.InteractionHand> opener, Consumer<TownLedgerSnapshotPayload> handler,
            Consumer<FoodM3SnapshotPayload> foodHandler, Consumer<CommerceSnapshotPayload> commerce,
            Consumer<ProsperitySnapshotPayload> prosperity) {
        ledgerOpener = opener;ledgerHandler = handler;foodM3Handler = foodHandler;commerceHandler=commerce;prosperityHandler=prosperity;
    }
    public static void setHistoryHandler(Consumer<HistorySnapshotPayload> history){historyHandler=history;}
    public static void setAdministrationHandler(Consumer<TownAdministrationSnapshotPayload> administration){administrationHandler=administration;}
    public static void requestLedger(net.minecraft.world.InteractionHand hand) { ledgerOpener.accept(hand); }
    public static void requestTownColors(net.minecraft.world.InteractionHand hand, BlockPos bellPosition) {
        PacketDistributor.sendToServer(new RequestTownColorsPayload(hand, bellPosition));
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("15");
        registrar.playToServer(RequestTownLedgerPayload.TYPE, RequestTownLedgerPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                var response=dev.conner.hometown.settlement.TownLedgerService.respond(player,payload);
                PacketDistributor.sendToPlayer(player,response);
                if(response.snapshot()!=null){
                    var settlementId=response.snapshot().settlementId();var generation=response.snapshot().metadata().requestGeneration();
                    dev.conner.hometown.settlement.TownLedgerService.foodM3(player,settlementId,generation,payload.requestId()).ifPresent(m3->
                            PacketDistributor.sendToPlayer(player,m3));
                    dev.conner.hometown.settlement.TownLedgerService.commerce(player,settlementId,generation,payload.requestId()).ifPresent(snapshot->
                            PacketDistributor.sendToPlayer(player,snapshot));
                    dev.conner.hometown.settlement.TownLedgerService.prosperity(player,settlementId,generation,payload.requestId()).ifPresent(snapshot->
                            PacketDistributor.sendToPlayer(player,snapshot));
                    dev.conner.hometown.settlement.TownLedgerService.history(player,settlementId,generation).ifPresent(history->
                            PacketDistributor.sendToPlayer(player,HistorySnapshotPayload.page(payload.requestId(),settlementId,generation,0,history)));
                }
            }
        });
        registrar.playToServer(RequestHistoryPagePayload.TYPE,RequestHistoryPagePayload.STREAM_CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player){
                var history=dev.conner.hometown.settlement.TownLedgerService.history(player,payload.settlementId(),payload.generation());
                var response=history.map(events->HistorySnapshotPayload.page(payload.requestId(),payload.settlementId(),payload.generation(),payload.page(),events))
                        .orElseGet(()->HistorySnapshotPayload.unavailable(payload.requestId(),payload.settlementId(),payload.generation(),payload.page()));
                PacketDistributor.sendToPlayer(player,response);
            }
        });
        registrar.playToClient(TownLedgerSnapshotPayload.TYPE, TownLedgerSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ledgerHandler.accept(payload));
        registrar.playToClient(FoodM3SnapshotPayload.TYPE,FoodM3SnapshotPayload.STREAM_CODEC,
                (payload,context)->foodM3Handler.accept(payload));
        registrar.playToClient(CommerceSnapshotPayload.TYPE,CommerceSnapshotPayload.STREAM_CODEC,
                (payload,context)->commerceHandler.accept(payload));
        registrar.playToClient(ProsperitySnapshotPayload.TYPE,ProsperitySnapshotPayload.STREAM_CODEC,
                (payload,context)->prosperityHandler.accept(payload));
        registrar.playToClient(HistorySnapshotPayload.TYPE,HistorySnapshotPayload.STREAM_CODEC,
                (payload,context)->historyHandler.accept(payload));
        registrar.playToClient(TownAdministrationSnapshotPayload.TYPE,TownAdministrationSnapshotPayload.STREAM_CODEC,
                (payload,context)->administrationHandler.accept(payload));
        registrar.playToClient(OpenTownNamingPayload.TYPE, OpenTownNamingPayload.STREAM_CODEC,
                (payload, context) -> clientHandler.accept(payload));
        registrar.playToClient(OpenTownColorsPayload.TYPE, OpenTownColorsPayload.STREAM_CODEC,
                (payload, context) -> colorHandler.accept(payload));
        registrar.playToServer(RequestTownColorsPayload.TYPE, RequestTownColorsPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                TownColorService.begin(player, payload).ifPresent(open -> PacketDistributor.sendToPlayer(player, open));
            }
        });
        registrar.playToServer(SubmitTownColorsPayload.TYPE, SubmitTownColorsPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) TownColorService.submit(player, payload);
        });
        // NeoForge defaults to the main thread; validation and creation are one synchronous operation.
        registrar.playToServer(SubmitTownNamePayload.TYPE, SubmitTownNamePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                SettlementManager.get(player.server).createSettlement(player, payload.bellPosition(), payload.nonce(), payload.name(),
                        payload.primary(), payload.secondary());
            }
        });
    }
}
