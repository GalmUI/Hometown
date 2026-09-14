package dev.conner.hometown.food;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.civic.FacilityMarker;
import dev.conner.hometown.civic.FacilityType;
import dev.conner.hometown.civic.StorageService;
import dev.conner.hometown.civic.TownCivicState;
import dev.conner.hometown.civic.TownHallService;
import dev.conner.hometown.room.RoomGeometry;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import dev.conner.hometown.settlement.TownCensusService;
import dev.conner.hometown.settlement.TownCensusState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** First server-authoritative Daily Meal operation. Runs once per town/day after sunset. */
public final class DailyMealService {
    public static final int MEAL_TICK = 12000;
    public static final int MIN_NUTRITION_PER_RESIDENT = 16;
    public static final int MAX_NUTRITION_PER_RESIDENT = 24;
    private static final int MAX_STORAGE_BLOCKS = 512;
    private static final int MAX_INVENTORY_SLOTS = 16384;

    private DailyMealService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        processDueMeals(event.getServer());
    }

    static void processDueMeals(MinecraftServer server) {
        HometownSavedData towns = HometownSavedData.get(server);
        DailyMealSavedData meals = DailyMealSavedData.get(server);
        for (Settlement town : List.copyOf(towns.all())) {
            ServerLevel level = server.getLevel(town.dimension());
            if (level == null) continue;
            long dayTime = level.getDayTime();
            long day = Math.floorDiv(dayTime, 24000L);
            long timeOfDay = Math.floorMod(dayTime, 24000L);
            if (timeOfDay < MEAL_TICK) continue;

            Optional<DailyMealState> existing = meals.get(town.id());
            if (!canAttempt(existing, day)) continue;

            DailyMealState state = processTown(level, town, towns, day);
            // Missing/stale census is a wait state, not a completed meal. Retry later the same day.
            if (state.outcome() == DailyMealState.Outcome.POPULATION_UNAVAILABLE) continue;
            if (meals.record(town.id(), state)) {
                Hometown.LOGGER.info("Daily Meal for '{}' day {}: {} ({} / {} nutrition from {})",
                        town.name(), day, state.outcome(), state.consumedNutrition(), state.requiredNutrition(), state.source());
            }
        }
    }

    /**
     * A completed real meal protects its day (and any earlier day after a command-driven clock rewind).
     * A census-wait result consumed no food, so it is always safe to retry once census data becomes usable.
     */
    static boolean canAttempt(Optional<DailyMealState> existing, long day) {
        if (existing.isEmpty()) return true;
        DailyMealState prior = existing.get();
        if (retryableCensusWait(prior)) return true;
        return prior.day() < day;
    }

    private static boolean retryableCensusWait(DailyMealState state) {
        return state != null
                && state.outcome() == DailyMealState.Outcome.POPULATION_UNAVAILABLE
                && state.consumedNutrition() == 0;
    }

    static DailyMealState processTown(ServerLevel level, Settlement town, HometownSavedData towns, long day) {
        int perResident = nutritionPerResident(town.id(), day);
        long now = level.getGameTime();
        Optional<TownCensusState> trusted = TownCensusService.trusted(level.getServer(), town.id());
        Optional<TownCensusState> census = TownCensusService.forOperations(level.getServer(), town.id(), now);
        if (census.isEmpty()) {
            int lastKnownPopulation = trusted.map(TownCensusState::population).orElse(0);
            return failure(level, day, DailyMealState.Source.NONE, DailyMealState.Outcome.POPULATION_UNAVAILABLE,
                    lastKnownPopulation, perResident, 0L, 0, 0);
        }

        int population = census.get().population();
        long required = Math.multiplyExact((long) population, perResident);
        if (population == 0) {
            return new DailyMealState(day, level.getGameTime(), DailyMealState.Source.NONE,
                    DailyMealState.Outcome.NO_RESIDENTS, 0, perResident, 0L, 0L, 0L, 0, 0);
        }

        TownCivicState civic = towns.civicState(town.id());
        FacilityMarker storageMarker = civic.facility(FacilityType.STORAGE).orElse(null);
        RoomGeometry sourceRoom;
        DailyMealState.Source source;
        if (storageMarker != null) {
            source = DailyMealState.Source.STORAGE;
            var storage = StorageService.revalidate(level, town, storageMarker);
            if (!storage.qualified() || storage.room() == null) {
                return failure(level, day, source, DailyMealState.Outcome.SOURCE_UNAVAILABLE,
                        population, perResident, required, 0, 0);
            }
            sourceRoom = storage.room();
        } else {
            source = DailyMealState.Source.TOWN_HALL;
            FacilityMarker hallMarker = civic.facility(FacilityType.TOWN_HALL).orElse(null);
            if (hallMarker == null) {
                return failure(level, day, DailyMealState.Source.NONE, DailyMealState.Outcome.NO_TOWN_HALL,
                        population, perResident, required, 0, 0);
            }
            var hall = TownHallService.revalidate(level, town, hallMarker);
            if (!hall.qualified() || hall.room() == null) {
                return failure(level, day, source, DailyMealState.Outcome.SOURCE_UNAVAILABLE,
                        population, perResident, required, 0, 0);
            }
            sourceRoom = hall.room();
        }

        InventoryResult inventory = consume(level, sourceRoom, required);
        if (!inventory.safeToCommit()) {
            return failure(level, day, source, DailyMealState.Outcome.SOURCE_UNAVAILABLE,
                    population, perResident, required, inventory.unresolvedLootContainers(), inventory.unavailableContainers());
        }
        DailyMealState.Outcome outcome = outcome(required, inventory.consumedNutrition());
        return new DailyMealState(day, level.getGameTime(), source, outcome, population, perResident,
                required, inventory.consumedNutrition(), inventory.remainingKnownNutrition(),
                inventory.unresolvedLootContainers(), inventory.unavailableContainers());
    }

    private static DailyMealState failure(ServerLevel level, long day, DailyMealState.Source source,
                                          DailyMealState.Outcome outcome, int population, int perResident,
                                          long required, int unresolved, int unavailable) {
        return new DailyMealState(day, level.getGameTime(), source, outcome, population, perResident,
                required, 0L, -1L, unresolved, unavailable);
    }

    /** Stable town/day roll. Reloading or reopening a world cannot reroll a cheaper meal. */
    public static int nutritionPerResident(UUID townId, long day) {
        long z = townId.getMostSignificantBits() ^ Long.rotateLeft(townId.getLeastSignificantBits(), 21)
                ^ (day * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        int span = MAX_NUTRITION_PER_RESIDENT - MIN_NUTRITION_PER_RESIDENT + 1;
        return MIN_NUTRITION_PER_RESIDENT + (int)Math.floorMod(z, (long)span);
    }

    static DailyMealState.Outcome outcome(long required, long consumed) {
        if (required <= 0) return DailyMealState.Outcome.NO_RESIDENTS;
        return consumed >= required ? DailyMealState.Outcome.FED : DailyMealState.Outcome.SHORTAGE;
    }

    public static String summary(Optional<DailyMealState> state) {
        if (state.isEmpty()) return "Not yet processed";
        DailyMealState meal = state.get();
        return switch (meal.outcome()) {
            case FED -> "Fed " + meal.consumedNutrition() + " / " + meal.requiredNutrition();
            case SHORTAGE -> "Shortage " + meal.consumedNutrition() + " / " + meal.requiredNutrition();
            case NO_RESIDENTS -> "No residents to feed";
            case POPULATION_UNAVAILABLE -> "Waiting for census";
            case NO_TOWN_HALL -> "Awaiting Town Hall";
            case SOURCE_UNAVAILABLE -> meal.source() == DailyMealState.Source.STORAGE
                    ? "Storage unavailable" : "Meal source unavailable";
        };
    }

    /** Current operational label, including safe recovery from an old census-wait after /time set. */
    public static String currentSummary(Optional<DailyMealState> state, long dayTime, boolean censusUsable) {
        long day = Math.floorDiv(dayTime, 24000L);
        long time = Math.floorMod(dayTime, 24000L);
        boolean pending = canAttempt(state, day);
        if (time >= MEAL_TICK && pending) return censusUsable ? "Due now" : "Waiting for census";
        if (time < MEAL_TICK && pending && censusUsable) return "Ready for sunset";
        if (time < MEAL_TICK && pending) return "Waiting for census";
        return summary(state);
    }

    public static boolean warning(Optional<DailyMealState> state) {
        return state.map(DailyMealState::warning).orElse(false);
    }

    public static boolean currentWarning(Optional<DailyMealState> state, long dayTime, boolean censusUsable) {
        String current = currentSummary(state, dayTime, censusUsable);
        if ("Waiting for census".equals(current)) return true;
        if ("Ready for sunset".equals(current) || "Due now".equals(current)) return false;
        return warning(state);
    }

    public static String nextMealLabel(long dayTime, Optional<DailyMealState> state) {
        long day = Math.floorDiv(dayTime, 24000L);
        long time = Math.floorMod(dayTime, 24000L);
        boolean pending = canAttempt(state, day);
        if (time < MEAL_TICK && pending) return "Today at sunset";
        if (time >= MEAL_TICK && pending) return "Due now";
        return "Next sunset";
    }

    private record SlotCandidate(Container inventory, int slot, Item item, int expectedCount,
                                 int nutritionPerItem, BlockPos position) {}
    private record Removal(SlotCandidate candidate, int count) {}
    private record InventoryResult(boolean safeToCommit, long consumedNutrition, long remainingKnownNutrition,
                                   int unresolvedLootContainers, int unavailableContainers) {}

    private static InventoryResult consume(ServerLevel level, RoomGeometry room, long required) {
        LinkedHashSet<BlockPos> semantic = new LinkedHashSet<>(room.boundary());
        semantic.addAll(room.interior());
        TreeSet<BlockPos> ordered = new TreeSet<>(semantic);
        Set<Container> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<SlotCandidate> candidates = new ArrayList<>();
        int storageBlocks = 0, slots = 0, unresolved = 0, unavailable = 0;
        long availableNutrition = 0L;

        try {
            for (BlockPos position : ordered) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                if (chunk == null) return new InventoryResult(false, 0L, -1L, unresolved, unavailable + 1);
                if (!chunk.getBlockState(position).is(FoodScanner.FOOD_STORAGE)) continue;
                if (++storageBlocks > MAX_STORAGE_BLOCKS) return new InventoryResult(false, 0L, -1L, unresolved, unavailable);
                BlockEntity entity = chunk.getBlockEntities().get(position);
                if (entity == null || entity.isRemoved() || !(entity instanceof Container inventory)) {
                    unavailable++;
                    continue;
                }
                if (!visited.add(inventory)) continue;
                if (entity instanceof RandomizableContainer loot && loot.getLootTable() != null) {
                    unresolved++;
                    continue;
                }
                int size = inventory.getContainerSize();
                if (size < 0) { unavailable++; continue; }
                for (int slot = 0; slot < size; slot++) {
                    if (++slots > MAX_INVENTORY_SLOTS) return new InventoryResult(false, 0L, -1L, unresolved, unavailable);
                    ItemStack stack = inventory.getItem(slot);
                    if (stack == null) { unavailable++; break; }
                    if (stack.isEmpty() || stack.is(FoodScanner.FOOD_EXCLUDED)) continue;
                    var food = stack.get(DataComponents.FOOD);
                    if (food == null || food.nutrition() <= 0) continue;
                    long stackNutrition = (long)food.nutrition() * stack.getCount();
                    availableNutrition = saturatingAdd(availableNutrition, stackNutrition);
                    candidates.add(new SlotCandidate(inventory, slot, stack.getItem(), stack.getCount(),
                            food.nutrition(), position.immutable()));
                }
            }
        } catch (RuntimeException ex) {
            Hometown.LOGGER.warn("Daily Meal inventory observation failed", ex);
            return new InventoryResult(false, 0L, -1L, unresolved, unavailable + 1);
        }

        ArrayList<Removal> removals = new ArrayList<>();
        long consumed = 0L;
        for (SlotCandidate candidate : candidates) {
            if (consumed >= required) break;
            long remaining = required - consumed;
            int needed = (int)Math.min(candidate.expectedCount(), Math.max(1L,
                    (remaining + candidate.nutritionPerItem() - 1L) / candidate.nutritionPerItem()));
            removals.add(new Removal(candidate, needed));
            consumed = saturatingAdd(consumed, (long)needed * candidate.nutritionPerItem());
        }

        for (Removal removal : removals) {
            ItemStack current = removal.candidate().inventory().getItem(removal.candidate().slot());
            var food = current == null ? null : current.get(DataComponents.FOOD);
            if (current == null || current.isEmpty() || current.getItem() != removal.candidate().item()
                    || current.getCount() < removal.count() || current.is(FoodScanner.FOOD_EXCLUDED)
                    || food == null || food.nutrition() != removal.candidate().nutritionPerItem()) {
                return new InventoryResult(false, 0L, -1L, unresolved, unavailable + 1);
            }
        }
        for (Removal removal : removals) {
            removal.candidate().inventory().removeItem(removal.candidate().slot(), removal.count());
            removal.candidate().inventory().setChanged();
        }
        return new InventoryResult(true, consumed, Math.max(0L, availableNutrition - consumed), unresolved, unavailable);
    }

    private static long saturatingAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }
}
