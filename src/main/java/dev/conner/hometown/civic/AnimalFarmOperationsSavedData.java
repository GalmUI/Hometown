package dev.conner.hometown.civic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Separate durable owner for Animal Farm operating policy and production history. */
public final class AnimalFarmOperationsSavedData extends SavedData {
    public static final String FILE_NAME = "hometown_animal_farm_operations";
    private static final int DATA_VERSION = 1;
    private final Map<UUID, AnimalFarmOperationsState> states = new LinkedHashMap<>();

    public static AnimalFarmOperationsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AnimalFarmOperationsSavedData::new,
                        AnimalFarmOperationsSavedData::load, null), FILE_NAME);
    }

    public AnimalFarmOperationsState state(UUID settlementId) {
        return states.getOrDefault(settlementId, AnimalFarmOperationsState.defaults());
    }

    public Optional<AnimalFarmOperationsState> get(UUID settlementId) {
        return Optional.ofNullable(states.get(settlementId));
    }

    public boolean update(UUID settlementId, UnaryOperator<AnimalFarmOperationsState> update) {
        AnimalFarmOperationsState current = state(settlementId);
        AnimalFarmOperationsState next = java.util.Objects.requireNonNull(update.apply(current));
        if (next.equals(current)) return false;
        states.put(settlementId, next);
        setDirty();
        return true;
    }

    /**
     * A terminal cycle is immutable for its day. Retryable failure states performed no mutation and
     * may therefore be replaced later the same day (or after a test/admin clock rewind).
     */
    public boolean recordCycle(UUID settlementId, AnimalFarmCycleResult result) {
        AnimalFarmOperationsState current = state(settlementId);
        AnimalFarmCycleResult existing = current.lastCycle();
        if (existing != null) {
            if (existing.terminalForDay() && existing.day() >= result.day()) return false;
            if (existing.retryable() && result.retryable() && sameAttemptState(existing, result)) return false;
        }
        states.put(settlementId, current.withLastCycle(result));
        setDirty();
        return true;
    }

    private static boolean sameAttemptState(AnimalFarmCycleResult a, AnimalFarmCycleResult b) {
        return a.day() == b.day() && a.outcome() == b.outcome()
                && a.cowAdults() == b.cowAdults() && a.cowYoung() == b.cowYoung()
                && a.pigAdults() == b.pigAdults() && a.pigYoung() == b.pigYoung();
    }

    public static AnimalFarmOperationsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("DataVersion") != DATA_VERSION || !tag.contains("Farms", Tag.TAG_LIST)) {
            throw new IllegalStateException("Unsupported or damaged Hometown Animal Farm operations data");
        }
        AnimalFarmOperationsSavedData data = new AnimalFarmOperationsSavedData();
        ListTag list = tag.getList("Farms", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("SettlementId") || !entry.contains("State", Tag.TAG_COMPOUND)) {
                throw new IllegalStateException("Damaged Hometown Animal Farm operations entry");
            }
            UUID id = entry.getUUID("SettlementId");
            AnimalFarmOperationsState state;
            try {
                state = AnimalFarmOperationsState.fromTag(entry.getCompound("State"));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Damaged Hometown Animal Farm operations state", ex);
            }
            if (data.states.putIfAbsent(id, state) != null) {
                throw new IllegalStateException("Duplicate Hometown Animal Farm operations owner");
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
        tag.put("Farms", list);
        return tag;
    }
}
