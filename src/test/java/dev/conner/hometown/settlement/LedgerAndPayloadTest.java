package dev.conner.hometown.settlement;

import dev.conner.hometown.component.SettlementIdComponent;
import dev.conner.hometown.item.TownLedgerItem;
import dev.conner.hometown.network.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LedgerAndPayloadTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void settlementUuidSurvivesPersistentAndNetworkCodecs() {
        SettlementIdComponent component = new SettlementIdComponent(UUID.randomUUID());
        var encoded = SettlementIdComponent.CODEC.encodeStart(NbtOps.INSTANCE, component).getOrThrow();
        assertEquals(component, SettlementIdComponent.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SettlementIdComponent.STREAM_CODEC.encode(buffer, component);
            assertEquals(component, SettlementIdComponent.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void namingPayloadsRoundTripUnicodeColorsAndLimitWireSize() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var open = new OpenTownNamingPayload(new BlockPos(-123, 70, 456), Long.MIN_VALUE);
            OpenTownNamingPayload.STREAM_CODEC.encode(buffer, open);
            assertEquals(open, OpenTownNamingPayload.STREAM_CODEC.decode(buffer));
            buffer.clear();
            var submit = new SubmitTownNamePayload(open.bellPosition(), open.nonce(), "🌳".repeat(32),
                    DyeColor.BLUE, DyeColor.WHITE);
            SubmitTownNamePayload.STREAM_CODEC.encode(buffer, submit);
            assertEquals(submit, SubmitTownNamePayload.STREAM_CODEC.decode(buffer));
            buffer.clear();
            assertThrows(RuntimeException.class, () -> SubmitTownNamePayload.STREAM_CODEC.encode(buffer,
                    new SubmitTownNamePayload(BlockPos.ZERO, 0, "x".repeat(257), DyeColor.RED, DyeColor.BLACK)));
        } finally { buffer.release(); }
    }

    @Test void existingTownColorPayloadsRoundTripIdentityHandBellAndPalette() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var request = new RequestTownColorsPayload(InteractionHand.OFF_HAND, new BlockPos(11, 64, -9));
            RequestTownColorsPayload.STREAM_CODEC.encode(buffer, request);
            assertEquals(request, RequestTownColorsPayload.STREAM_CODEC.decode(buffer));
            buffer.clear();

            UUID townId = UUID.randomUUID();
            var open = new OpenTownColorsPayload(townId, "Osea", InteractionHand.MAIN_HAND);
            OpenTownColorsPayload.STREAM_CODEC.encode(buffer, open);
            assertEquals(open, OpenTownColorsPayload.STREAM_CODEC.decode(buffer));
            buffer.clear();

            var submit = new SubmitTownColorsPayload(townId, InteractionHand.MAIN_HAND, DyeColor.RED, DyeColor.YELLOW);
            SubmitTownColorsPayload.STREAM_CODEC.encode(buffer, submit);
            assertEquals(submit, SubmitTownColorsPayload.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test void shiftUsingLedgerInAirPassesWithoutOpeningGlobalColorFlow() {
        TownLedgerItem ledgerItem = new TownLedgerItem(new Item.Properties());
        ItemStack stack = new ItemStack(ledgerItem);
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
        when(player.isShiftKeyDown()).thenReturn(true);

        var result = ledgerItem.use(level, player, InteractionHand.MAIN_HAND);

        assertEquals(InteractionResult.PASS, result.getResult());
        assertSame(stack, result.getObject());
    }

    private ServerPlayer player(boolean creative) {
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(player.serverLevel()).thenReturn(level);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        Abilities abilities = new Abilities();
        abilities.instabuild = creative;
        when(player.getAbilities()).thenReturn(abilities);
        Inventory inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    @Test void deliversIntoAvailableSlotWithoutDropping() {
        ServerPlayer player = player(false);
        ItemStack ledger = new ItemStack(Items.WRITTEN_BOOK);
        assertTrue(TownLedgerItem.deliver(player, ledger));
        assertTrue(player.getInventory().getItem(0).is(Items.WRITTEN_BOOK));
        assertTrue(ledger.isEmpty());
        verify(player.serverLevel(), never()).addFreshEntity(any());
    }

    @Test void fullCreativeInventoryDropsInsteadOfSilentlyDeletingLedger() {
        ServerPlayer player = player(true);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        when(player.serverLevel().addFreshEntity(any())).thenReturn(true);
        ItemStack ledger = new ItemStack(Items.WRITTEN_BOOK);
        assertTrue(TownLedgerItem.deliver(player, ledger));
        var dropped = ArgumentCaptor.forClass(net.minecraft.world.entity.Entity.class);
        verify(player.serverLevel()).addFreshEntity(dropped.capture());
        assertInstanceOf(ItemEntity.class, dropped.getValue());
        assertTrue(((ItemEntity)dropped.getValue()).getItem().is(Items.WRITTEN_BOOK));
        assertEquals(1, ((ItemEntity)dropped.getValue()).getItem().getCount());
        assertTrue(ledger.isEmpty());
    }

    @Test void rejectedDropReportsFailureAndDoesNotDiscardStack() {
        ServerPlayer player = player(false);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        when(player.serverLevel().addFreshEntity(any())).thenReturn(false);
        ItemStack ledger = new ItemStack(Items.WRITTEN_BOOK);
        assertFalse(TownLedgerItem.deliver(player, ledger));
        assertEquals(1, ledger.getCount());
    }
}
