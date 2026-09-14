package dev.conner.hometown.settlement;

import dev.conner.hometown.network.data.ResidentSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Durable last-known complete resident census for one Hometown. */
public record TownCensusState(
        long observedGameTime,
        int population,
        int beds,
        int employed,
        int professionDiversity,
        List<ResidentSummary> residents) {

    public static final int MAX_RESIDENTS = 4096;

    public TownCensusState {
        if (observedGameTime < 0 || population < 0 || beds < 0 || employed < 0 || professionDiversity < 0) {
            throw new IllegalArgumentException("Invalid Town Census counters");
        }
        Objects.requireNonNull(residents);
        residents = List.copyOf(residents);
        if (residents.size() != population || residents.size() > MAX_RESIDENTS) {
            throw new IllegalArgumentException("Town Census resident list does not match population");
        }
    }

    public static TownCensusState from(SettlementStats stats, long observedGameTime) {
        Objects.requireNonNull(stats);
        if (stats.availability() != SettlementStats.Availability.COMPLETE) {
            throw new IllegalArgumentException("Only complete observations may become trusted censuses");
        }
        return new TownCensusState(observedGameTime, stats.population(), stats.beds(), stats.employed(),
                stats.professionDiversity(), stats.residents());
    }

    public SettlementStats stats() {
        return new SettlementStats(population, beds, employed, professionDiversity,
                SettlementStats.Availability.COMPLETE, residents);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ObservedGameTime", observedGameTime);
        tag.putInt("Population", population);
        tag.putInt("Beds", beds);
        tag.putInt("Employed", employed);
        tag.putInt("ProfessionDiversity", professionDiversity);
        ListTag residentList = new ListTag();
        for (ResidentSummary resident : residents) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", resident.name());
            entry.putString("ProfessionKey", resident.professionKey());
            entry.putBoolean("Child", resident.child());
            residentList.add(entry);
        }
        tag.put("Residents", residentList);
        return tag;
    }

    public static TownCensusState fromTag(CompoundTag tag) {
        if (!tag.contains("Residents", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Damaged Town Census resident list");
        }
        ListTag residentList = tag.getList("Residents", Tag.TAG_COMPOUND);
        if (residentList.size() > MAX_RESIDENTS) throw new IllegalArgumentException("Town Census is too large");
        ArrayList<ResidentSummary> residents = new ArrayList<>(residentList.size());
        for (int i = 0; i < residentList.size(); i++) {
            CompoundTag entry = residentList.getCompound(i);
            residents.add(new ResidentSummary(entry.getString("Name"), entry.getString("ProfessionKey"),
                    entry.getBoolean("Child")));
        }
        return new TownCensusState(tag.getLong("ObservedGameTime"), tag.getInt("Population"),
                tag.getInt("Beds"), tag.getInt("Employed"), tag.getInt("ProfessionDiversity"), residents);
    }
}
