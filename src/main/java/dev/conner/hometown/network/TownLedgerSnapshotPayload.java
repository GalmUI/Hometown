package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.network.data.ResidentSummary;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import dev.conner.hometown.settlement.SettlementStats;
import java.util.ArrayList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TownLedgerSnapshotPayload(int requestId, TownLedgerSnapshot snapshot, Error error) implements CustomPacketPayload {
    public enum Error { NONE, UNKNOWN, HOLD_LEDGER, WAIT, FAILED }
    public static final Type<TownLedgerSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "ledger_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, TownLedgerSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            TownLedgerSnapshotPayload::write, TownLedgerSnapshotPayload::read);
    public TownLedgerSnapshotPayload {
        if ((error == Error.NONE) != (snapshot != null)) throw new IllegalArgumentException("Invalid ledger response");
    }
    private static void write(FriendlyByteBuf b, TownLedgerSnapshotPayload p) {
        b.writeInt(p.requestId()); b.writeEnum(p.error());
        if (p.error() != Error.NONE) return;
        var s = p.snapshot();
        b.writeUUID(s.settlementId()); b.writeUtf(s.townName(), 64); b.writeUtf(s.founderName(), 256);
        b.writeLong(s.foundedDay()); b.writeUtf(s.dimension(), 256); b.writeBlockPos(s.bell());
        b.writeEnum(s.bellState()); b.writeEnum(s.availability());
        b.writeVarInt(s.population()); b.writeVarInt(s.beds()); b.writeVarInt(s.employed()); b.writeVarInt(s.professionDiversity());
        var h = s.housing();
        for (int value : new int[]{h.population(), h.totalBeds(), h.enclosedBeds(), h.unsealedBeds(), h.roomCount(), h.privateRooms(), h.sharedRooms(), h.sharedBeds(), h.unknownBeds(), h.crowdedRooms(), h.highDensityRooms(), h.privateBeds(), h.crowdedBeds(), h.highDensityBeds()}) b.writeVarInt(value);
        b.writeBoolean(h.scanComplete());
        b.writeBoolean(h.privacyPercent().isPresent());
        if (h.privacyPercent().isPresent()) b.writeVarInt(h.privacyPercent().getAsInt());
        b.writeVarInt(s.residentPage()); b.writeVarInt(s.residentPages()); b.writeVarInt(s.residents().size());
        for (var r : s.residents()) {
            b.writeUtf(r.name(), ResidentSummary.MAX_TEXT); b.writeUtf(r.professionKey(), ResidentSummary.MAX_TEXT); b.writeBoolean(r.child());
        }
        var f = s.food();
        b.writeVarInt(f.population()); b.writeVarInt(f.foodContainers()); b.writeVarInt(f.foodStacks()); b.writeVarInt(f.uniqueFoodTypes());
        b.writeLong(f.totalNutrition()); b.writeLong(f.dailyNutritionRequirement()); b.writeEnum(f.scanStatus());
        b.writeBoolean(f.state().isPresent()); if(f.state().isPresent()) b.writeEnum(f.state().get());
        var d=f.diagnostics(); b.writeVarInt(d.reasons().size());
        for(var reason:new java.util.TreeSet<>(d.reasons())) b.writeEnum(reason);
        for(int value:new int[]{d.settlementChunks(),d.loadedChunks(),d.unavailableChunks(),d.blockEntitiesInspected(),d.storageFound(),d.storageScanned(),
                d.duplicateInventoriesSkipped(),d.storageUnavailable(),d.lootContainersSkipped(),d.slotsInspected(),d.limits().blockEntities(),d.limits().storageContainers(),d.limits().inventorySlots()}) b.writeVarInt(value);
        b.writeBoolean(d.populationComplete());
        b.writeBoolean(f.reserveDays().isPresent()); if (f.reserveDays().isPresent()) b.writeDouble(f.reserveDays().getAsDouble());
        b.writeBoolean(f.barPercent().isPresent()); if (f.barPercent().isPresent()) b.writeVarInt(f.barPercent().getAsInt());
        s.safety().write(b);
        s.comfort().write(b);
    }
    private static TownLedgerSnapshotPayload read(FriendlyByteBuf b) {
        int request = b.readInt();
        Error error = b.readEnum(Error.class);
        if (error != Error.NONE) return new TownLedgerSnapshotPayload(request, null, error);
        var id = b.readUUID(); var name = b.readUtf(64); var founder = b.readUtf(256);
        long day = b.readLong(); var dimension = b.readUtf(256); var bell = b.readBlockPos();
        var bellState = b.readEnum(TownLedgerSnapshot.BellState.class); var availability = b.readEnum(SettlementStats.Availability.class);
        int population = b.readVarInt(), beds = b.readVarInt(), employed = b.readVarInt(), diversity = b.readVarInt();
        int hp = b.readVarInt(), ht = b.readVarInt(), he = b.readVarInt(), hu = b.readVarInt(), hr = b.readVarInt();
        int hpr = b.readVarInt(), hsr = b.readVarInt(), hsb = b.readVarInt(), huk = b.readVarInt();
        int hcr = b.readVarInt(), hdr = b.readVarInt(), hpb = b.readVarInt(), hcb = b.readVarInt(), hdb = b.readVarInt();
        boolean complete = b.readBoolean();
        var privacy = b.readBoolean() ? java.util.OptionalInt.of(b.readVarInt()) : java.util.OptionalInt.empty();
        var housing = new dev.conner.hometown.housing.HousingSnapshot(hp,ht,he,hu,hr,hpr,hsr,hsb,huk,complete,hcr,hdr,hpb,hcb,hdb,privacy);
        int page = b.readVarInt(), pages = b.readVarInt(), count = b.readVarInt();
        if (count < 0 || count > TownLedgerSnapshot.PAGE_SIZE) throw new IllegalArgumentException("Resident page too large");
        var residents = new ArrayList<ResidentSummary>(count);
        for (int i = 0; i < count; i++) residents.add(new ResidentSummary(b.readUtf(ResidentSummary.MAX_TEXT), b.readUtf(ResidentSummary.MAX_TEXT), b.readBoolean()));
        int fp=b.readVarInt(), fc=b.readVarInt(), fs=b.readVarInt(), ft=b.readVarInt();
        long nutrition=b.readLong(), daily=b.readLong();
        var foodStatus=b.readEnum(dev.conner.hometown.food.FoodScanStatus.class);
        var foodState=b.readBoolean()?java.util.Optional.of(b.readEnum(dev.conner.hometown.food.FoodSnapshot.FoodSecurityState.class)):java.util.Optional.<dev.conner.hometown.food.FoodSnapshot.FoodSecurityState>empty();
        int reasonCount=b.readVarInt();
        if(reasonCount<0 || reasonCount>=dev.conner.hometown.food.FoodScanReason.values().length) throw new IllegalArgumentException("Invalid Food reason count");
        var reasons=java.util.EnumSet.noneOf(dev.conner.hometown.food.FoodScanReason.class);
        for(int i=0;i<reasonCount;i++) if(!reasons.add(b.readEnum(dev.conner.hometown.food.FoodScanReason.class))) throw new IllegalArgumentException("Duplicate Food reason");
        int chunks=b.readVarInt(), loaded=b.readVarInt(), unavailable=b.readVarInt(), inspected=b.readVarInt(), found=b.readVarInt(), scanned=b.readVarInt(), duplicates=b.readVarInt(), missing=b.readVarInt(), loot=b.readVarInt(), slots=b.readVarInt();
        var limits=new dev.conner.hometown.food.FoodScanner.Limits(b.readVarInt(),b.readVarInt(),b.readVarInt());
        var diagnostics=new dev.conner.hometown.food.FoodScanDiagnostics(reasons,chunks,loaded,unavailable,inspected,found,scanned,duplicates,missing,loot,slots,limits,b.readBoolean());
        var days=b.readBoolean()?java.util.OptionalDouble.of(b.readDouble()):java.util.OptionalDouble.empty();
        var bar=b.readBoolean()?java.util.OptionalInt.of(b.readVarInt()):java.util.OptionalInt.empty();
        var food=new dev.conner.hometown.food.FoodSnapshot(fp,fc,fs,ft,nutrition,daily,days,foodState,foodStatus,bar,diagnostics);
        return new TownLedgerSnapshotPayload(request, new TownLedgerSnapshot(id, name, founder, day, dimension, bell,
                bellState, availability, population, beds, employed, diversity, page, pages, residents, housing, food,
                dev.conner.hometown.safety.SafetySnapshot.read(b),dev.conner.hometown.comfort.ComfortSnapshot.read(b)), Error.NONE);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
