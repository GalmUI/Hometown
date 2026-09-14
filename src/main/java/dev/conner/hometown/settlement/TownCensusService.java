package dev.conner.hometown.settlement;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.config.HometownServerConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Shared, loaded-only census owner. Complete observations become durable trusted town facts;
 * partial observations never replace the last complete census.
 */
public final class TownCensusService {
    /** Five real-time minutes at 20 TPS. */
    public static final long SCAN_INTERVAL_TICKS = 6000L;
    /** UI begins calling a census stale after ten minutes, but still displays it. */
    public static final long FRESH_TICKS = 12000L;
    /** Destructive operations refuse census data older than thirty minutes. */
    public static final long MAX_OPERATION_AGE_TICKS = 36000L;

    public enum Freshness { MISSING, FRESH, STALE, EXPIRED }

    private static final Map<MinecraftServer, Schedule> SCHEDULES = new WeakHashMap<>();

    private static final class Schedule {
        final Map<UUID, Long> nextAttempt = new java.util.HashMap<>();
    }

    private TownCensusService() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    static void tick(MinecraftServer server) {
        if (server == null || !server.isSameThread()) return;
        HometownSavedData towns = HometownSavedData.get(server);
        long now = server.overworld().getGameTime();
        Schedule schedule = SCHEDULES.computeIfAbsent(server, ignored -> new Schedule());

        ArrayList<Settlement> ordered = new ArrayList<>(towns.all());
        ordered.sort(Comparator.comparing(town -> town.id().toString()));
        java.util.HashSet<UUID> liveIds = new java.util.HashSet<>();
        for (Settlement town : ordered) liveIds.add(town.id());
        schedule.nextAttempt.keySet().retainAll(liveIds);

        // Smooth the periodic work: at most one town is attempted on any server tick.
        for (Settlement town : ordered) {
            long due = schedule.nextAttempt.getOrDefault(town.id(), Long.MIN_VALUE);
            if (now < due) continue;
            schedule.nextAttempt.put(town.id(), now + SCAN_INTERVAL_TICKS);
            ServerLevel level = server.getLevel(town.dimension());
            if (level != null) refresh(level, town, now);
            break;
        }
    }

    /** Performs one explicit loaded-only census attempt and trusts it only when fully complete. */
    static boolean refresh(ServerLevel level, Settlement town, long observedGameTime) {
        try {
            SettlementObservation observation = SettlementScanner.observeCurrent(
                    level, town, HometownServerConfig.VERTICAL_SCAN_RADIUS.get());
            return acceptObservation(level, town, observation, observedGameTime);
        } catch (RuntimeException exception) {
            Hometown.LOGGER.warn("Town Census refresh failed for Hometown {}", town.id(), exception);
            return false;
        }
    }

    /**
     * Opportunistically accepts a complete observation already paid for by another Hometown operation
     * (notably the Ledger) without creating a second scan.
     */
    public static boolean acceptObservation(
            ServerLevel level, Settlement town, SettlementObservation observation, long observedGameTime) {
        if (level == null || town == null || observation == null
                || observation.stats().availability() != SettlementStats.Availability.COMPLETE) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;
        return TownCensusSavedData.get(server).record(town.id(),
                TownCensusState.from(observation.stats(), observedGameTime));
    }

    /**
     * Returns the trusted complete census when one exists; otherwise preserves the live observation.
     * A partial live pass can therefore never lower a previously trusted population.
     */
    public static SettlementObservation reconcile(
            ServerLevel level, Settlement town, SettlementObservation live) {
        if (level == null || town == null || live == null) return live;
        MinecraftServer server = level.getServer();
        if (server == null) return live;
        try {
            if (live.stats().availability() == SettlementStats.Availability.COMPLETE) {
                acceptObservation(level, town, live, level.getGameTime());
            }
            Optional<TownCensusState> trusted = TownCensusSavedData.get(server).get(town.id());
            if (trusted.isEmpty()) return live;
            return new SettlementObservation(trusted.get().stats(), live.residentFacts(),
                    live.residentInspections(), live.duplicateResidents());
        } catch (RuntimeException exception) {
            Hometown.LOGGER.warn("Unable to reconcile trusted Town Census for Hometown {}", town.id(), exception);
            return live;
        }
    }

    public static Optional<TownCensusState> trusted(MinecraftServer server, UUID settlementId) {
        if (server == null || settlementId == null) return Optional.empty();
        return TownCensusSavedData.get(server).get(settlementId);
    }

    /** Complete census usable by a destructive town operation such as Daily Meal. */
    public static Optional<TownCensusState> forOperations(MinecraftServer server, UUID settlementId, long now) {
        return trusted(server, settlementId).filter(state -> freshness(state, now) != Freshness.EXPIRED);
    }

    public static Freshness freshness(TownCensusState state, long now) {
        if (state == null) return Freshness.MISSING;
        long age = ageTicks(state, now);
        if (age <= FRESH_TICKS) return Freshness.FRESH;
        if (age <= MAX_OPERATION_AGE_TICKS) return Freshness.STALE;
        return Freshness.EXPIRED;
    }

    public static long ageTicks(TownCensusState state, long now) {
        if (state == null) return Long.MAX_VALUE;
        return Math.max(0L, now - state.observedGameTime());
    }

    public static String statusLabel(Optional<TownCensusState> census, long now) {
        if (census.isEmpty()) return "Waiting for complete census";
        TownCensusState state = census.get();
        long minutes = ageTicks(state, now) / 1200L;
        return switch (freshness(state, now)) {
            case MISSING -> "Waiting for complete census";
            case FRESH -> state.population() + " residents — current census";
            case STALE -> state.population() + " residents — " + Math.max(1L, minutes) + "m old";
            case EXPIRED -> state.population() + " residents — stale (" + Math.max(1L, minutes) + "m)";
        };
    }

    public static boolean warning(Optional<TownCensusState> census, long now) {
        return census.isEmpty() || freshness(census.get(), now) != Freshness.FRESH;
    }

    public static void release(MinecraftServer server) {
        SCHEDULES.remove(server);
    }
}
