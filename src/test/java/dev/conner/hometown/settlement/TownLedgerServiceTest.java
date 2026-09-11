package dev.conner.hometown.settlement;

import dev.conner.hometown.component.SettlementIdComponent;
import dev.conner.hometown.network.RequestTownLedgerPayload;
import dev.conner.hometown.network.TownLedgerSnapshotPayload;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TownLedgerServiceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void authorityUnknownLedgerBellRecoveryAndMissingDimension() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);
        var cooldowns = new ItemCooldowns();
        when(player.getCooldowns()).thenReturn(cooldowns);
        var component = DataComponentType.<SettlementIdComponent>builder().persistent(SettlementIdComponent.CODEC).build();
        // Supply registry handles without requiring a running mod loader in this unit test.
        var ledgerItem = Items.WRITTEN_BOOK;
        var stack = new ItemStack(ledgerItem);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
        when(player.getItemInHand(InteractionHand.OFF_HAND)).thenReturn(new ItemStack(Items.BOOK));
        var request = new RequestTownLedgerPayload(InteractionHand.MAIN_HAND, 0, 42);
        var data = new HometownSavedData();
        var town = new Settlement(UUID.randomUUID(), "Dured", Level.OVERWORLD, new BlockPos(8, 64, 8), 16,
                UUID.randomUUID(), "GalrUI", 432000);
        data.addSettlement(town);
        try (var config=new dev.conner.hometown.TestServerConfig();
                var stores = mockStatic(HometownSavedData.class); var rules = mockStatic(SettlementValidator.Rules.class);
                var scanner = mockStatic(SettlementScanner.class)) {
            stores.when(() -> HometownSavedData.get(server)).thenReturn(data);
            rules.when(SettlementValidator.Rules::current).thenReturn(new SettlementValidator.Rules(64, 32, 2, 2, true, true));
            assertEquals(TownLedgerSnapshotPayload.Error.UNKNOWN, TownLedgerService.respond(player, request, ledgerItem, component).error());
            stack.set(component, new SettlementIdComponent(UUID.randomUUID()));
            assertEquals(TownLedgerSnapshotPayload.Error.UNKNOWN, TownLedgerService.respond(player, request, ledgerItem, component).error());
            assertEquals(TownLedgerSnapshotPayload.Error.HOLD_LEDGER, TownLedgerService.respond(player,
                    new RequestTownLedgerPayload(InteractionHand.OFF_HAND, 0, 43), ledgerItem, component).error());
            scanner.verifyNoInteractions();
            stack.set(component, new SettlementIdComponent(town.id()));
            ServerLevel level = mock(ServerLevel.class);
            when(server.overworld()).thenReturn(level);
            when(player.getUUID()).thenReturn(UUID.randomUUID());
            ServerChunkCache chunks = mock(ServerChunkCache.class);
            when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
            when(level.getChunkSource()).thenReturn(chunks);
            when(chunks.getChunkNow(0, 0)).thenReturn(mock(LevelChunk.class));
            when(level.getBlockState(town.bellPosition())).thenReturn(Blocks.AIR.defaultBlockState());
            scanner.when(() -> SettlementScanner.scan(level, town, 32)).thenReturn(SettlementStats.unavailable());
            var missing = TownLedgerService.respond(player, request, ledgerItem, component);
            assertEquals(TownLedgerSnapshotPayload.Error.NONE, missing.error());
            assertEquals(TownLedgerSnapshot.BellState.MISSING, missing.snapshot().bellState());
            assertEquals(town.id(), missing.snapshot().settlementId());
            assertEquals(18, missing.snapshot().foundedDay());
            assertEquals(42, missing.requestId());
            assertEquals(missing.snapshot(), TownLedgerService.respond(player, request, ledgerItem, component).snapshot());
            cooldowns.removeCooldown(ledgerItem);
            when(level.getGameTime()).thenReturn(40L);
            when(level.getBlockState(town.bellPosition())).thenReturn(Blocks.BELL.defaultBlockState());
            assertEquals(TownLedgerSnapshot.BellState.PRESENT, TownLedgerService.respond(player, request, ledgerItem, component).snapshot().bellState());
            cooldowns.removeCooldown(ledgerItem);
            when(level.getGameTime()).thenReturn(80L);
            when(server.getLevel(Level.OVERWORLD)).thenReturn(null);
            scanner.when(() -> SettlementScanner.scan(null, town, 32)).thenReturn(SettlementStats.unavailable());
            var unloaded = TownLedgerService.respond(player, request, ledgerItem, component).snapshot();
            assertEquals(TownLedgerSnapshot.BellState.UNAVAILABLE, unloaded.bellState());
            assertEquals(SettlementStats.Availability.UNAVAILABLE, unloaded.availability());
            assertEquals(town, data.getSettlement(town.id()).orElseThrow());
            assertEquals(1, stack.getCount());
            verify(chunks, never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());
        }
    }
}
