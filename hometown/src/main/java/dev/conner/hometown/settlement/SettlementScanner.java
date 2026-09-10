package dev.conner.hometown.settlement;

import dev.conner.hometown.network.data.ResidentSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** A synchronous, read-only scan, invoked solely by explicit Ledger requests. */
public final class SettlementScanner {
    private SettlementScanner() {}

    public static SettlementStats scan(ServerLevel level, Settlement settlement, int verticalRange) {
        if (level == null) return SettlementStats.unavailable();
        var bell = settlement.bellPosition();
        int radius = settlement.radius();
        int chunks = 0, loaded = 0;
        for (int x = (bell.getX() - radius) >> 4; x <= (bell.getX() + radius) >> 4; x++) {
            for (int z = (bell.getZ() - radius) >> 4; z <= (bell.getZ() + radius) >> 4; z++) {
                chunks++;
                if (level.getChunkSource().getChunkNow(x, z) != null) loaded++;
            }
        }
        if (loaded == 0) return SettlementStats.unavailable();
        var villagers = new ArrayList<>(SettlementQueries.residents(level, SettlementQueries.bounds(bell, radius, verticalRange)));
        // Stable ordering between pages without storing resident identities or history.
        villagers.sort(Comparator.comparing(Villager::getUUID));
        var summaries = new ArrayList<ResidentSummary>(villagers.size());
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
        }
        int beds = SettlementQueries.countBeds(level, bell, radius, verticalRange, Integer.MAX_VALUE);
        return new SettlementStats(villagers.size(), beds, employed, professions.size(),
                loaded == chunks ? SettlementStats.Availability.COMPLETE : SettlementStats.Availability.PARTIAL, summaries);
    }
}
