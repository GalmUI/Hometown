package dev.conner.hometown.settlement;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettlementValidatorTest {
    private ServerPlayer player;
    private ServerLevel level;
    private ServerChunkCache chunks;
    private HometownSavedData data;
    private Inventory inventory;
    private final BlockPos bell = new BlockPos(0, 64, 0);
    private final SettlementValidator.Rules rules = new SettlementValidator.Rules(64, 32, 2, 2, true, true);

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() {
        player = mock(ServerPlayer.class);
        level = mock(ServerLevel.class);
        chunks = mock(ServerChunkCache.class);
        inventory = new Inventory(player);
        inventory.setItem(0, new ItemStack(Items.BOOK, 3));
        when(player.getInventory()).thenReturn(inventory);
        when(player.serverLevel()).thenReturn(level);
        when(player.isAlive()).thenReturn(true);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getChunkSource()).thenReturn(chunks);
        // Only the anchor chunk is loaded: all neighboring chunks must be skipped.
        when(chunks.getChunkNow(0, 0)).thenReturn(mock(LevelChunk.class));
        when(level.getBlockState(bell)).thenReturn(Blocks.BELL.defaultBlockState());
        data = new HometownSavedData();
    }

    @Test void rejectsMissingBookBellAndDistantPlayersBeforeScanning() {
        inventory.setItem(0, new ItemStack(Items.WRITABLE_BOOK));
        assertTrue(SettlementValidator.validate(player, bell, data, rules).isPresent());
        inventory.setItem(0, new ItemStack(Items.BOOK));
        when(player.distanceToSqr(any(Vec3.class))).thenReturn(64.01);
        assertTrue(SettlementValidator.validate(player, bell, data, rules).isPresent());
        when(player.distanceToSqr(any(Vec3.class))).thenReturn(64.0);
        when(level.getBlockState(bell)).thenReturn(Blocks.AIR.defaultBlockState());
        assertTrue(SettlementValidator.validate(player, bell, data, rules).isPresent());
        verify(level, never()).getPoiManager();
        assertEquals(1, inventory.getItem(0).getCount());
        assertTrue(data.all().isEmpty());
    }

    @Test void onlyLivingVanillaVillagersInsideBoundsCountIncludingBabies() {
        Villager adult = resident(new Vec3(2, 64, 2), true);
        Villager baby = resident(new Vec3(3, 64, 3), true);
        when(baby.isBaby()).thenReturn(true);
        Villager dead = resident(new Vec3(3, 64, 3), false);
        Villager outside = resident(new Vec3(65, 64, 3), true);
        when(level.getEntitiesOfClass(eq(Villager.class), any(), any())).thenAnswer(invocation -> {
            java.util.function.Predicate<Villager> predicate = invocation.getArgument(2);
            return List.of(adult, baby, dead, outside).stream().filter(predicate).toList();
        });
        var noBedRule = new SettlementValidator.Rules(64, 32, 2, 0, true, true);
        assertTrue(SettlementValidator.validate(player, bell, data, noBedRule).isEmpty());
        when(baby.isAlive()).thenReturn(false);
        var failure = SettlementValidator.validate(player, bell, data, noBedRule).orElseThrow();
        assertEquals("hometown.error.residents", ((net.minecraft.network.chat.contents.TranslatableContents)failure.getContents()).getKey());
    }

    private Villager resident(Vec3 pos, boolean alive) {
        Villager villager = mock(Villager.class);
        doReturn(EntityType.VILLAGER).when(villager).getType();
        when(villager.position()).thenReturn(pos);
        when(villager.blockPosition()).thenReturn(BlockPos.containing(pos));
        when(villager.isAlive()).thenReturn(alive);
        return villager;
    }

    @Test void homePoisRequireIntactVanillaBedsAndOnlyLoadedChunksAreQueried() {
        PoiManager pois = mock(PoiManager.class);
        when(level.getPoiManager()).thenReturn(pois);
        BlockPos bedA = new BlockPos(2, 64, 2);
        BlockPos bedB = new BlockPos(4, 64, 2);
        PoiRecord a = mock(PoiRecord.class), b = mock(PoiRecord.class);
        when(a.getPos()).thenReturn(bedA);
        when(b.getPos()).thenReturn(bedB);
        when(pois.getInChunk(any(), any(), eq(PoiManager.Occupancy.ANY))).thenAnswer(call -> Stream.of(a, b));
        var head = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD);
        for (BlockPos pos : List.of(bedA, bedB)) {
            when(level.getBlockState(pos)).thenReturn(head);
            when(level.getBlockState(pos.relative(head.getValue(BedBlock.FACING).getOpposite())))
                    .thenReturn(head.setValue(BedBlock.PART, BedPart.FOOT));
        }
        var noResidents = new SettlementValidator.Rules(64, 32, 0, 2, true, true);
        assertTrue(SettlementValidator.validate(player, bell, data, noResidents).isEmpty());
        verify(pois, times(1)).getInChunk(any(), eq(new net.minecraft.world.level.ChunkPos(0, 0)), eq(PoiManager.Occupancy.ANY));
        when(level.getBlockState(bedB)).thenReturn(Blocks.AIR.defaultBlockState());
        assertTrue(SettlementValidator.validate(player, bell, data, noResidents).isPresent());
        verify(chunks, never()).getChunk(anyInt(), anyInt(), any(), anyBoolean());
    }

    @Test void overlapCanBeDisabledButDuplicateAnchorCannot() {
        data.addSettlement(new Settlement(UUID.randomUUID(), "Oakridge", Level.OVERWORLD, bell, 64,
                UUID.randomUUID(), "Conner", 0));
        var relaxed = new SettlementValidator.Rules(64, 32, 0, 0, false, true);
        assertTrue(SettlementValidator.validate(player, bell, data, relaxed).isPresent());
        BlockPos nearby = bell.offset(3, 0, 0);
        when(level.getBlockState(nearby)).thenReturn(Blocks.BELL.defaultBlockState());
        assertTrue(SettlementValidator.validate(player, nearby, data, relaxed).isEmpty());
        assertTrue(SettlementValidator.validate(player, nearby, data, rules).isPresent());
    }
}
