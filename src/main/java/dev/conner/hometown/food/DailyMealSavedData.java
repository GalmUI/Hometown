package dev.conner.hometown.food;

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

/** Separate durable owner for Daily Meal state so R3 food operations can evolve independently. */
public final class DailyMealSavedData extends SavedData {
    public static final String FILE_NAME = "hometown_daily_meals";
    private static final int DATA_VERSION = 1;
    private final Map<UUID, DailyMealState> states = new LinkedHashMap<>();

    public static DailyMealSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DailyMealSavedData::new, DailyMealSavedData::load, null), FILE_NAME);
    }

    public Optional<DailyMealState> get(UUID settlementId) {
        return Optional.ofNullable(states.get(settlementId));
    }

    /**
     * Commits at most one result for a given-or-older Minecraft day. Returning false means the
     * town already has an equal/newer meal result and callers must not consume anything again.
     */
    public boolean record(UUID settlementId, DailyMealState state) {
        DailyMealState existing = states.get(settlementId);
        if (existing != null && existing.day() >= state.day()) return false;
        states.put(settlementId, state);
        setDirty();
        return true;
    }

    public boolean remove(UUID settlementId) {
        if (states.remove(settlementId) == null) return false;
        setDirty();
        return true;
    }

    public static DailyMealSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("DataVersion") != DATA_VERSION || !tag.contains("Meals", Tag.TAG_LIST)) {
            throw new IllegalStateException("Unsupported or damaged Hometown Daily Meal SavedData");
        }
        DailyMealSavedData data = new DailyMealSavedData();
        ListTag list = tag.getList("Meals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("SettlementId") || !entry.contains("State", Tag.TAG_COMPOUND)) {
                throw new IllegalStateException("Damaged Hometown Daily Meal entry");
            }
            UUID id = entry.getUUID("SettlementId");
            if (data.states.putIfAbsent(id, DailyMealState.fromTag(entry.getCompound("State"))) != null) {
                throw new IllegalStateException("Duplicate Hometown Daily Meal owner");
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
        tag.put("Meals", list);
        return tag;
    }
}
