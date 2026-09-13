package dev.conner.hometown.settlement;

import dev.conner.hometown.component.SettlementIdComponent;
import dev.conner.hometown.network.RequestTownColorsPayload;
import dev.conner.hometown.network.SubmitTownColorsPayload;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TownColorServiceTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static DataComponentType<SettlementIdComponent> component() {
        return DataComponentType.<SettlementIdComponent>builder().persistent(SettlementIdComponent.CODEC).build();
    }

    private static Settlement town(String name) { return town(name, BlockPos.ZERO); }
    private static Settlement town(String name, BlockPos bell) {
        return new Settlement(UUID.randomUUID(), name, Level.OVERWORLD, bell, 64,
                UUID.randomUUID(), "Founder", 24000L);
    }

    private static ServerLevel validBellWorld(ServerPlayer player, Settlement town) {
        ServerLevel level = mock(ServerLevel.class);
        when(player.serverLevel()).thenReturn(level);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.hasChunkAt(town.bellPosition())).thenReturn(true);
        when(level.getBlockState(town.bellPosition())).thenReturn(Blocks.BELL.defaultBlockState());
        when(player.distanceToSqr(town.bellPosition().getX() + 0.5D, town.bellPosition().getY() + 0.5D,
                town.bellPosition().getZ() + 0.5D)).thenReturn(4.0D);
        return level;
    }

    @Test void linkedLedgerCanConfigureMigratedTownExactlyOnceAtFoundingBell() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);

        var data = new HometownSavedData();
        var town = town("Osea", new BlockPos(102, 64, 181));
        data.addSettlement(town);
        data.setDirty(false);
        validBellWorld(player, town);

        var component = component();
        var ledgerItem = Items.WRITTEN_BOOK;
        var stack = new ItemStack(ledgerItem);
        stack.set(component, new SettlementIdComponent(town.id()));
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

        try (var stores = mockStatic(HometownSavedData.class)) {
            stores.when(() -> HometownSavedData.get(server)).thenReturn(data);
            var open = TownColorService.begin(player,
                    new RequestTownColorsPayload(InteractionHand.MAIN_HAND, town.bellPosition()),
                    ledgerItem, component).orElseThrow();
            assertEquals(town.id(), open.settlementId());
            assertEquals("Osea", open.townName());
            assertFalse(data.civicState(town.id()).colorsConfigured());
            assertFalse(data.isDirty(), "opening color selection must not mutate SavedData");

            assertTrue(TownColorService.submit(player,
                    new SubmitTownColorsPayload(town.id(), InteractionHand.MAIN_HAND, DyeColor.RED, DyeColor.YELLOW),
                    ledgerItem, component));
            var civic = data.civicState(town.id());
            assertEquals(DyeColor.RED, civic.primaryColor().orElseThrow());
            assertEquals(DyeColor.YELLOW, civic.secondaryColor().orElseThrow());
            assertTrue(data.isDirty());

            data.setDirty(false);
            assertTrue(TownColorService.begin(player,
                    new RequestTownColorsPayload(InteractionHand.MAIN_HAND, town.bellPosition()),
                    ledgerItem, component).isEmpty());
            assertFalse(TownColorService.submit(player,
                    new SubmitTownColorsPayload(town.id(), InteractionHand.MAIN_HAND, DyeColor.BLUE, DyeColor.WHITE),
                    ledgerItem, component));
            assertFalse(data.isDirty());
            assertEquals(DyeColor.RED, data.civicState(town.id()).primaryColor().orElseThrow());
        }
    }

    @Test void linkedLedgerDoesNotOpenAtAnotherTownsBell() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);

        var data = new HometownSavedData();
        var osea = town("Osea", new BlockPos(102, 64, 181));
        var oured = town("Oured", new BlockPos(300, 70, 0));
        data.addSettlement(osea);
        data.addSettlement(oured);
        data.setDirty(false);

        var component = component();
        var ledgerItem = Items.WRITTEN_BOOK;
        var ledger = new ItemStack(ledgerItem);
        ledger.set(component, new SettlementIdComponent(osea.id()));
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(ledger);

        try (var stores = mockStatic(HometownSavedData.class)) {
            stores.when(() -> HometownSavedData.get(server)).thenReturn(data);
            assertTrue(TownColorService.begin(player,
                    new RequestTownColorsPayload(InteractionHand.MAIN_HAND, oured.bellPosition()),
                    ledgerItem, component).isEmpty());
            assertFalse(data.civicState(osea.id()).colorsConfigured());
            assertFalse(data.civicState(oured.id()).colorsConfigured());
            assertFalse(data.isDirty());
            verify(player, never()).serverLevel();
        }
    }

    @Test void savedBellPositionMustStillContainALoadedBell() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);

        var data = new HometownSavedData();
        var town = town("Osea", new BlockPos(102, 64, 181));
        data.addSettlement(town);
        data.setDirty(false);

        ServerLevel level = mock(ServerLevel.class);
        when(player.serverLevel()).thenReturn(level);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.hasChunkAt(town.bellPosition())).thenReturn(true);
        when(level.getBlockState(town.bellPosition())).thenReturn(Blocks.STONE.defaultBlockState());

        var component = component();
        var ledgerItem = Items.WRITTEN_BOOK;
        var ledger = new ItemStack(ledgerItem);
        ledger.set(component, new SettlementIdComponent(town.id()));
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(ledger);

        try (var stores = mockStatic(HometownSavedData.class)) {
            stores.when(() -> HometownSavedData.get(server)).thenReturn(data);
            assertTrue(TownColorService.begin(player,
                    new RequestTownColorsPayload(InteractionHand.MAIN_HAND, town.bellPosition()),
                    ledgerItem, component).isEmpty());
            assertFalse(data.civicState(town.id()).colorsConfigured());
            assertFalse(data.isDirty());
        }
    }

    @Test void colorSetupRequiresPlayerToBeNearFoundingBell() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);

        var data = new HometownSavedData();
        var town = town("Osea", new BlockPos(102, 64, 181));
        data.addSettlement(town);
        data.setDirty(false);
        validBellWorld(player, town);
        when(player.distanceToSqr(town.bellPosition().getX() + 0.5D, town.bellPosition().getY() + 0.5D,
                town.bellPosition().getZ() + 0.5D)).thenReturn(100.0D);

        var component = component();
        var ledgerItem = Items.WRITTEN_BOOK;
        var ledger = new ItemStack(ledgerItem);
        ledger.set(component, new SettlementIdComponent(town.id()));
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(ledger);

        try (var stores = mockStatic(HometownSavedData.class)) {
            stores.when(() -> HometownSavedData.get(server)).thenReturn(data);
            assertTrue(TownColorService.begin(player,
                    new RequestTownColorsPayload(InteractionHand.MAIN_HAND, town.bellPosition()),
                    ledgerItem, component).isEmpty());
            assertFalse(data.civicState(town.id()).colorsConfigured());
            assertFalse(data.isDirty());
        }
    }

    @Test void wrongOrSpoofedLedgerCannotConfigureAnotherTown() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);

        var data = new HometownSavedData();
        var first = town("Osea", BlockPos.ZERO);
        var second = town("Oured", new BlockPos(256, 64, 0));
        data.addSettlement(first);
        data.addSettlement(second);
        data.setDirty(false);

        var component = component();
        var ledgerItem = Items.WRITTEN_BOOK;
        var firstLedger = new ItemStack(ledgerItem);
        firstLedger.set(component, new SettlementIdComponent(first.id()));
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(firstLedger);
        when(player.getItemInHand(InteractionHand.OFF_HAND)).thenReturn(new ItemStack(Items.BOOK));

        try (var stores = mockStatic(HometownSavedData.class)) {
            stores.when(() -> HometownSavedData.get(server)).thenReturn(data);
            assertFalse(TownColorService.submit(player,
                    new SubmitTownColorsPayload(second.id(), InteractionHand.MAIN_HAND, DyeColor.GREEN, DyeColor.WHITE),
                    ledgerItem, component));
            assertFalse(data.civicState(first.id()).colorsConfigured());
            assertFalse(data.civicState(second.id()).colorsConfigured());
            assertFalse(data.isDirty());

            assertTrue(TownColorService.begin(player,
                    new RequestTownColorsPayload(InteractionHand.OFF_HAND, first.bellPosition()),
                    ledgerItem, component).isEmpty());
            assertFalse(data.isDirty());
        }
    }

    @Test void sameColorSubmissionIsRejectedWithoutMutation() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(player.isAlive()).thenReturn(true);

        var data = new HometownSavedData();
        var town = town("Osea");
        data.addSettlement(town);
        data.setDirty(false);
        var component = component();
        var ledgerItem = Items.WRITTEN_BOOK;
        var stack = new ItemStack(ledgerItem);
        stack.set(component, new SettlementIdComponent(town.id()));
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

        try (var stores = mockStatic(HometownSavedData.class)) {
            stores.when(() -> HometownSavedData.get(server)).thenReturn(data);
            assertFalse(TownColorService.submit(player,
                    new SubmitTownColorsPayload(town.id(), InteractionHand.MAIN_HAND, DyeColor.BLACK, DyeColor.BLACK),
                    ledgerItem, component));
            assertFalse(data.civicState(town.id()).colorsConfigured());
            assertFalse(data.isDirty());
        }
    }
}
