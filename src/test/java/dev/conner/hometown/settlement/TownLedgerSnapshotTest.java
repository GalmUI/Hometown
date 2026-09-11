package dev.conner.hometown.settlement;

import dev.conner.hometown.network.RequestTownLedgerPayload;
import dev.conner.hometown.network.TownLedgerSnapshotPayload;
import dev.conner.hometown.network.data.ResidentSummary;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TownLedgerSnapshotTest {
    private final Settlement town = new Settlement(UUID.randomUUID(), "Dured", Level.OVERWORLD,
            new BlockPos(115, 68, 7), 64, UUID.randomUUID(), "GalrUI", 432000);

    @Test void pagesExposeEveryResidentWithoutOversizedPacketsOrMutableLists() {
        var residents = new ArrayList<ResidentSummary>();
        for (int i = 0; i < 101; i++) residents.add(new ResidentSummary("Villager " + i, "hometown.ledger.unemployed", i % 2 == 0));
        var stats = new SettlementStats(101, 3, 0, 0, SettlementStats.Availability.COMPLETE, residents);
        var seen = new ArrayList<ResidentSummary>();
        for (int page = 0; page < 26; page++) {
            var snapshot = TownLedgerSnapshot.of(town, stats, TownLedgerSnapshot.BellState.PRESENT, page);
            assertEquals(26, snapshot.residentPages());
            assertTrue(snapshot.residents().size() <= 4);
            seen.addAll(snapshot.residents());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.residents().clear());
        }
        assertEquals(residents, seen);
        assertEquals(25, TownLedgerSnapshot.of(town, stats, TownLedgerSnapshot.BellState.MISSING, Integer.MAX_VALUE).residentPage());
        assertEquals(0, TownLedgerSnapshot.of(town, stats, TownLedgerSnapshot.BellState.MISSING, -99).residentPage());
        residents.clear();
        assertEquals(101, stats.residents().size());
    }

    @Test void requestsSnapshotsAndGracefulErrorsRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var request = new RequestTownLedgerPayload(InteractionHand.OFF_HAND, 8, 99);
            RequestTownLedgerPayload.STREAM_CODEC.encode(buffer, request);
            assertEquals(request, RequestTownLedgerPayload.STREAM_CODEC.decode(buffer));
            buffer.clear();
            var stats = new SettlementStats(1, 2, 1, 1, SettlementStats.Availability.PARTIAL,
                    List.of(new ResidentSummary("Míra 村", "entity.minecraft.villager.librarian", false)));
            var snapshot = TownLedgerSnapshot.of(town, stats, TownLedgerSnapshot.BellState.MISSING, 0);
            var packet = new TownLedgerSnapshotPayload(99, snapshot, TownLedgerSnapshotPayload.Error.NONE);
            TownLedgerSnapshotPayload.STREAM_CODEC.encode(buffer, packet);
            assertTrue(buffer.readableBytes() < 8192);
            assertEquals(packet, TownLedgerSnapshotPayload.STREAM_CODEC.decode(buffer));
            for (var error : TownLedgerSnapshotPayload.Error.values()) {
                if (error == TownLedgerSnapshotPayload.Error.NONE) continue;
                buffer.clear();
                var response = new TownLedgerSnapshotPayload(99, null, error);
                TownLedgerSnapshotPayload.STREAM_CODEC.encode(buffer, response);
                assertEquals(response, TownLedgerSnapshotPayload.STREAM_CODEC.decode(buffer));
            }
        } finally { buffer.release(); }
    }

    @Test void unavailableHasIdentityButNoInventedStatisticsAndTextIsWireBounded() {
        var snapshot = TownLedgerSnapshot.of(town, SettlementStats.unavailable(), TownLedgerSnapshot.BellState.UNAVAILABLE, 99);
        assertEquals(town.id(), snapshot.settlementId());
        assertEquals(1, snapshot.residentPages());
        assertTrue(snapshot.residents().isEmpty());
        assertEquals(SettlementStats.Availability.UNAVAILABLE, snapshot.availability());
        var resident = new ResidentSummary("🌳".repeat(200), "p".repeat(400), true);
        assertTrue(resident.name().length() <= ResidentSummary.MAX_TEXT);
        assertTrue(resident.professionKey().length() <= ResidentSummary.MAX_TEXT);
        assertFalse(Character.isHighSurrogate(resident.name().charAt(resident.name().length() - 2)));
    }
}
