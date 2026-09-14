package dev.conner.hometown.civic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Immutable persisted Animal Farm operating policy plus the latest completed work cycle. */
public record AnimalFarmOperationsState(
        Map<LivestockSpecies, LivestockPolicy> policies,
        AnimalFarmCycleResult lastCycle) {

    public AnimalFarmOperationsState {
        Objects.requireNonNull(policies);
        EnumMap<LivestockSpecies, LivestockPolicy> copy = new EnumMap<>(LivestockSpecies.class);
        copy.putAll(policies);
        for (LivestockSpecies species : LivestockSpecies.values()) {
            copy.putIfAbsent(species, LivestockPolicy.defaults());
        }
        policies = Collections.unmodifiableMap(copy);
    }

    public static AnimalFarmOperationsState defaults() {
        return new AnimalFarmOperationsState(Map.of(), null);
    }

    public LivestockPolicy policy(LivestockSpecies species) {
        return policies.getOrDefault(species, LivestockPolicy.defaults());
    }

    public Optional<AnimalFarmCycleResult> lastCycleOptional() {
        return Optional.ofNullable(lastCycle);
    }

    public AnimalFarmOperationsState withPolicy(LivestockSpecies species, LivestockPolicy policy) {
        EnumMap<LivestockSpecies, LivestockPolicy> next = new EnumMap<>(LivestockSpecies.class);
        next.putAll(policies);
        next.put(Objects.requireNonNull(species), Objects.requireNonNull(policy));
        return new AnimalFarmOperationsState(next, lastCycle);
    }

    public AnimalFarmOperationsState withLastCycle(AnimalFarmCycleResult cycle) {
        return new AnimalFarmOperationsState(policies, Objects.requireNonNull(cycle));
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag policyList = new ListTag();
        for (LivestockSpecies species : LivestockSpecies.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Species", species.id());
            entry.put("Policy", policy(species).toTag());
            policyList.add(entry);
        }
        tag.put("Policies", policyList);
        if (lastCycle != null) tag.put("LastCycle", lastCycle.toTag());
        return tag;
    }

    static AnimalFarmOperationsState fromTag(CompoundTag tag) {
        EnumMap<LivestockSpecies, LivestockPolicy> policies = new EnumMap<>(LivestockSpecies.class);
        if (tag.contains("Policies", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Policies", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                LivestockSpecies species = LivestockSpecies.fromId(entry.getString("Species"));
                if (!entry.contains("Policy", Tag.TAG_COMPOUND)
                        || policies.putIfAbsent(species, LivestockPolicy.fromTag(entry.getCompound("Policy"))) != null) {
                    throw new IllegalArgumentException("Duplicate or damaged livestock policy");
                }
            }
        }
        AnimalFarmCycleResult last = tag.contains("LastCycle", Tag.TAG_COMPOUND)
                ? AnimalFarmCycleResult.fromTag(tag.getCompound("LastCycle")) : null;
        return new AnimalFarmOperationsState(policies, last);
    }
}
