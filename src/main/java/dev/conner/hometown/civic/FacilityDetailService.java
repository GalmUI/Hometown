package dev.conner.hometown.civic;

import dev.conner.hometown.food.DailyMealSavedData;
import dev.conner.hometown.food.DailyMealService;
import dev.conner.hometown.food.DailyMealState;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload.Line;
import dev.conner.hometown.network.FacilityDetailSnapshotPayload.Tone;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/** Read-only server owner for the registered-facility sign detail UI. */
public final class FacilityDetailService {
    private FacilityDetailService() {}

    public static Optional<FacilityDetailSnapshotPayload> open(
            ServerPlayer player, BlockPos markerPosition, boolean front) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(markerPosition);
        var server = player.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Facility detail access requires the server thread");
        }
        if (!player.isAlive() || player.isSpectator()) return fail(player, "You must be an active player to inspect civic facilities.");
        if (player.distanceToSqr(markerPosition.getX() + 0.5, markerPosition.getY() + 0.5,
                markerPosition.getZ() + 0.5) > 64.0) {
            return fail(player, "You must be within 8 blocks of the facility sign.");
        }

        ServerLevel level = player.serverLevel();
        var chunk = level.getChunkSource().getChunkNow(markerPosition.getX() >> 4, markerPosition.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntities().get(markerPosition) instanceof SignBlockEntity)) return Optional.empty();

        HometownSavedData data = HometownSavedData.get(server);
        Settlement town = data.findSettlementContaining(level.dimension(), markerPosition).orElse(null);
        if (town == null) return Optional.empty();
        TownCivicState civic = data.civicState(town.id());
        if (!civic.colorsConfigured()) return Optional.empty();

        FacilityMarkerSide side = front ? FacilityMarkerSide.FRONT : FacilityMarkerSide.BACK;
        FacilityMarker marker = null;
        for (FacilityMarker candidate : civic.facilities().values()) {
            if (!candidate.dimension().equals(level.dimension())
                    || !candidate.markerPosition().equals(markerPosition)
                    || candidate.side() != side) continue;
            if (marker != null) return fail(player, "This civic sign has conflicting facility registrations.");
            marker = candidate;
        }
        if (marker == null) return Optional.empty();

        var meal = DailyMealSavedData.get(server).get(town.id());
        return Optional.of(snapshotFor(level, town, civic, marker, meal, level.getDayTime()));
    }

    static FacilityDetailSnapshotPayload snapshotFor(
            ServerLevel level, Settlement town, TownCivicState civic, FacilityMarker marker) {
        return snapshotFor(level, town, civic, marker, Optional.empty(), 0L);
    }

    static FacilityDetailSnapshotPayload snapshotFor(
            ServerLevel level, Settlement town, TownCivicState civic, FacilityMarker marker,
            Optional<DailyMealState> meal, long dayTime) {
        return switch (marker.type()) {
            case STORAGE -> storageSnapshotFor(town, civic, marker,
                    StorageService.revalidate(level, town, marker), meal, dayTime);
            case TOWN_HALL -> {
                var result = TownHallService.revalidate(level, town, marker);
                yield genericSnapshot(town, civic, marker, result.qualified(),
                        result.qualified() ? null : TownHallService.qualificationMessage(result),
                        "Civic administration and town-wide progression.",
                        "Use the linked Town Ledger on the Hall lectern for Town Administration.");
            }
            case ANIMAL_FARM -> animalFarmSnapshotFor(
                    town, civic, marker, AnimalFarmService.revalidate(level, town, marker));
        };
    }

    static FacilityDetailSnapshotPayload storageSnapshotFor(
            Settlement town, TownCivicState civic, FacilityMarker marker, StorageQualifier.Result result) {
        return storageSnapshotFor(town, civic, marker, result, Optional.empty(), 0L);
    }

    static FacilityDetailSnapshotPayload storageSnapshotFor(
            Settlement town, TownCivicState civic, FacilityMarker marker, StorageQualifier.Result result,
            Optional<DailyMealState> meal, long dayTime) {
        boolean active = result.qualified();
        ArrayList<Line> lines = new ArrayList<>();
        addStatus(lines, marker, active);
        lines.add(Line.section("Facility"));
        lines.add(Line.row("Recognized storage",
                result.storageBlocks() + " / " + StorageRules.MIN_STORAGE_BLOCKS,
                result.storageBlocks() >= StorageRules.MIN_STORAGE_BLOCKS ? Tone.GOOD : Tone.WARNING));
        lines.add(Line.row("Usable floor positions", Integer.toString(result.usableFloorPositions()),
                result.usableFloorPositions() >= StorageRules.MIN_USABLE_FLOOR_POSITIONS ? Tone.GOOD : Tone.WARNING));
        lines.add(Line.row("Dark floor positions", Integer.toString(result.darkFloorPositions()),
                result.darkFloorPositions() == 0 ? Tone.GOOD : Tone.WARNING));
        if (!active) {
            lines.add(Line.section("Diagnostics"));
            lines.add(Line.note(StorageService.qualificationMessage(result), Tone.WARNING));
        }
        lines.add(Line.section("Operations"));
        lines.add(Line.row("Daily Meal", DailyMealService.summary(meal),
                DailyMealService.warning(meal) ? Tone.WARNING : meal.isPresent() ? Tone.GOOD : Tone.MUTED));
        lines.add(Line.row("Next meal", DailyMealService.nextMealLabel(dayTime, meal), Tone.MUTED));
        meal.ifPresent(last -> {
            if (last.requiredNutrition() > 0) {
                lines.add(Line.row("Last target",
                        last.consumedNutrition() + " / " + last.requiredNutrition(),
                        last.warning() ? Tone.WARNING : Tone.GOOD));
            }
            lines.add(Line.row("Last source", sourceLabel(last.source()), Tone.MUTED));
            if (last.remainingKnownNutrition() >= 0) {
                lines.add(Line.row("Known reserve after meal", Long.toString(last.remainingKnownNutrition()), Tone.NORMAL));
            }
            if (last.unresolvedLootContainers() > 0) {
                lines.add(Line.note(last.unresolvedLootContainers()
                        + " unresolved loot container(s) were protected and skipped.", Tone.MUTED));
            }
        });
        lines.add(Line.section("Role"));
        lines.add(Line.note("Primary town food reserve. Once established, Daily Meal draws only from this registered facility.", Tone.NORMAL));
        return payload(town, civic, marker.type(), active, lines);
    }

    static FacilityDetailSnapshotPayload animalFarmSnapshotFor(
            Settlement town, TownCivicState civic, FacilityMarker marker, AnimalFarmQualifier.Result result) {
        boolean active = result.qualified();
        boolean buildingQualified = result.storageBlocks() >= AnimalFarmRules.MIN_STORAGE_BLOCKS
                && result.looms() >= AnimalFarmRules.MIN_LOOMS
                && result.reason() != AnimalFarmQualifier.Reason.ROOM_NOT_FOUND
                && result.reason() != AnimalFarmQualifier.Reason.ROOM_AMBIGUOUS
                && result.reason() != AnimalFarmQualifier.Reason.ROOM_INCOMPLETE;
        ArrayList<Line> lines = new ArrayList<>();
        addStatus(lines, marker, active);

        lines.add(Line.section("Facility"));
        lines.add(Line.row("Farm building", buildingQualified ? "Qualified" : "Unavailable",
                buildingQualified ? Tone.GOOD : Tone.WARNING));
        lines.add(Line.row("Recognized storage",
                result.storageBlocks() + " / " + AnimalFarmRules.MIN_STORAGE_BLOCKS,
                result.storageBlocks() >= AnimalFarmRules.MIN_STORAGE_BLOCKS ? Tone.GOOD : Tone.WARNING));
        lines.add(Line.row("Looms",
                result.looms() + " / " + AnimalFarmRules.MIN_LOOMS,
                result.looms() >= AnimalFarmRules.MIN_LOOMS ? Tone.GOOD : Tone.WARNING));

        lines.add(Line.section("Paddock"));
        if (buildingQualified) {
            lines.add(Line.row("Building connections",
                    result.paddockAttachments() + " / " + AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS,
                    result.paddockAttachments() >= AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS ? Tone.GOOD : Tone.WARNING));
            lines.add(Line.row("Connected fences/gates",
                    result.paddockBarriers() + " / " + AnimalFarmRules.MIN_PADDOCK_BARRIERS,
                    result.paddockBarriers() >= AnimalFarmRules.MIN_PADDOCK_BARRIERS ? Tone.GOOD : Tone.WARNING));
            String enclosure = enclosureLabel(result);
            lines.add(Line.row("Enclosure", enclosure,
                    result.paddockEnclosed() ? Tone.GOOD : Tone.WARNING));
        } else {
            lines.add(Line.row("Building connections", "Not evaluated", Tone.MUTED));
            lines.add(Line.row("Connected fences/gates", "Not evaluated", Tone.MUTED));
            lines.add(Line.row("Enclosure", "Not evaluated", Tone.MUTED));
        }

        if (!active) {
            lines.add(Line.section("Diagnostics"));
            lines.add(Line.note(AnimalFarmService.qualificationMessage(result), Tone.WARNING));
        }

        lines.add(Line.section("Livestock"));
        lines.add(Line.row("Production", "Not yet active", Tone.MUTED));
        lines.add(Line.row("Animals", "Not yet tracked", Tone.MUTED));
        lines.add(Line.section("Role"));
        lines.add(Line.note("Livestock production and animal-based town supplies.", Tone.NORMAL));
        return payload(town, civic, marker.type(), active, lines);
    }

    private static String sourceLabel(DailyMealState.Source source) {
        return switch (source) {
            case NONE -> "None";
            case TOWN_HALL -> "Town Hall";
            case STORAGE -> "Town Storage";
        };
    }

    private static String enclosureLabel(AnimalFarmQualifier.Result result) {
        if (result.paddockEnclosed()) return "Closed";
        return switch (result.reason()) {
            case PADDOCK_OPEN -> "Open";
            case PADDOCK_INCOMPLETE -> "Unavailable";
            case PADDOCK_TRACE_LIMIT_REACHED, PADDOCK_SCAN_LIMIT_REACHED -> "Validation limit";
            case NOT_ENOUGH_PADDOCK_ATTACHMENTS, NOT_ENOUGH_PADDOCK_BARRIERS -> "Not qualified";
            default -> "Not evaluated";
        };
    }

    private static FacilityDetailSnapshotPayload genericSnapshot(
            Settlement town, TownCivicState civic, FacilityMarker marker, boolean active,
            String diagnostic, String role, String note) {
        ArrayList<Line> lines = new ArrayList<>();
        addStatus(lines, marker, active);
        if (diagnostic != null) {
            lines.add(Line.section("Diagnostics"));
            lines.add(Line.note(diagnostic, Tone.WARNING));
        }
        lines.add(Line.section("Role"));
        lines.add(Line.note(role, Tone.NORMAL));
        lines.add(Line.note(note, Tone.MUTED));
        return payload(town, civic, marker.type(), active, lines);
    }

    private static void addStatus(ArrayList<Line> lines, FacilityMarker marker, boolean active) {
        lines.add(Line.section("Status"));
        lines.add(Line.row("State", active ? "Established — Active" : "Established — Unavailable",
                active ? Tone.GOOD : Tone.WARNING));
        lines.add(Line.row("Registered sign", position(marker.markerPosition()), Tone.MUTED));
    }

    private static FacilityDetailSnapshotPayload payload(
            Settlement town, TownCivicState civic, FacilityType type, boolean active, java.util.List<Line> lines) {
        return new FacilityDetailSnapshotPayload(town.id(), town.name(),
                civic.primaryColor().orElseThrow(), civic.secondaryColor().orElseThrow(), type, active, lines);
    }

    private static String position(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static Optional<FacilityDetailSnapshotPayload> fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return Optional.empty();
    }
}
