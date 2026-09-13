package dev.conner.hometown.settlement;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.civic.*;
import dev.conner.hometown.history.*;
import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** One save-wide store, attached to the overworld even for towns in other dimensions. */
public final class HometownSavedData extends SavedData {
    public static final String FILE_NAME = "hometown_settlements";
    private static final int DATA_VERSION = 3;
    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();
    private final Map<UUID, HistoryTownState> history = new LinkedHashMap<>();
    private final Map<UUID, TownCivicState> civic = new LinkedHashMap<>();

    public static HometownSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HometownSavedData::new, HometownSavedData::load, null), FILE_NAME);
    }

    public Optional<Settlement> getSettlement(UUID id) { return Optional.ofNullable(settlements.get(id)); }
    public Collection<Settlement> all() { return Collections.unmodifiableCollection(settlements.values()); }
    public Optional<HistoryTownState> getHistory(UUID id){return Optional.ofNullable(history.get(id));}
    public HistoryTownState history(UUID id){
        if(!settlements.containsKey(id))throw new IllegalArgumentException("Unknown Hometown history owner");
        return history.computeIfAbsent(id,ignored->new HistoryTownState());
    }
    public Optional<TownCivicState> getCivicState(UUID id){return Optional.ofNullable(civic.get(id));}
    public TownCivicState civicState(UUID id){
        if(!settlements.containsKey(id))throw new IllegalArgumentException("Unknown Hometown civic owner");
        TownCivicState state=civic.get(id);
        if(state==null)throw new IllegalStateException("Missing Hometown civic state");
        return state;
    }
    public boolean configureColors(UUID id, DyeColor primary, DyeColor secondary){
        return updateCivicState(id,state->state.withColors(primary,secondary));
    }
    public boolean updateCivicState(UUID id, UnaryOperator<TownCivicState> update){
        Objects.requireNonNull(update);
        TownCivicState current=civicState(id);
        TownCivicState next=Objects.requireNonNull(update.apply(current));
        validateCivicOwner(id,next);
        if(next.equals(current))return false;
        civic.put(id,next);setDirty();return true;
    }
    private static void validateCivicOwner(UUID id,TownCivicState state){
        for(var marker:state.facilities().values())if(!marker.settlementId().equals(id))
            throw new IllegalArgumentException("Foreign civic facility owner");
    }
    public Optional<Settlement> findByBell(ResourceKey<Level> dimension, BlockPos pos) {
        return settlements.values().stream().filter(s -> s.dimension().equals(dimension) && s.bellPosition().equals(pos)).findFirst();
    }
    public Optional<Settlement> findByName(String name) {
        String key = TownNames.key(name);
        return settlements.values().stream().filter(s -> TownNames.key(s.name()).equals(key)).findFirst();
    }
    public Optional<Settlement> findSettlementContaining(ResourceKey<Level> dimension, BlockPos pos) {
        return settlements.values().stream().filter(s -> s.contains(dimension, pos)).findFirst();
    }
    public List<Settlement> getSettlementsInDimension(ResourceKey<Level> dimension) {
        return settlements.values().stream().filter(s -> s.dimension().equals(dimension)).toList();
    }

    public void addSettlement(Settlement settlement) {
        insertUnique(settlement);
        history.put(settlement.id(), foundingHistory(settlement));
        civic.put(settlement.id(),TownCivicState.empty());
        setDirty();
    }

    /** New R3 founding commits identity and validated color identity in one SavedData mutation. */
    public void addSettlement(Settlement settlement, DyeColor primary, DyeColor secondary) {
        TownCivicState initialCivic = TownCivicState.empty().withColors(primary, secondary);
        insertUnique(settlement);
        history.put(settlement.id(), foundingHistory(settlement));
        civic.put(settlement.id(), initialCivic);
        setDirty();
    }

    private void insertUnique(Settlement settlement) {
        if (settlements.containsKey(settlement.id()) || findByName(settlement.name()).isPresent()
                || findByBell(settlement.dimension(), settlement.bellPosition()).isPresent()) {
            throw new IllegalArgumentException("Duplicate settlement identity, name or bell");
        }
        settlements.put(settlement.id(), settlement);
    }

    public Optional<Settlement> removeSettlement(UUID id) {
        Settlement removed = settlements.remove(id);
        if (removed != null) {
            history.remove(id);
            civic.remove(id);
            setDirty();
            Hometown.LOGGER.info("Removed Hometown '{}' [{}]", removed.name(), removed.id());
        }
        return Optional.ofNullable(removed);
    }

    public static HometownSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int version=tag.getInt("DataVersion");
        if ((version != 1 && version != 2 && version != DATA_VERSION) || !tag.contains("Settlements", Tag.TAG_LIST)) {
            throw new IllegalStateException("Unsupported or damaged Hometown SavedData; restore a world backup");
        }
        HometownSavedData data = new HometownSavedData();
        ListTag list = tag.getList("Settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) data.insertUnique(Settlement.fromTag(list.getCompound(i)));
        boolean migrated=version<DATA_VERSION;
        if(version==1){
            // Revision-1's History page was an authoritative presentation of these persisted founding fields.
            // Convert that known record; do not infer any derived events or baselines.
            for(var settlement:data.settlements.values())data.history.put(settlement.id(),foundingHistory(settlement));
        } else if(tag.contains("History",Tag.TAG_LIST)) {
            var histories=tag.getList("History",Tag.TAG_COMPOUND);var seen=new HashSet<UUID>();
            for(int i=0;i<histories.size();i++){
                var entry=histories.getCompound(i);UUID id=entry.getUUID("SettlementId");
                if(!data.settlements.containsKey(id)||!seen.add(id))throw new IllegalStateException("Damaged Hometown History owner");
                data.history.put(id,HistoryTownState.fromTag(entry.getCompound("State"),id));
            }
        }
        // Preserve the R2 behavior for absent History state; never fabricate a missing v2/v3 founding event.
        for(var id:data.settlements.keySet())if(!data.history.containsKey(id)){data.history.put(id,new HistoryTownState());migrated=true;}

        if(version<3){
            for(var id:data.settlements.keySet())data.civic.put(id,TownCivicState.empty());
        } else {
            if(!tag.contains("Civic",Tag.TAG_LIST))throw new IllegalStateException("Missing Hometown civic state");
            var civicList=tag.getList("Civic",Tag.TAG_COMPOUND);var seen=new HashSet<UUID>();
            for(int i=0;i<civicList.size();i++){
                var entry=civicList.getCompound(i);
                if(!entry.hasUUID("SettlementId")||!entry.contains("State",Tag.TAG_COMPOUND))
                    throw new IllegalStateException("Damaged Hometown civic entry");
                UUID id=entry.getUUID("SettlementId");
                if(!data.settlements.containsKey(id)||!seen.add(id))throw new IllegalStateException("Damaged Hometown civic owner");
                try{data.civic.put(id,TownCivicState.fromTag(entry.getCompound("State"),id));}
                catch(IllegalArgumentException ex){throw new IllegalStateException("Damaged Hometown civic state",ex);}
            }
            if(data.civic.size()!=data.settlements.size())throw new IllegalStateException("Missing Hometown civic owner");
        }
        if(migrated)data.setDirty();
        Hometown.LOGGER.info("Loaded {} Hometowns from SavedData version {}{}", data.settlements.size(),version,migrated?" (migrated)":"");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        settlements.values().forEach(s -> list.add(s.toTag()));
        tag.put("Settlements", list);
        var histories=new ListTag();
        settlements.keySet().forEach(id->{var entry=new CompoundTag();entry.putUUID("SettlementId",id);entry.put("State",history(id).toTag());histories.add(entry);});
        tag.put("History",histories);
        var civicList=new ListTag();
        settlements.keySet().forEach(id->{var entry=new CompoundTag();entry.putUUID("SettlementId",id);entry.put("State",civicState(id).toTag());civicList.add(entry);});
        tag.put("Civic",civicList);
        return tag;
    }

    private static HistoryTownState foundingHistory(Settlement settlement){
        var state=new HistoryTownState();
        var args=new LinkedHashMap<String,HistoryArgument>();
        args.put("founderUuid",HistoryArgument.uuid(settlement.founderUuid()));
        args.put("founderName",HistoryArgument.string(settlement.founderName()));
        args.put("townName",HistoryArgument.string(settlement.name()));
        args.put("foundedGameTime",HistoryArgument.longValue(settlement.foundedGameTime()));
        args.put("foundedDay",HistoryArgument.longValue(Math.floorDiv(settlement.foundedGameTime(),24000L)));
        args.put("bellPosition",HistoryArgument.blockPos(settlement.bellPosition()));
        args.put("dimension",HistoryArgument.resource(settlement.dimension().location().toString()));
        state.append(settlement.id(),HistoryEvent.Type.TOWN_FOUNDED,settlement.foundedGameTime(),0,args);
        return state;
    }
}
