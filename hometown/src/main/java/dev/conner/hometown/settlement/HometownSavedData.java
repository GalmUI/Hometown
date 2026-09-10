package dev.conner.hometown.settlement;

import dev.conner.hometown.Hometown;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** One save-wide store, attached to the overworld even for towns in other dimensions. */
public final class HometownSavedData extends SavedData {
    public static final String FILE_NAME = "hometown_settlements";
    private static final int DATA_VERSION = 1;
    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();

    public static HometownSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HometownSavedData::new, HometownSavedData::load, null), FILE_NAME);
    }

    public Optional<Settlement> getSettlement(UUID id) { return Optional.ofNullable(settlements.get(id)); }
    public Collection<Settlement> all() { return Collections.unmodifiableCollection(settlements.values()); }
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
            setDirty();
            Hometown.LOGGER.info("Removed Hometown '{}' [{}]", removed.name(), removed.id());
        }
        return Optional.ofNullable(removed);
    }

    public static HometownSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        // Validate schema and records before accepting persisted settlements.
        if (tag.getInt("DataVersion") != DATA_VERSION || !tag.contains("Settlements", Tag.TAG_LIST)) {
            throw new IllegalStateException("Unsupported or damaged Hometown SavedData; restore a world backup");
        }
        HometownSavedData data = new HometownSavedData();
        ListTag list = tag.getList("Settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) data.insertUnique(Settlement.fromTag(list.getCompound(i)));
        Hometown.LOGGER.info("Loaded {} Hometowns from SavedData", data.settlements.size());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        settlements.values().forEach(s -> list.add(s.toTag()));
        tag.put("Settlements", list);
        return tag;
    }
}
