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

/** Server-authoritative Storage registration and explicit revalidation owner. */
public final class StorageService {
    private enum ExistingStatus { VALID, INVALID, UNAVAILABLE }
    private StorageService() {}

    public static boolean register(ServerPlayer player, InteractionHand hand, BlockPos markerPosition) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(hand);
        Objects.requireNonNull(markerPosition);
        var server = player.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Storage registration requires the server thread");
        }
        if (!player.isAlive() || player.isSpectator()) {
            return fail(player, "You must be an active player to register Storage.");
        }
        if (player.distanceToSqr(markerPosition.getX() + 0.5, markerPosition.getY() + 0.5,
                markerPosition.getZ() + 0.5) > 64.0) {
            return fail(player, "You must be within 8 blocks of the Storage sign.");
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
        if (!civic.isUnlocked(ProgressionUnlock.STORAGE)) {
            return fail(player, "Establish a Town Hall before registering town Storage.");
        }

        SignBlockEntity sign = signAt(level, markerPosition);
        if (sign == null) return fail(player, "The Storage sign is unavailable or no longer exists.");
        boolean front = sign.isFacingFrontText(player);
        SignText text = sign.getText(front);
        if (!StorageSignGrammar.matches(text)) {
            return fail(player, "Write [Hometown] on line 1 and Storage on line 2 of the interacted sign face.");
        }

        StorageQualifier.Result qualification = StorageQualifier.qualify(level, town, markerPosition);
        if (!qualification.qualified()) return fail(player, qualificationMessage(qualification));

        FacilityMarker current = civic.facility(FacilityType.STORAGE).orElse(null);
        if (current != null) {
            ExistingStatus status = existingStatus(level, town, current);
            if (status == ExistingStatus.VALID) {
                return fail(player, "Valid town Storage is already registered at " + position(current.markerPosition()) + ".");
            }
            if (status == ExistingStatus.UNAVAILABLE) {
                return fail(player, "The existing town Storage cannot be checked because part of it is unloaded or unavailable.");
            }
        }

        FacilityMarkerSide side = front ? FacilityMarkerSide.FRONT : FacilityMarkerSide.BACK;
        FacilityMarker marker = new FacilityMarker(town.id(), FacilityType.STORAGE,
                town.dimension(), markerPosition, side);
        var commit = data.registerStorage(town.id(), marker, level.getGameTime());
        if (!commit.changed()) return fail(player, "This Storage facility is already registered.");

        var primary = civic.primaryColor().orElseThrow();
        var secondary = civic.secondaryColor().orElseThrow();
        var visibleColor = text.getColor() == primary ? secondary : primary;
        sign.setText(text.setColor(visibleColor), front);
        sign.setChanged();
        var state = level.getBlockState(markerPosition);
        level.sendBlockUpdated(markerPosition, state, state, 3);

        player.sendSystemMessage(Component.literal(commit.firstEstablishment()
                ? town.name() + " has established its first Storage facility."
                : town.name() + " Storage registration has been restored."));
        return true;
    }

    /** Current Storage validity is derived only when an explicit operation needs it. */
    public static StorageQualifier.Result revalidate(ServerLevel level, Settlement town, FacilityMarker marker) {
        if (marker == null || marker.type() != FacilityType.STORAGE || !marker.settlementId().equals(town.id())
                || !marker.dimension().equals(level.dimension())) {
            return StorageQualifier.Result.failed(StorageQualifier.Reason.ROOM_NOT_FOUND);
        }
        SignBlockEntity sign = signAt(level, marker.markerPosition());
        if (sign == null) return StorageQualifier.Result.failed(StorageQualifier.Reason.ROOM_INCOMPLETE);
        if (marker.side() == FacilityMarkerSide.NONE) {
            return StorageQualifier.Result.failed(StorageQualifier.Reason.ROOM_NOT_FOUND);
        }
        boolean front = marker.side() == FacilityMarkerSide.FRONT;
        if (!StorageSignGrammar.matches(sign.getText(front))) {
            return StorageQualifier.Result.failed(StorageQualifier.Reason.ROOM_NOT_FOUND);
        }
        return StorageQualifier.qualify(level, town, marker.markerPosition());
    }

    private static ExistingStatus existingStatus(ServerLevel level, Settlement town, FacilityMarker marker) {
        if (!marker.dimension().equals(level.dimension())) return ExistingStatus.UNAVAILABLE;
        LevelChunk chunk = chunk(level, marker.markerPosition());
        if (chunk == null) return ExistingStatus.UNAVAILABLE;
        var entity = chunk.getBlockEntities().get(marker.markerPosition());
        if (!(entity instanceof SignBlockEntity sign)) return ExistingStatus.INVALID;
        if (marker.side() == FacilityMarkerSide.NONE) return ExistingStatus.INVALID;
        boolean front = marker.side() == FacilityMarkerSide.FRONT;
        if (!StorageSignGrammar.matches(sign.getText(front))) return ExistingStatus.INVALID;
        StorageQualifier.Result result = StorageQualifier.qualify(level, town, marker.markerPosition());
        if (result.reason() == StorageQualifier.Reason.ROOM_INCOMPLETE) return ExistingStatus.UNAVAILABLE;
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

    public static String qualificationMessage(StorageQualifier.Result result) {
        return switch (result.reason()) {
            case ROOM_NOT_FOUND -> "The Storage sign does not resolve to one enclosed room.";
            case ROOM_AMBIGUOUS -> "The Storage sign touches more than one enclosed room.";
            case ROOM_INCOMPLETE -> "Storage cannot be checked completely because required room data is unavailable.";
            case NO_USABLE_FLOOR -> "Storage needs at least one usable standing position inside the room.";
            case NOT_ENOUGH_STORAGE -> "Storage needs at least " + StorageRules.MIN_STORAGE_BLOCKS
                    + " recognized storage blocks. Found: " + result.storageBlocks() + ".";
            case UNSAFE_LIGHTING -> "Every usable Storage floor position must have block light of at least "
                    + StorageRules.MIN_BLOCK_LIGHT + ". Dark positions: " + result.darkFloorPositions() + ".";
            case QUALIFIED -> "Storage qualified.";
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
