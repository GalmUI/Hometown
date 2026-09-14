package dev.conner.hometown.settlement;

import dev.conner.hometown.commerce.CommerceResidentFact;
import dev.conner.hometown.network.data.ResidentSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Loaded-only resident observation. Complete results feed the shared Town Census cache. */
public final class SettlementScanner {
    private SettlementScanner() {}

    /** Protected compatibility path. New consumers should reuse {@link #observe} rather than rescan residents. */
    public static SettlementStats scan(ServerLevel level, Settlement settlement, int verticalRange) {
        return observe(level, settlement, verticalRange).stats();
    }

    /**
     * Normal consumer path. The current loaded-only pass is reconciled through the trusted census so a
     * partial observation never lowers a previously complete population.
     */
    public static SettlementObservation observe(ServerLevel level, Settlement settlement, int verticalRange) {
        return TownCensusService.reconcile(level, settlement, observeCurrent(level, settlement, verticalRange));
    }

    /**
     * Raw loaded-only resident pass used by the periodic Town Census owner. This never force-loads chunks
     * and deliberately bypasses cached census data so a new complete census can actually be discovered.
     */
    public static SettlementObservation observeCurrent(ServerLevel level, Settlement settlement, int verticalRange) {
        if (level == null) return new SettlementObservation(SettlementStats.unavailable(), java.util.List.of(), 0, 0);
        var bell = settlement.bellPosition();
        int radius = settlement.radius();
        int chunks = 0, loaded = 0;
        for (int x = (bell.getX() - radius) >> 4; x <= (bell.getX() + radius) >> 4; x++) {
            for (int z = (bell.getZ() - radius) >> 4; z <= (bell.getZ() + radius) >> 4; z++) {
                chunks++;
                if (level.getChunkSource().getChunkNow(x, z) != null) loaded++;
            }
        }
        if (loaded == 0) return new SettlementObservation(SettlementStats.unavailable(), java.util.List.of(), 0, 0);
        var candidates = new ArrayList<>(SettlementQueries.residents(level, SettlementQueries.bounds(bell, radius, verticalRange)));
        candidates.sort(Comparator.comparing(Villager::getUUID));
        var unique = new LinkedHashMap<java.util.UUID,Villager>();
        for (var villager : candidates) unique.putIfAbsent(villager.getUUID(), villager);
        var villagers = new ArrayList<>(unique.values());
        var summaries = new ArrayList<ResidentSummary>(villagers.size());
        var commerceFacts = new ArrayList<CommerceResidentFact>(villagers.size());
        var professions = new HashSet<VillagerProfession>();
        int employed = 0;
        for (Villager villager : villagers) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (!villager.isBaby() && profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT) {
                employed++;
                professions.add(profession);
            }
            var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            String professionKey = profession == VillagerProfession.NONE ? "hometown.ledger.unemployed"
                    : "entity." + key.getNamespace() + ".villager." + key.getPath();
            String name = villager.getCustomName() == null ? "" : villager.getCustomName().getString();
            summaries.add(new ResidentSummary(name, professionKey, villager.isBaby()));
            commerceFacts.add(new CommerceResidentFact(villager.getUUID(), villager.isBaby(), key));
        }
        int beds = SettlementQueries.countBeds(level, bell, radius, verticalRange, Integer.MAX_VALUE);
        var stats = new SettlementStats(villagers.size(), beds, employed, professions.size(),
                loaded == chunks ? SettlementStats.Availability.COMPLETE : SettlementStats.Availability.PARTIAL, summaries);
        return new SettlementObservation(stats, commerceFacts, candidates.size(), candidates.size() - villagers.size());
    }
}
