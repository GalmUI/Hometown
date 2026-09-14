package dev.conner.hometown.settlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Save-wide trusted complete census cache, independent from civic progression and meal history. */
public final class TownCensusSavedData extends SavedData {
    public static final String FILE_NAME = "hometown_town_census";
    private static final int DATA_VERSION = 1;
    private final Map<UUID, TownCensusState> states = new LinkedHashMap<>();

    public static TownCensusSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TownCensusSavedData::new, TownCensusSavedData::load, null), FILE_NAME);
    }

    public Optional<TownCensusState> get(UUID settlementId) {
        return Optional.ofNullable(states.get(settlementId));
    }

    /** Newer complete censuses replace older ones; partial observations never enter this owner. */
    public boolean record(UUID settlementId, TownCensusState state) {
        TownCensusState existing = states.get(settlementId);
        if (existing != null && existing.observedGameTime() > state.observedGameTime()) return false;
        if (state.equals(existing)) return false;
        states.put(settlementId, state);
        setDirty();
        return true;
    }

    public boolean remove(UUID settlementId) {
        if (states.remove(settlementId) == null) return false;
        setDirty();
        return true;
    }

    public static TownCensusSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("DataVersion") != DATA_VERSION || !tag.contains("Censuses", Tag.TAG_LIST)) {
            throw new IllegalStateException("Unsupported or damaged Hometown Town Census SavedData");
        }
        TownCensusSavedData data = new TownCensusSavedData();
        ListTag list = tag.getList("Censuses", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("SettlementId") || !entry.contains("State", Tag.TAG_COMPOUND)) {
                throw new IllegalStateException("Damaged Hometown Town Census entry");
            }
            UUID id = entry.getUUID("SettlementId");
            TownCensusState state;
            try {
                state = TownCensusState.fromTag(entry.getCompound("State"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Damaged Hometown Town Census state", exception);
            }
            if (data.states.putIfAbsent(id, state) != null) {
                throw new IllegalStateException("Duplicate Hometown Town Census owner");
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        states.forEach((id, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("SettlementId", id);
            entry.put("State", state.toTag());
            list.add(entry);
        });
        tag.put("Censuses", list);
        return tag;
    }
}
