package dev.conner.hometown.civic;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload;
import dev.conner.hometown.network.UpdateAnimalFarmPolicyPayload;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-authoritative livestock work cycle. The farm observes a loaded enclosed paddock, protects
 * breeding stock and named animals, verifies output capacity first, then performs bounded culling.
 */
public final class AnimalFarmOperationsService {
    /** Late workday: 100 seconds before the Daily Meal sunset operation at tick 12000. */
    public static final int WORK_TICK = 10000;
    public static final int MAX_CULL_PER_SPECIES = 4;
    public static final int BEEF_PER_COW = 2;
    public static final int LEATHER_PER_COW = 1;
    public static final int PORKCHOPS_PER_PIG = 2;

    private static final long RETRY_INTERVAL_TICKS = 200L;
    private static final int MAX_LIVESTOCK_ENTITIES = 512;
    private static final int MAX_STORAGE_BLOCKS = 128;
    private static final int MAX_INVENTORY_SLOTS = 4096;
    private static final Map<MinecraftServer, Map<UUID, Long>> NEXT_ATTEMPT = new WeakHashMap<>();

    private record Herd(List<Animal> adults, int young, int namedAdults) {
        Herd {
            adults = List.copyOf(adults);
            if (young < 0 || namedAdults < 0 || namedAdults > adults.size()) {
                throw new IllegalArgumentException("Invalid herd observation");
            }
        }
    }
    private record LivestockScan(boolean available, Map<LivestockSpecies, Herd> herds) {}
    private record SlotRef(Container inventory, int slot, ItemStack original) {}
    private record Output(Item item, int count) {}

