package dev.conner.hometown.civic;

import dev.conner.hometown.component.HometownDataComponents;
import dev.conner.hometown.component.SettlementIdComponent;
import dev.conner.hometown.item.HometownItems;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;

/** Server-authoritative Animal Farm registration and explicit revalidation owner. */
public final class AnimalFarmService {
    private enum ExistingStatus { VALID, INVALID, UNAVAILABLE }
    private AnimalFarmService() {}

    public static boolean register(ServerPlayer player, InteractionHand hand, BlockPos markerPosition) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(hand);
        Objects.requireNonNull(markerPosition);
        var server = player.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Animal Farm registration requires the server thread");
        }
        if (!player.isAlive() || player.isSpectator()) {
            return fail(player, "You must be an active player to register an Animal Farm.");
        }
        if (player.distanceToSqr(markerPosition.getX() + 0.5, markerPosition.getY() + 0.5,
                markerPosition.getZ() + 0.5) > 64.0) {
            return fail(player, "You must be within 8 blocks of the Animal Farm sign.");
        }

        ItemStack ledger = player.getItemInHand(hand);
        if (!ledger.is(HometownItems.TOWN_LEDGER.get())) return false;
        SettlementIdComponent link = ledger.get(HometownDataComponents.SETTLEMENT_ID.get());
        if (link == null) return fail(player, "This Town Ledger is not linked to a known Hometown.");

        HometownSavedData data = HometownSavedData.get(server);
        Settlement town = data.getSettlement(link.settlementId()).orElse(null);
        if (town == null) return fail(player, "This Town Ledger no longer points to a known Hometown.");
        ServerLevel level = player.serverLevel();
        if (!town.dimension().equals(level.dimension()) || !town.contains(level.dimension(), markerPosition)) {
            return fail(player, "That sign is not inside the Hometown linked to this Ledger.");
        }

        TownCivicState civic = data.civicState(town.id());
        if (!civic.colorsConfigured()) {
            return fail(player, "Choose this town's colors at its founding Bell before registering civic facilities.");
        }
        if (!civic.isUnlocked(ProgressionUnlock.ANIMAL_FARMS)) {
            return fail(player, "Establish a Town Hall before registering an Animal Farm.");
        }

        SignBlockEntity sign = signAt(level, markerPosition);
        if (sign == null) return fail(player, "The Animal Farm sign is unavailable or no longer exists.");
        boolean front = sign.isFacingFrontText(player);
        SignText text = sign.getText(front);
        if (!AnimalFarmSignGrammar.matches(text)) {
            return fail(player, "Write [Hometown] on line 1 and Animal Farm on line 2 of the interacted sign face.");
        }

        AnimalFarmQualifier.Result qualification = AnimalFarmQualifier.qualify(level, town, markerPosition);
        if (!qualification.qualified()) return fail(player, qualificationMessage(qualification));

        FacilityMarker current = civic.facility(FacilityType.ANIMAL_FARM).orElse(null);
        if (current != null) {
            ExistingStatus status = existingStatus(level, town, current);
            if (status == ExistingStatus.VALID) {
                return fail(player, "A valid Animal Farm is already registered at " + position(current.markerPosition()) + ".");
            }
            if (status == ExistingStatus.UNAVAILABLE) {
                return fail(player, "The existing Animal Farm cannot be checked because part of it is unloaded or unavailable.");
            }
        }

        FacilityMarkerSide side = front ? FacilityMarkerSide.FRONT : FacilityMarkerSide.BACK;
        FacilityMarker marker = new FacilityMarker(town.id(), FacilityType.ANIMAL_FARM,
                town.dimension(), markerPosition, side);
        var commit = data.registerAnimalFarm(town.id(), marker, level.getGameTime());
        if (!commit.changed()) return fail(player, "This Animal Farm is already registered.");

        var primary = civic.primaryColor().orElseThrow();
        var secondary = civic.secondaryColor().orElseThrow();
        var visibleColor = text.getColor() == primary ? secondary : primary;
        sign.setText(text.setColor(visibleColor), front);
        sign.setChanged();
        var state = level.getBlockState(markerPosition);
        level.sendBlockUpdated(markerPosition, state, state, 3);

        player.sendSystemMessage(Component.literal(commit.firstEstablishment()
                ? town.name() + " has established its first Animal Farm."
                : town.name() + " Animal Farm registration has been restored."));
        return true;
    }

    /** Current Animal Farm validity is derived only when an explicit operation needs it. */
    public static AnimalFarmQualifier.Result revalidate(ServerLevel level, Settlement town, FacilityMarker marker) {
        if (marker == null || marker.type() != FacilityType.ANIMAL_FARM || !marker.settlementId().equals(town.id())
                || !marker.dimension().equals(level.dimension())) {
            return AnimalFarmQualifier.Result.failed(AnimalFarmQualifier.Reason.ROOM_NOT_FOUND);
        }
        SignBlockEntity sign = signAt(level, marker.markerPosition());
        if (sign == null) return AnimalFarmQualifier.Result.failed(AnimalFarmQualifier.Reason.ROOM_INCOMPLETE);
        if (marker.side() == FacilityMarkerSide.NONE) {
            return AnimalFarmQualifier.Result.failed(AnimalFarmQualifier.Reason.ROOM_NOT_FOUND);
        }
        boolean front = marker.side() == FacilityMarkerSide.FRONT;
        if (!AnimalFarmSignGrammar.matches(sign.getText(front))) {
            return AnimalFarmQualifier.Result.failed(AnimalFarmQualifier.Reason.ROOM_NOT_FOUND);
        }
        return AnimalFarmQualifier.qualify(level, town, marker.markerPosition());
    }

    private static ExistingStatus existingStatus(ServerLevel level, Settlement town, FacilityMarker marker) {
        if (!marker.dimension().equals(level.dimension())) return ExistingStatus.UNAVAILABLE;
        LevelChunk chunk = chunk(level, marker.markerPosition());
        if (chunk == null) return ExistingStatus.UNAVAILABLE;
        var entity = chunk.getBlockEntities().get(marker.markerPosition());
        if (!(entity instanceof SignBlockEntity sign)) return ExistingStatus.INVALID;
        if (marker.side() == FacilityMarkerSide.NONE) return ExistingStatus.INVALID;
        boolean front = marker.side() == FacilityMarkerSide.FRONT;
        if (!AnimalFarmSignGrammar.matches(sign.getText(front))) return ExistingStatus.INVALID;
        AnimalFarmQualifier.Result result = AnimalFarmQualifier.qualify(level, town, marker.markerPosition());
        return unavailable(result.reason()) ? ExistingStatus.UNAVAILABLE
                : result.qualified() ? ExistingStatus.VALID : ExistingStatus.INVALID;
    }

    private static boolean unavailable(AnimalFarmQualifier.Reason reason) {
        return reason == AnimalFarmQualifier.Reason.ROOM_INCOMPLETE
                || reason == AnimalFarmQualifier.Reason.PADDOCK_INCOMPLETE
                || reason == AnimalFarmQualifier.Reason.PADDOCK_TRACE_LIMIT_REACHED
                || reason == AnimalFarmQualifier.Reason.PADDOCK_SCAN_LIMIT_REACHED;
    }

    private static SignBlockEntity signAt(ServerLevel level, BlockPos position) {
        LevelChunk chunk = chunk(level, position);
        if (chunk == null) return null;
        var entity = chunk.getBlockEntities().get(position);
        return entity instanceof SignBlockEntity sign ? sign : null;
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    public static String qualificationMessage(AnimalFarmQualifier.Result result) {
        return switch (result.reason()) {
            case ROOM_NOT_FOUND -> "The Animal Farm sign does not resolve to one enclosed farm building.";
            case ROOM_AMBIGUOUS -> "The Animal Farm sign touches more than one enclosed room.";
            case ROOM_INCOMPLETE -> "The farm building cannot be checked completely because required room data is unavailable.";
            case NO_STORAGE -> "The farm building needs at least one recognized storage block.";
            case NO_LOOM -> "The farm building needs a loom.";
            case NOT_ENOUGH_PADDOCK_ATTACHMENTS -> "The paddock must connect to the farm building at at least "
                    + AnimalFarmRules.MIN_PADDOCK_ATTACHMENTS + " fence or gate positions. Found: "
                    + result.paddockAttachments() + ".";
            case NOT_ENOUGH_PADDOCK_BARRIERS -> "The attached paddock needs at least "
                    + AnimalFarmRules.MIN_PADDOCK_BARRIERS + " connected fences/gates. Found: "
                    + result.paddockBarriers() + ".";
            case PADDOCK_OPEN -> "The attached fence/gate network does not form an enclosed paddock.";
            case PADDOCK_INCOMPLETE -> "The paddock cannot be checked completely because part of the connected fence area is unloaded.";
            case PADDOCK_TRACE_LIMIT_REACHED -> "The paddock fence network is too large to validate safely.";
            case PADDOCK_SCAN_LIMIT_REACHED -> "The paddock enclosure footprint is too large to validate safely.";
            case QUALIFIED -> "Animal Farm qualified.";
        };
    }

    private static String position(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return false;
    }
}
