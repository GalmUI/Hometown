package dev.conner.hometown.food;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Explicit loaded-block-entity scan. Never opens, unpacks, extracts, or modifies inventories. */
public final class FoodScanner {
    public static final TagKey<Block> FOOD_STORAGE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("hometown","food_storage"));
    public static final TagKey<Item> FOOD_EXCLUDED = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("hometown","food_excluded"));
    public static final int MAX_BLOCK_ENTITIES = 32768, MAX_STORAGE = 4096, MAX_SLOTS = 131072;
    public record Limits(int blockEntities, int storageContainers, int inventorySlots) {
        public static final Limits DEFAULT = new Limits(MAX_BLOCK_ENTITIES,MAX_STORAGE,MAX_SLOTS);
        public Limits { if (blockEntities < 0 || storageContainers < 0 || inventorySlots < 0) throw new IllegalArgumentException("Negative Food limit"); }
    }
    private FoodScanner() {}
    private record Store(BlockPos pos, BlockState state, BlockEntity entity) {}
    public static FoodSnapshot scan(ServerLevel level, Settlement town, SettlementStats stats, int vertical, FoodRules rules) {
        return observe(level,town,stats,vertical,rules,Limits.DEFAULT).reserves();
    }
    public static FoodSnapshot scan(ServerLevel level, Settlement town, SettlementStats stats, int vertical, FoodRules rules, Limits limits) {
        return observe(level,town,stats,vertical,rules,limits).reserves();
    }
    public static FoodObservation observe(ServerLevel level, Settlement town, SettlementStats stats, int vertical, FoodRules rules) {
        return observe(level,town,stats,vertical,rules,Limits.DEFAULT);
    }
    public static FoodObservation observe(ServerLevel level, Settlement town, SettlementStats stats, int vertical, FoodRules rules, Limits limits) {
        if (level == null || town == null || stats == null)
            return new FoodObservation(FoodSnapshot.unavailable(stats == null ? 0 : stats.population()),List.of());
        Session scan = new Session(stats,limits);
        try { scan.read(level,town,vertical); }
        catch (RuntimeException exception) {
            scan.reasons.add(FoodScanReason.INTERNAL_ERROR);
            Hometown.LOGGER.warn("Food scan failed for Hometown [{}]; retaining known observations",town.id(),exception);
        }
        return scan.finish(rules);
    }
    private static final class Session {
        final SettlementStats stats;
        final Limits limits;
        final Set<FoodScanReason> reasons = EnumSet.noneOf(FoodScanReason.class);
        final Map<BlockPos,Store> stores = new TreeMap<>();
        final Set<BlockPos> containingFood = new HashSet<>();
        final Set<Item> types = new HashSet<>();
        final List<FoodStackFact> facts = new ArrayList<>();
        int considered, loaded, unavailable, inspected, found, scanned, duplicates, storageUnavailable, lootSkipped, slots, stacks;
        long nutrition;
        boolean meaningful, emptyArea;
        Session(SettlementStats stats, Limits limits) {
            this.stats=stats;this.limits=limits;
            if (stats.availability()!=SettlementStats.Availability.COMPLETE) reasons.add(FoodScanReason.POPULATION_INCOMPLETE);
        }
        void read(ServerLevel level, Settlement town, int vertical) {
            var bell=town.bellPosition();int radius=town.radius();
            AABB bounds=SettlementQueries.bounds(bell,radius,vertical);
            var chunks=new ArrayList<net.minecraft.world.level.chunk.LevelChunk>();
            // Count the whole considered area even if later inventory work hits a limit.
            for(int x=(bell.getX()-radius)>>4;x<=(bell.getX()+radius)>>4;x++) {
                for(int z=(bell.getZ()-radius)>>4;z<=(bell.getZ()+radius)>>4;z++) {
                    considered++;
                    try {
                        var chunk=level.getChunkSource().getChunkNow(x,z);
                        if(chunk==null) { unavailable++;reasons.add(FoodScanReason.UNLOADED_CHUNKS); }
                        else { loaded++;chunks.add(chunk); }
                    } catch(RuntimeException exception) { unavailable++;reasons.add(FoodScanReason.INTERNAL_ERROR); }
                }
            }
            collection:
            for(var chunk:chunks) {
                boolean chunkKnown=true;int storesBefore=found;
                try {
                    for(BlockPos pos:new TreeSet<>(chunk.getBlockEntitiesPos())) {
                        if(inspected>=limits.blockEntities()) { reasons.add(FoodScanReason.SCAN_LIMIT_REACHED);break collection; }
                        inspected++;
                        if(!bounds.contains(Vec3.atCenterOf(pos))) continue;
                        try {
                            var state=chunk.getBlockState(pos);
                            if(!state.is(FOOD_STORAGE)) continue;
                            found++;
                            if(stores.size()>=limits.storageContainers()) { reasons.add(FoodScanReason.SCAN_LIMIT_REACHED);break collection; }
                            stores.put(pos.immutable(),new Store(pos.immutable(),state,chunk.getBlockEntities().get(pos)));
                        } catch(RuntimeException exception) { chunkKnown=false;reasons.add(FoodScanReason.INTERNAL_ERROR); }
                    }
                    // A safely inspected empty area is meaningful even with zero food.
                    if(chunkKnown && storesBefore==found) emptyArea=true;
                } catch(RuntimeException exception) { reasons.add(FoodScanReason.INTERNAL_ERROR); }
            }
            Set<Container> visited=Collections.newSetFromMap(new IdentityHashMap<>());
            inventories:
            for(Store store:stores.values()) {
                try {
                    var entity=store.entity();
                    if(entity==null || entity.isRemoved() || !(entity instanceof Container inventory)) {
                        storageUnavailable++;reasons.add(FoodScanReason.STORAGE_UNAVAILABLE);continue;
                    }
                    if(!visited.add(inventory)) { duplicates++;continue; }
                    if(entity instanceof RandomizableContainer loot && loot.getLootTable()!=null) {
                        lootSkipped++;storageUnavailable++;reasons.add(FoodScanReason.LOOT_NOT_GENERATED);continue;
                    }
                    int size=inventory.getContainerSize();
                    if(size<0) { storageUnavailable++;reasons.add(FoodScanReason.STORAGE_UNAVAILABLE);continue; }
                    for(int slot=0;slot<size;slot++) {
                        if(slots>=limits.inventorySlots()) { reasons.add(FoodScanReason.SCAN_LIMIT_REACHED);break inventories; }
                        slots++;
                        var stack=inventory.getItem(slot);
                        if(stack==null) { storageUnavailable++;reasons.add(FoodScanReason.STORAGE_UNAVAILABLE);continue inventories; }
                        meaningful=true;
                        if(stack.isEmpty() || stack.is(FOOD_EXCLUDED)) continue;
                        var food=stack.get(DataComponents.FOOD);
                        if(food==null || food.nutrition()<=0) continue;
                        long stackNutrition=Math.multiplyExact((long)food.nutrition(),stack.getCount());
                        long next=Math.addExact(nutrition,stackNutrition);
                        // Commit only the successfully read stack; failures retain all earlier stacks.
                        nutrition=next;stacks++;types.add(stack.getItem());containingFood.add(inventoryKey(store,stores));
                        facts.add(new FoodStackFact(BuiltInRegistries.ITEM.getKey(stack.getItem()),stackNutrition));
                    }
                    meaningful=true;scanned++;
                } catch(RuntimeException exception) {
                    storageUnavailable++;reasons.add(FoodScanReason.INTERNAL_ERROR);
                    Hometown.LOGGER.warn("Unable to read Food storage at [{}]; preserving earlier stacks",store.pos(),exception);
                }
            }
        }
        FoodObservation finish(FoodRules rules) {
            boolean knownData=meaningful || (emptyArea && found==0 && !reasons.contains(FoodScanReason.SCAN_LIMIT_REACHED));
            FoodScanStatus status=reasons.isEmpty()?FoodScanStatus.COMPLETE:knownData?FoodScanStatus.PARTIAL:FoodScanStatus.UNAVAILABLE;
            var diagnostics=new FoodScanDiagnostics(reasons,considered,loaded,unavailable,inspected,found,scanned,duplicates,
                    storageUnavailable,lootSkipped,slots,limits,stats.availability()==SettlementStats.Availability.COMPLETE);
            return new FoodObservation(rules.snapshot(stats.population(),containingFood.size(),stacks,types.size(),nutrition,status,diagnostics),facts);
        }
    }
    private static BlockPos inventoryKey(Store store, Map<BlockPos,Store> stores) {
        // Count each vanilla physical half's local 27 slots, never the combined 54-slot wrapper.
        // Pair only halves inside the town: outside storage is never included.
        if (store.entity() instanceof ChestBlockEntity && store.state().getBlock() instanceof ChestBlock
                && store.state().getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
            BlockPos partnerPos=store.pos().relative(ChestBlock.getConnectedDirection(store.state()));
            Store partner=stores.get(partnerPos);
            if (partner!=null && partner.entity() instanceof ChestBlockEntity && partner.state().is(store.state().getBlock())
                    && partner.state().getValue(ChestBlock.FACING)==store.state().getValue(ChestBlock.FACING)
                    && partner.state().getValue(ChestBlock.TYPE)!=ChestType.SINGLE
                    && partnerPos.relative(ChestBlock.getConnectedDirection(partner.state())).equals(store.pos()))
                return store.pos().compareTo(partnerPos)<0 ? store.pos() : partnerPos;
        }
        return store.pos();
    }
}
