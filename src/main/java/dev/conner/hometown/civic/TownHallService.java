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

/** Server-authoritative R3 M1 Town Hall registration/revalidation owner. */
public final class TownHallService {
    private enum ExistingStatus { VALID, INVALID, UNAVAILABLE }
    private TownHallService() {}

    public static boolean register(ServerPlayer player, InteractionHand hand, BlockPos markerPosition) {
        Objects.requireNonNull(player); Objects.requireNonNull(hand); Objects.requireNonNull(markerPosition);
        var server = player.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Town Hall registration requires the server thread");
        if (!player.isAlive() || player.isSpectator()) return fail(player, "You must be an active player to register a Town Hall.");
        if (player.distanceToSqr(markerPosition.getX() + 0.5, markerPosition.getY() + 0.5, markerPosition.getZ() + 0.5) > 64.0) {
            return fail(player, "You must be within 8 blocks of the Town Hall sign.");
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
        if (!data.civicState(town.id()).colorsConfigured()) {
            return fail(player, "Choose this town's colors at its founding Bell before registering civic facilities.");
        }

        SignBlockEntity sign = signAt(level, markerPosition);
        if (sign == null) return fail(player, "The Town Hall sign is unavailable or no longer exists.");
        boolean front = sign.isFacingFrontText(player);
        SignText text = sign.getText(front);
        if (!TownHallSignGrammar.matches(text)) {
            return fail(player, "Write [Hometown] on line 1 and Town Hall on line 2 of the interacted sign face.");
        }

        TownHallQualifier.Result qualification = TownHallQualifier.qualify(level, town, markerPosition);
        if (!qualification.qualified()) return fail(player, qualificationMessage(qualification));

        var current = data.civicState(town.id()).facility(FacilityType.TOWN_HALL).orElse(null);
        if (current != null) {
            ExistingStatus status = existingStatus(level, town, current);
            if (status == ExistingStatus.VALID) {
                return fail(player, "A valid Town Hall is already registered at " + position(current.markerPosition()) + ".");
            }
            if (status == ExistingStatus.UNAVAILABLE) {
                return fail(player, "The existing Town Hall cannot be checked because part of it is unloaded or unavailable.");
            }
        }

        FacilityMarkerSide side = front ? FacilityMarkerSide.FRONT : FacilityMarkerSide.BACK;
        FacilityMarker marker = new FacilityMarker(town.id(), FacilityType.TOWN_HALL, town.dimension(), markerPosition, side);
        var commit = data.registerTownHall(town.id(), marker, level.getGameTime());
        if (!commit.changed()) return fail(player, "This Town Hall is already registered.");

        // Vanilla SignText applies one dye color to the whole interacted face. Pick the primary unless
        // it is already the face color; the distinct secondary then guarantees visible registration feedback.
        var civic = data.civicState(town.id());
        var primary = civic.primaryColor().orElseThrow();
        var secondary = civic.secondaryColor().orElseThrow();
        var visibleColor = text.getColor() == primary ? secondary : primary;
        sign.setText(text.setColor(visibleColor), front);
        sign.setChanged();
        var state = level.getBlockState(markerPosition);
        level.sendBlockUpdated(markerPosition, state, state, 3);

        player.sendSystemMessage(Component.literal(commit.firstEstablishment()
                ? town.name() + " has established its first Town Hall."
                : town.name() + " Town Hall registration has been restored."));
        return true;
    }

    /** Explicit revalidation used by later Administration access without persisting transient validity. */
    public static TownHallQualifier.Result revalidate(ServerLevel level, Settlement town, FacilityMarker marker) {
        if (marker == null || marker.type() != FacilityType.TOWN_HALL || !marker.settlementId().equals(town.id())
                || !marker.dimension().equals(level.dimension())) {
            return TownHallQualifier.Result.failed(TownHallQualifier.Reason.ROOM_NOT_FOUND);
        }
        SignBlockEntity sign = signAt(level, marker.markerPosition());
        if (sign == null) return TownHallQualifier.Result.failed(TownHallQualifier.Reason.ROOM_INCOMPLETE);
        boolean front = marker.side() == FacilityMarkerSide.FRONT;
        if (marker.side() == FacilityMarkerSide.NONE || !TownHallSignGrammar.matches(sign.getText(front))) {
            return TownHallQualifier.Result.failed(TownHallQualifier.Reason.ROOM_NOT_FOUND);
        }
        return TownHallQualifier.qualify(level, town, marker.markerPosition());
    }

    private static ExistingStatus existingStatus(ServerLevel level, Settlement town, FacilityMarker marker) {
        if (!marker.dimension().equals(level.dimension())) return ExistingStatus.UNAVAILABLE;
        LevelChunk chunk = chunk(level, marker.markerPosition());
        if (chunk == null) return ExistingStatus.UNAVAILABLE;
        var entity = chunk.getBlockEntities().get(marker.markerPosition());
        if (!(entity instanceof SignBlockEntity sign)) return ExistingStatus.INVALID;
        if (marker.side() == FacilityMarkerSide.NONE) return ExistingStatus.INVALID;
        boolean front = marker.side() == FacilityMarkerSide.FRONT;
        if (!TownHallSignGrammar.matches(sign.getText(front))) return ExistingStatus.INVALID;
        var result = TownHallQualifier.qualify(level, town, marker.markerPosition());
        if (result.reason() == TownHallQualifier.Reason.ROOM_INCOMPLETE) return ExistingStatus.UNAVAILABLE;
        return result.qualified() ? ExistingStatus.VALID : ExistingStatus.INVALID;
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

    private static String qualificationMessage(TownHallQualifier.Result result) {
        return switch (result.reason()) {
            case ROOM_NOT_FOUND -> "The sign does not resolve to one enclosed Town Hall room.";
            case ROOM_AMBIGUOUS -> "The sign touches more than one enclosed room; place it clearly within one Town Hall room.";
            case ROOM_INCOMPLETE -> "The Town Hall cannot be checked completely because required room data is unavailable.";
            case FLOOR_AREA_TOO_SMALL -> "The Town Hall needs at least " + TownHallRules.MIN_USABLE_FLOOR_POSITIONS
                    + " usable floor positions. Found: " + result.usableFloorPositions() + ".";
            case NOT_ENOUGH_BOOKSHELVES -> "The Town Hall needs at least " + TownHallRules.MIN_BOOKSHELVES
                    + " bookshelves. Found: " + result.bookshelves() + ".";
            case NO_LECTERN -> "The Town Hall needs a lectern.";
            case NO_STORAGE -> "The Town Hall needs at least one recognized storage block.";
            case UNSAFE_LIGHTING -> "Every usable Town Hall floor position must have block light of at least "
                    + TownHallRules.MIN_BLOCK_LIGHT + ". Dark positions: " + result.darkFloorPositions() + ".";
            case QUALIFIED -> "Town Hall qualified.";
        };
    }

    private static String position(BlockPos pos) { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return false;
    }
}
