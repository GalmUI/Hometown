package dev.conner.hometown.settlement;

import com.mojang.authlib.GameProfile;
import dev.conner.hometown.item.TownLedgerItem;
import dev.conner.hometown.network.OpenTownNamingPayload;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettlementManagerTest {
    private static final DyeColor PRIMARY = DyeColor.BLUE;
    private static final DyeColor SECONDARY = DyeColor.WHITE;
    private MinecraftServer server;
    private ServerLevel level;
    private SettlementManager manager;
    private HometownSavedData data;
    private MockedStatic<HometownSavedData> stores;
    private MockedStatic<SettlementValidator.Rules> rules;
    private MockedStatic<TownLedgerItem> ledgers;
    private final BlockPos bell = new BlockPos(0, 64, 0);

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() {
        server = mock(MinecraftServer.class);
        level = mock(ServerLevel.class);
        when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(level);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getGameTime()).thenReturn(100L);
        when(level.getBlockState(bell)).thenReturn(Blocks.BELL.defaultBlockState());
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkNow(0, 0)).thenReturn(mock(LevelChunk.class));
        data = new HometownSavedData();
        stores = mockStatic(HometownSavedData.class);
        stores.when(() -> HometownSavedData.get(server)).thenReturn(data);
        rules = mockStatic(SettlementValidator.Rules.class);
        rules.when(SettlementValidator.Rules::current).thenReturn(new SettlementValidator.Rules(64, 32, 0, 0, true, true));
        ledgers = mockStatic(TownLedgerItem.class);
        ledgers.when(() -> TownLedgerItem.create(any())).thenAnswer(call -> new ItemStack(Items.WRITTEN_BOOK));
        ledgers.when(() -> TownLedgerItem.deliver(any(), any())).thenAnswer(call -> {
            // Delivery must never precede creation, and the Book must still be intact here.
            assertEquals(1, data.all().size());
            var stored = data.all().iterator().next();
            assertTrue(data.civicState(stored.id()).colorsConfigured(), "new towns commit colors before Ledger delivery");
            ServerPlayer player = call.getArgument(0);
            assertEquals(3, player.getInventory().getItem(0).getCount());
            return true;
        });
        manager = SettlementManager.get(server);
    }
    @AfterEach void teardown() {
        if (ledgers != null) ledgers.close();
        if (rules != null) rules.close();
        if (stores != null) stores.close();
        SettlementManager.release(server);
    }

    private ServerPlayer player(String name) {
        ServerPlayer player = mock(ServerPlayer.class);
        UUID id = UUID.randomUUID();
        when(player.getUUID()).thenReturn(id);
        when(player.getGameProfile()).thenReturn(new GameProfile(id, name));
        when(player.serverLevel()).thenReturn(level);
        when(player.isAlive()).thenReturn(true);
        when(player.isShiftKeyDown()).thenReturn(true);
        Inventory inventory = new Inventory(player);
        inventory.setItem(0, new ItemStack(Items.BOOK, 3));
        when(player.getInventory()).thenReturn(inventory);
        when(player.getMainHandItem()).thenAnswer(call -> inventory.getItem(0));
        when(player.getAbilities()).thenReturn(new Abilities());
        player.containerMenu = mock(AbstractContainerMenu.class);
        return player;
    }

    private OpenTownNamingPayload begin(ServerPlayer player) { return manager.beginFounding(player, bell).orElseThrow(); }
    private Optional<Settlement> create(ServerPlayer player, BlockPos position, long nonce, String name) {
        return manager.createSettlement(player, position, nonce, name, PRIMARY, SECONDARY);
    }
    private Optional<Settlement> create(ServerPlayer player, OpenTownNamingPayload attempt, String name) {
        return create(player, bell, attempt.nonce(), name);
    }

    @Test void commitsColorsThenDeliversThenConsumesExactlyOneBookAndRejectsReplay() {
        ServerPlayer player = player("Conner");
        var attempt = begin(player);
        Settlement town = create(player, attempt, " Oakridge ").orElseThrow();
        assertEquals("Oakridge", town.name());
        assertEquals(2, player.getInventory().getItem(0).getCount());
        assertEquals(town, data.getSettlement(town.id()).orElseThrow());
        var civic = data.civicState(town.id());
        assertEquals(PRIMARY, civic.primaryColor().orElseThrow());
        assertEquals(SECONDARY, civic.secondaryColor().orElseThrow());
        assertTrue(create(player, attempt, "Other").isEmpty());
        assertEquals(1, data.all().size());
        assertEquals(2, player.getInventory().getItem(0).getCount());
    }

    @Test void samePrimaryAndSecondaryCannotCreatePartialTown() {
        ServerPlayer player = player("Conner");
        var attempt = begin(player);
        assertTrue(manager.createSettlement(player, bell, attempt.nonce(), "Oakridge", DyeColor.RED, DyeColor.RED).isEmpty());
        assertTrue(data.all().isEmpty());
        assertEquals(3, player.getInventory().getItem(0).getCount());
    }

    @Test void concurrentAttemptsAtSameBellCreateOneTownAndOnlyWinnerPays() {
        ServerPlayer a = player("Conner"), b = player("Alex");
        var first = begin(a);
        var second = begin(b);
        assertTrue(create(a, first, "Oakridge").isPresent());
        assertTrue(create(b, second, "Other").isEmpty());
        assertEquals(1, data.all().size());
        assertEquals(2, a.getInventory().getItem(0).getCount());
        assertEquals(3, b.getInventory().getItem(0).getCount());
    }

    @Test void rejectsUnsolicitedWrongNonceWrongPositionAndCancelledSessions() {
        ServerPlayer player = player("Conner");
        assertTrue(create(player, bell, 0, "Forged").isEmpty());
        var attempt = begin(player);
        assertTrue(create(player, bell, attempt.nonce() + 1, "Forged").isEmpty());
        when(level.getGameTime()).thenReturn(120L);
        attempt = begin(player);
        assertTrue(create(player, bell.offset(1, 0, 0), attempt.nonce(), "Forged").isEmpty());
        when(level.getGameTime()).thenReturn(140L);
        attempt = begin(player);
        manager.cancel(player.getUUID());
        assertTrue(create(player, attempt, "Cancelled").isEmpty());
        assertTrue(data.all().isEmpty());
        assertEquals(3, player.getInventory().getItem(0).getCount());
    }

    @Test void brokenBellOrMissingBookAfterScreenOpensPreventsCommit() {
        ServerPlayer a = player("Conner"), b = player("Alex");
        var first = begin(a);
        var second = begin(b);
        a.getInventory().setItem(0, ItemStack.EMPTY);
        assertTrue(create(a, first, "No book").isEmpty());
        when(level.getBlockState(bell)).thenReturn(Blocks.AIR.defaultBlockState());
        assertTrue(create(b, second, "No bell").isEmpty());
        assertTrue(data.all().isEmpty());
        assertEquals(3, b.getInventory().getItem(0).getCount());
    }

    @Test void distanceDimensionExpiryAndInvalidNameAreRechecked() {
        ServerPlayer a = player("A"), b = player("B"), c = player("C"), d = player("D");
        var first = begin(a); var second = begin(b); var third = begin(c); var fourth = begin(d);
        when(a.distanceToSqr(any(Vec3.class))).thenReturn(65.0);
        assertTrue(create(a, first, "Far").isEmpty());
        ServerLevel nether = mock(ServerLevel.class);
        when(nether.dimension()).thenReturn(Level.NETHER);
        when(b.serverLevel()).thenReturn(nether);
        assertTrue(create(b, second, "Wrong dimension").isEmpty());
        assertTrue(create(c, third, "§cInvalid").isEmpty());
        when(level.getGameTime()).thenReturn(2501L);
        assertTrue(create(d, fourth, "Expired").isEmpty());
        assertTrue(data.all().isEmpty());
        for (ServerPlayer player : new ServerPlayer[]{a, b, c, d}) assertEquals(3, player.getInventory().getItem(0).getCount());
    }

    @Test void failedLedgerDeliveryRollsBackTownColorsAndKeepsBook() {
        ServerPlayer player = player("Conner");
        var attempt = begin(player);
        ledgers.when(() -> TownLedgerItem.deliver(any(), any())).thenReturn(false);
        assertTrue(create(player, attempt, "Oakridge").isEmpty());
        assertTrue(data.all().isEmpty());
        assertEquals(3, player.getInventory().getItem(0).getCount());
    }

    @Test void creativeFoundingRetainsBook() {
        ServerPlayer player = player("Conner");
        player.getAbilities().instabuild = true;
        var attempt = begin(player);
        assertTrue(create(player, attempt, "Oakridge").isPresent());
        assertEquals(3, player.getInventory().getItem(0).getCount());
    }

    @Test void disabledBookConsumptionStillRequiresBookButKeepsIt() {
        rules.when(SettlementValidator.Rules::current).thenReturn(new SettlementValidator.Rules(64, 32, 0, 0, true, false));
        ServerPlayer player = player("Conner");
        var attempt = begin(player);
        assertTrue(create(player, attempt, "Oakridge").isPresent());
        assertEquals(3, player.getInventory().getItem(0).getCount());
    }

    @Test void namesAreReservedSaveWideAtFinalSubmission() {
        ServerPlayer player = player("Conner");
        var attempt = begin(player);
        data.addSettlement(new Settlement(UUID.randomUUID(), "OAKRIDGE", Level.NETHER, bell, 64,
                UUID.randomUUID(), "Alex", 0));
        assertTrue(create(player, attempt, "Oakridge").isEmpty());
        assertEquals(1, data.all().size());
        assertEquals(3, player.getInventory().getItem(0).getCount());
    }

    @Test void residentRequirementsAreRepeatedAtSubmission() {
        ServerPlayer player = player("Conner");
        var attempt = begin(player);
        rules.when(SettlementValidator.Rules::current).thenReturn(new SettlementValidator.Rules(64, 32, 2, 0, true, true));
        assertTrue(create(player, attempt, "Oakridge").isEmpty());
        assertTrue(data.all().isEmpty());
        assertEquals(3, player.getInventory().getItem(0).getCount());
    }
}