    private AnimalFarmOperationsService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        processDueCycles(event.getServer());
    }

    static void processDueCycles(MinecraftServer server) {
        if (server == null || !server.isSameThread()) return;
        HometownSavedData towns = HometownSavedData.get(server);
        AnimalFarmOperationsSavedData operations = AnimalFarmOperationsSavedData.get(server);
        long now = server.overworld().getGameTime();
        Map<UUID, Long> schedule = NEXT_ATTEMPT.computeIfAbsent(server, ignored -> new HashMap<>());

        ArrayList<Settlement> ordered = new ArrayList<>(towns.all());
        ordered.sort(Comparator.comparing(town -> town.id().toString()));
        HashMap<UUID, Boolean> live = new HashMap<>();
        for (Settlement town : ordered) live.put(town.id(), Boolean.TRUE);
        schedule.keySet().retainAll(live.keySet());

        // Smooth work across towns: perform at most one potentially expensive farm cycle attempt per tick.
        for (Settlement town : ordered) {
            ServerLevel level = server.getLevel(town.dimension());
            if (level == null) continue;
            FacilityMarker marker = towns.civicState(town.id()).facility(FacilityType.ANIMAL_FARM).orElse(null);
            if (marker == null) continue;

            long dayTime = level.getDayTime();
            long day = Math.floorDiv(dayTime, 24000L);
            long timeOfDay = Math.floorMod(dayTime, 24000L);
            if (timeOfDay < WORK_TICK) continue;

            AnimalFarmCycleResult previous = operations.state(town.id()).lastCycle();
            if (previous != null && previous.terminalForDay() && previous.day() >= day) continue;
            long due = schedule.getOrDefault(town.id(), Long.MIN_VALUE);
            if (now < due) continue;
            schedule.put(town.id(), now + RETRY_INTERVAL_TICKS);

            AnimalFarmCycleResult result = processTown(level, town, marker, operations.state(town.id()), day);
            if (operations.recordCycle(town.id(), result)) {
                Hometown.LOGGER.info("Animal Farm cycle for '{}' day {}: {} (cows {}, pigs {})",
                        town.name(), day, result.outcome(), result.cowsCulled(), result.pigsCulled());
            }
            break;
        }
    }

    static AnimalFarmCycleResult processTown(ServerLevel level, Settlement town, FacilityMarker marker,
                                             AnimalFarmOperationsState state, long day) {
        AnimalFarmQualifier.Result validation = AnimalFarmService.revalidate(level, town, marker);
        if (!validation.qualified()) {
            return result(level, day, AnimalFarmCycleResult.Outcome.FACILITY_UNAVAILABLE,
                    zeroHerd(), zeroHerd(), 0, 0);
        }
        Optional<AnimalFarmPaddockScanner.Area> area = AnimalFarmPaddockScanner.resolve(level, town, validation);
        if (area.isEmpty()) {
            return result(level, day, AnimalFarmCycleResult.Outcome.LIVESTOCK_UNAVAILABLE,
                    zeroHerd(), zeroHerd(), 0, 0);
        }
        LivestockScan scan = observeLivestock(level, town, area.get());
        if (!scan.available()) {
            return result(level, day, AnimalFarmCycleResult.Outcome.LIVESTOCK_UNAVAILABLE,
                    zeroHerd(), zeroHerd(), 0, 0);
        }

        Herd cows = scan.herds().get(LivestockSpecies.COW);
        Herd pigs = scan.herds().get(LivestockSpecies.PIG);
        int cowsToCull = cullCount(cows.adults().size(), cows.namedAdults(), state.policy(LivestockSpecies.COW));
        int pigsToCull = cullCount(pigs.adults().size(), pigs.namedAdults(), state.policy(LivestockSpecies.PIG));
        if (cowsToCull == 0 && pigsToCull == 0) {
            return result(level, day, AnimalFarmCycleResult.Outcome.NO_SURPLUS, cows, pigs, 0, 0);
        }

        List<Animal> selectedCows = selectCull(cows.adults(), cowsToCull);
        List<Animal> selectedPigs = selectCull(pigs.adults(), pigsToCull);
        if (selectedCows.size() != cowsToCull || selectedPigs.size() != pigsToCull
                || !validateSelected(selectedCows, LivestockSpecies.COW, area.get())
                || !validateSelected(selectedPigs, LivestockSpecies.PIG, area.get())) {
            return result(level, day, AnimalFarmCycleResult.Outcome.LIVESTOCK_UNAVAILABLE,
                    cows, pigs, 0, 0);
        }

        List<Output> outputs = outputs(cowsToCull, pigsToCull);
        if (!insertOutputs(level, validation.room(), outputs)) {
            return result(level, day, AnimalFarmCycleResult.Outcome.OUTPUT_STORAGE_FULL,
                    cows, pigs, 0, 0);
        }

        // Output capacity was verified and committed first. discard() suppresses vanilla random drops;
        // Hometown's deterministic fixed output is already safely in the farm's recognized storage.
        selectedCows.forEach(Animal::discard);
        selectedPigs.forEach(Animal::discard);
        return result(level, day, AnimalFarmCycleResult.Outcome.PROCESSED,
                cows, pigs, cowsToCull, pigsToCull);
    }

    public static AnimalFarmOperationsSnapshot snapshot(
            ServerLevel level, Settlement town, AnimalFarmQualifier.Result validation) {
        AnimalFarmOperationsState state = AnimalFarmOperationsSavedData.get(level.getServer()).state(town.id());
        EnumMap<LivestockSpecies, LivestockPolicy> policies = new EnumMap<>(LivestockSpecies.class);
        for (LivestockSpecies species : LivestockSpecies.values()) policies.put(species, state.policy(species));
        EnumMap<LivestockSpecies, AnimalFarmOperationsSnapshot.Counts> counts = new EnumMap<>(LivestockSpecies.class);
        for (LivestockSpecies species : LivestockSpecies.values()) {
            counts.put(species, new AnimalFarmOperationsSnapshot.Counts(0, 0, 0));
        }
        if (validation == null || !validation.qualified()) {
            return new AnimalFarmOperationsSnapshot(false, counts, policies, state.lastCycle());
        }
        Optional<AnimalFarmPaddockScanner.Area> area = AnimalFarmPaddockScanner.resolve(level, town, validation);
        if (area.isEmpty()) return new AnimalFarmOperationsSnapshot(false, counts, policies, state.lastCycle());
        LivestockScan scan = observeLivestock(level, town, area.get());
        if (!scan.available()) return new AnimalFarmOperationsSnapshot(false, counts, policies, state.lastCycle());
        for (LivestockSpecies species : LivestockSpecies.values()) {
            Herd herd = scan.herds().get(species);
            counts.put(species, new AnimalFarmOperationsSnapshot.Counts(
                    herd.adults().size(), herd.young(), herd.namedAdults()));
        }
        return new AnimalFarmOperationsSnapshot(true, counts, policies, state.lastCycle());
    }

    /** Server-authoritative adjustment used by the Animal Farm sign screen. */
    public static Optional<FacilityDetailSnapshotPayload> updatePolicy(
            ServerPlayer player, UpdateAnimalFarmPolicyPayload request) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Animal Farm policy changes require the server thread");
        }
        if (!player.isAlive() || player.isSpectator()) return fail(player, "You must be an active player to manage an Animal Farm.");

        HometownSavedData towns = HometownSavedData.get(server);
        Settlement town = towns.getSettlement(request.settlementId()).orElse(null);
        if (town == null) return fail(player, "That Hometown no longer exists.");
        ServerLevel level = player.serverLevel();
        TownCivicState civic = towns.civicState(town.id());
        FacilityMarker marker = civic.facility(FacilityType.ANIMAL_FARM).orElse(null);
        if (marker == null || !marker.dimension().equals(level.dimension())) {
            return fail(player, "This Hometown has no registered Animal Farm here.");
        }
        if (player.distanceToSqr(marker.markerPosition().getX() + 0.5, marker.markerPosition().getY() + 0.5,
                marker.markerPosition().getZ() + 0.5) > 64.0) {
            return fail(player, "You must be within 8 blocks of the registered Animal Farm sign.");
        }
        LevelChunk markerChunk = chunk(level, marker.markerPosition());
        if (markerChunk == null || !(markerChunk.getBlockEntities().get(marker.markerPosition()) instanceof SignBlockEntity sign)
                || marker.side() == FacilityMarkerSide.NONE) {
            return fail(player, "The registered Animal Farm sign is unavailable.");
        }
        boolean front = marker.side() == FacilityMarkerSide.FRONT;
        if (!AnimalFarmSignGrammar.matches(sign.getText(front))) {
            return fail(player, "The registered Animal Farm sign no longer matches its civic marker.");
        }

        AnimalFarmOperationsSavedData operations = AnimalFarmOperationsSavedData.get(server);
        operations.update(town.id(), current -> {
            LivestockPolicy policy = current.policy(request.species());
            LivestockPolicy next = switch (request.setting()) {
                case BREEDING_PAIRS -> policy.withBreedingPairs(policy.breedingPairs() + request.delta());
                case CULL_ABOVE -> policy.withCullAbove(policy.cullAbove() + request.delta());
            };
            return current.withPolicy(request.species(), next);
        });
        return FacilityDetailService.open(player, marker.markerPosition(), front);
    }

    static int cullCount(int adults, int namedAdults, LivestockPolicy policy) {
        int rawSurplus = Math.max(0, adults - policy.cullAbove());
        int unnamed = Math.max(0, adults - namedAdults);
        return Math.min(MAX_CULL_PER_SPECIES, Math.min(rawSurplus, unnamed));
    }

    public static String nextCycleLabel(long dayTime, Optional<AnimalFarmCycleResult> lastCycle) {
        long day = Math.floorDiv(dayTime, 24000L);
        long time = Math.floorMod(dayTime, 24000L);
        boolean complete = lastCycle.filter(cycle -> cycle.terminalForDay() && cycle.day() >= day).isPresent();
        if (complete) return "Next workday";
        if (time < WORK_TICK) return "Today — late workday";
        return "Due now";
    }

    public static void release(MinecraftServer server) {
        NEXT_ATTEMPT.remove(server);
    }

    private static LivestockScan observeLivestock(
            ServerLevel level, Settlement town, AnimalFarmPaddockScanner.Area area) {
        if (!area.fullyLoaded(level)) return unavailableScan();
        AABB bounds = new AABB(area.minX(), level.getMinBuildHeight(), area.minZ(),
                area.maxX() + 1.0, level.getMaxBuildHeight(), area.maxZ() + 1.0);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, bounds, animal ->
                species(animal) != null && area.contains(animal.blockPosition())
                        && town.contains(level.dimension(), animal.blockPosition()));
        if (animals.size() > MAX_LIVESTOCK_ENTITIES) return unavailableScan();

        EnumMap<LivestockSpecies, ArrayList<Animal>> adults = new EnumMap<>(LivestockSpecies.class);
        EnumMap<LivestockSpecies, Integer> young = new EnumMap<>(LivestockSpecies.class);
        EnumMap<LivestockSpecies, Integer> named = new EnumMap<>(LivestockSpecies.class);
        for (LivestockSpecies species : LivestockSpecies.values()) {
            adults.put(species, new ArrayList<>());
            young.put(species, 0);
            named.put(species, 0);
        }
        for (Animal animal : animals) {
            LivestockSpecies species = species(animal);
            if (species == null) continue;
            if (animal.isBaby()) {
                young.put(species, young.get(species) + 1);
            } else {
                adults.get(species).add(animal);
                if (animal.hasCustomName()) named.put(species, named.get(species) + 1);
            }
        }
        Comparator<Animal> byUuid = Comparator.comparing(animal -> animal.getUUID().toString());
        EnumMap<LivestockSpecies, Herd> herds = new EnumMap<>(LivestockSpecies.class);
        for (LivestockSpecies species : LivestockSpecies.values()) {
            adults.get(species).sort(byUuid);
            herds.put(species, new Herd(adults.get(species), young.get(species), named.get(species)));
        }
        return new LivestockScan(true, Map.copyOf(herds));
    }

    private static LivestockScan unavailableScan() {
        EnumMap<LivestockSpecies, Herd> herds = new EnumMap<>(LivestockSpecies.class);
        for (LivestockSpecies species : LivestockSpecies.values()) herds.put(species, zeroHerd());
        return new LivestockScan(false, Map.copyOf(herds));
    }

    private static Herd zeroHerd() { return new Herd(List.of(), 0, 0); }

    private static LivestockSpecies species(Animal animal) {
        if (animal instanceof Cow) return LivestockSpecies.COW;
        if (animal instanceof Pig) return LivestockSpecies.PIG;
        return null;
    }

    private static List<Animal> selectCull(List<Animal> adults, int count) {
        if (count <= 0) return List.of();
        ArrayList<Animal> selected = new ArrayList<>(count);
        for (Animal animal : adults) {
            if (animal.hasCustomName()) continue;
            selected.add(animal);
            if (selected.size() == count) break;
        }
        return List.copyOf(selected);
    }

    private static boolean validateSelected(
            List<Animal> animals, LivestockSpecies expected, AnimalFarmPaddockScanner.Area area) {
        for (Animal animal : animals) {
            if (!animal.isAlive() || animal.isBaby() || animal.hasCustomName()
                    || species(animal) != expected || !area.contains(animal.blockPosition())) return false;
        }
        return true;
    }

    private static AnimalFarmCycleResult result(
            ServerLevel level, long day, AnimalFarmCycleResult.Outcome outcome,
            Herd cows, Herd pigs, int cowsCulled, int pigsCulled) {
        int beef = outcome == AnimalFarmCycleResult.Outcome.PROCESSED ? cowsCulled * BEEF_PER_COW : 0;
        int leather = outcome == AnimalFarmCycleResult.Outcome.PROCESSED ? cowsCulled * LEATHER_PER_COW : 0;
        int pork = outcome == AnimalFarmCycleResult.Outcome.PROCESSED ? pigsCulled * PORKCHOPS_PER_PIG : 0;
        return new AnimalFarmCycleResult(day, level.getGameTime(), outcome,
                cows.adults().size(), cows.young(), pigs.adults().size(), pigs.young(),
                cowsCulled, pigsCulled, beef, leather, pork);
    }

    private static List<Output> outputs(int cows, int pigs) {
        ArrayList<Output> outputs = new ArrayList<>();
        if (cows > 0) {
            outputs.add(new Output(Items.BEEF, cows * BEEF_PER_COW));
            outputs.add(new Output(Items.LEATHER, cows * LEATHER_PER_COW));
        }
        if (pigs > 0) outputs.add(new Output(Items.PORKCHOP, pigs * PORKCHOPS_PER_PIG));
        return List.copyOf(outputs);
    }

    /** Plans every slot first; if the full output cannot fit, no inventory or animal is mutated. */
    private static boolean insertOutputs(ServerLevel level, dev.conner.hometown.room.RoomGeometry room, List<Output> outputs) {
        LinkedHashSet<BlockPos> semantic = new LinkedHashSet<>(room.boundary());
        semantic.addAll(room.interior());
        TreeSet<BlockPos> ordered = new TreeSet<>(semantic);
        Set<Container> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<SlotRef> slots = new ArrayList<>();
        int storageBlocks = 0;
        int slotCount = 0;

        for (BlockPos position : ordered) {
            LevelChunk chunk = chunk(level, position);
            if (chunk == null) return false;
            if (!chunk.getBlockState(position).is(StorageRules.STORAGE)) continue;
            if (++storageBlocks > MAX_STORAGE_BLOCKS) return false;
            BlockEntity entity = chunk.getBlockEntities().get(position);
            if (entity == null || entity.isRemoved() || !(entity instanceof Container inventory)) continue;
            if (!visited.add(inventory)) continue;
            if (entity instanceof RandomizableContainer loot && loot.getLootTable() != null) continue;
            int size = inventory.getContainerSize();
            if (size < 0) return false;
            for (int slot = 0; slot < size; slot++) {
                if (++slotCount > MAX_INVENTORY_SLOTS) return false;
                ItemStack stack = inventory.getItem(slot);
                if (stack == null) return false;
                slots.add(new SlotRef(inventory, slot, stack.copy()));
            }
        }
        if (slots.isEmpty()) return false;

        ArrayList<ItemStack> planned = new ArrayList<>(slots.size());
        for (SlotRef slot : slots) planned.add(slot.original().copy());
        for (Output output : outputs) {
            int remaining = output.count();
            ItemStack template = new ItemStack(output.item());
            // Merge first, then use empty slots. This minimizes slot churn and makes capacity deterministic.
            for (int i = 0; i < slots.size() && remaining > 0; i++) {
                ItemStack stack = planned.get(i);
                SlotRef ref = slots.get(i);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)
                        || !ref.inventory().canPlaceItem(ref.slot(), template)) continue;
                int cap = Math.min(stack.getMaxStackSize(), ref.inventory().getMaxStackSize()) - stack.getCount();
                if (cap <= 0) continue;
                int added = Math.min(cap, remaining);
                stack.grow(added);
                remaining -= added;
            }
            for (int i = 0; i < slots.size() && remaining > 0; i++) {
                ItemStack stack = planned.get(i);
                SlotRef ref = slots.get(i);
                if (!stack.isEmpty() || !ref.inventory().canPlaceItem(ref.slot(), template)) continue;
                int cap = Math.min(template.getMaxStackSize(), ref.inventory().getMaxStackSize());
                int added = Math.min(cap, remaining);
                planned.set(i, new ItemStack(output.item(), added));
                remaining -= added;
            }
            if (remaining > 0) return false;
        }

        // Revalidate every slot that will change before committing any output.
        for (int i = 0; i < slots.size(); i++) {
            if (sameStack(slots.get(i).original(), planned.get(i))) continue;
            ItemStack current = slots.get(i).inventory().getItem(slots.get(i).slot());
            if (current == null || !sameStack(current, slots.get(i).original())) return false;
        }
        Set<Container> changed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < slots.size(); i++) {
            if (sameStack(slots.get(i).original(), planned.get(i))) continue;
            slots.get(i).inventory().setItem(slots.get(i).slot(), planned.get(i).copy());
            changed.add(slots.get(i).inventory());
        }
        changed.forEach(Container::setChanged);
        return true;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    private static Optional<FacilityDetailSnapshotPayload> fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return Optional.empty();
    }
}
