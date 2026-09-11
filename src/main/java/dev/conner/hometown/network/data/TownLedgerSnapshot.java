package dev.conner.hometown.network.data;

import dev.conner.hometown.settlement.Settlement;
import dev.conner.hometown.settlement.SettlementStats;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record TownLedgerSnapshot(UUID settlementId, String townName, String founderName, long foundedDay,
        String dimension, BlockPos bell, BellState bellState, SettlementStats.Availability availability,
        int population, int beds, int employed, int professionDiversity, int residentPage, int residentPages,
        List<ResidentSummary> residents, dev.conner.hometown.housing.HousingSnapshot housing, dev.conner.hometown.food.FoodSnapshot food,
        dev.conner.hometown.safety.SafetySnapshot safety,dev.conner.hometown.comfort.ComfortSnapshot comfort) {
    public TownLedgerSnapshot(UUID id, String name, String founder, long day, String dimension, BlockPos bell,
            BellState bellState, SettlementStats.Availability availability, int population, int beds, int employed,
            int diversity, int page, int pages, List<ResidentSummary> residents, dev.conner.hometown.housing.HousingSnapshot housing,
            dev.conner.hometown.food.FoodSnapshot food,dev.conner.hometown.safety.SafetySnapshot safety) {
        this(id,name,founder,day,dimension,bell,bellState,availability,population,beds,employed,diversity,page,pages,residents,housing,food,
            safety,dev.conner.hometown.comfort.ComfortSnapshot.unavailable(safety.metadata()));
    }
    public TownLedgerSnapshot withComfort(dev.conner.hometown.comfort.ComfortSnapshot value) {
        return new TownLedgerSnapshot(settlementId,townName,founderName,foundedDay,dimension,bell,bellState,availability,
            population,beds,employed,professionDiversity,residentPage,residentPages,residents,housing,food,safety,value);
    }
    public TownLedgerSnapshot(UUID id, String name, String founder, long day, String dimension, BlockPos bell,
            BellState bellState, SettlementStats.Availability availability, int population, int beds, int employed,
            int diversity, int page, int pages, List<ResidentSummary> residents, dev.conner.hometown.housing.HousingSnapshot housing,
            dev.conner.hometown.food.FoodSnapshot food) {
        this(id,name,founder,day,dimension,bell,bellState,availability,population,beds,employed,diversity,page,pages,residents,housing,food,
            dev.conner.hometown.safety.SafetySnapshot.unavailable(new dev.conner.hometown.observation.ObservationMetadata(id,dimension,0,0,0,0,0)));
    }
    public dev.conner.hometown.observation.ObservationMetadata metadata() { return safety.metadata(); }
    public TownLedgerSnapshot withSafety(dev.conner.hometown.safety.SafetySnapshot value) {
        return new TownLedgerSnapshot(settlementId,townName,founderName,foundedDay,dimension,bell,bellState,availability,
            population,beds,employed,professionDiversity,residentPage,residentPages,residents,housing,food,value);
    }
    public TownLedgerSnapshot(UUID id, String name, String founder, long day, String dimension, BlockPos bell,
            BellState bellState, SettlementStats.Availability availability, int population, int beds, int employed,
            int diversity, int page, int pages, List<ResidentSummary> residents) {
        this(id,name,founder,day,dimension,bell,bellState,availability,population,beds,employed,diversity,page,pages,residents,
                dev.conner.hometown.housing.HousingSnapshot.unavailable(population,beds));
    }
    public TownLedgerSnapshot(UUID id, String name, String founder, long day, String dimension, BlockPos bell,
            BellState bellState, SettlementStats.Availability availability, int population, int beds, int employed,
            int diversity, int page, int pages, List<ResidentSummary> residents, dev.conner.hometown.housing.HousingSnapshot housing) {
        this(id,name,founder,day,dimension,bell,bellState,availability,population,beds,employed,diversity,page,pages,residents,
                housing,dev.conner.hometown.food.FoodSnapshot.unavailable(population));
    }
    public TownLedgerSnapshot withFood(dev.conner.hometown.food.FoodSnapshot food) {
        return new TownLedgerSnapshot(settlementId,townName,founderName,foundedDay,dimension,bell,bellState,availability,
                population,beds,employed,professionDiversity,residentPage,residentPages,residents,housing,food,safety,comfort);
    }
    public TownLedgerSnapshot withHousing(dev.conner.hometown.housing.HousingSnapshot housing) {
        return new TownLedgerSnapshot(settlementId,townName,founderName,foundedDay,dimension,bell,bellState,availability,
                population,beds,employed,professionDiversity,residentPage,residentPages,residents,housing,food,safety,comfort);
    }
    public static final int PAGE_SIZE = 4;
    public enum BellState { PRESENT, MISSING, UNAVAILABLE }
    public TownLedgerSnapshot {
        java.util.Objects.requireNonNull(housing);
        java.util.Objects.requireNonNull(food);
        java.util.Objects.requireNonNull(safety);
        java.util.Objects.requireNonNull(comfort);
        if(!comfort.metadata().equals(safety.metadata()))throw new IllegalArgumentException("Mismatched Comfort observation");
        if (!settlementId.equals(safety.metadata().settlementId()) || !dimension.equals(safety.metadata().dimension()))
            throw new IllegalArgumentException("Mismatched observation");
        bell = bell.immutable();
        residents = List.copyOf(residents);
        if (residents.size() > PAGE_SIZE || residentPage < 0 || residentPages < 1 || residentPage >= residentPages)
            throw new IllegalArgumentException("Invalid resident page");
    }

    public static TownLedgerSnapshot of(Settlement town, SettlementStats stats, BellState bellState, int requestedPage) {
        int pages = Math.max(1, (stats.residents().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int start = page * PAGE_SIZE;
        return new TownLedgerSnapshot(town.id(), town.name(), town.founderName(), town.foundedDay(),
                town.dimension().location().toString(), town.bellPosition(), bellState, stats.availability(),
                stats.population(), stats.beds(), stats.employed(), stats.professionDiversity(), page, pages,
                stats.residents().subList(start, Math.min(start + PAGE_SIZE, stats.residents().size())));
    }
}
