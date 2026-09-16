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

/** Server-authoritative 0.13.0 Notice Board registration and live validity owner. */
public final class NoticeBoardService {
    public enum Validation { ACTIVE, INVALID, UNAVAILABLE }
    record Commit(boolean changed, boolean firstEstablishment) {}

    private NoticeBoardService() {}

    public static boolean register(ServerPlayer player, InteractionHand hand, BlockPos markerPosition) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(hand);
        Objects.requireNonNull(markerPosition);
        var server = player.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Notice Board registration requires the server thread");
        }
        if (!player.isAlive() || player.isSpectator()) {
            return fail(player, "You must be an active player to register a Notice Board.");
        }
        if (player.distanceToSqr(markerPosition.getX() + 0.5, markerPosition.getY() + 0.5,
                markerPosition.getZ() + 0.5) > 64.0) {
            return fail(player, "You must be within 8 blocks of the Notice Board sign.");
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
        if (!civic.isUnlocked(ProgressionUnlock.NOTICE_BOARD)) {
            return fail(player, "Establish a Town Hall before registering a Notice Board.");
        }

        SignBlockEntity sign = signAt(level, markerPosition);
        if (sign == null) return fail(player, "The Notice Board sign is unavailable or no longer exists.");
        boolean front = sign.isFacingFrontText(player);
        SignText text = sign.getText(front);
        if (!NoticeBoardSignGrammar.matches(text)) {
            return fail(player, "Write [Hometown] on line 1 and Notice Board on line 2 of the interacted sign face.");
        }

        FacilityMarker current = civic.facility(FacilityType.NOTICE_BOARD).orElse(null);
        if (current != null) {
            Validation validation = revalidate(level, town, current);
            if (validation == Validation.ACTIVE) {
                return fail(player, "A valid Notice Board is already registered at " + position(current.markerPosition()) + ".");
            }
            if (validation == Validation.UNAVAILABLE) {
                return fail(player, "The existing Notice Board cannot be checked because its sign is unloaded or unavailable.");
            }
        }

        FacilityMarkerSide side = front ? FacilityMarkerSide.FRONT : FacilityMarkerSide.BACK;
        FacilityMarker marker = new FacilityMarker(town.id(), FacilityType.NOTICE_BOARD,
                town.dimension(), markerPosition, side);
        Commit commit = commit(data, town, marker);
        if (!commit.changed()) return fail(player, "This Notice Board is already registered.");

        var primary = civic.primaryColor().orElseThrow();
        var secondary = civic.secondaryColor().orElseThrow();
        var visibleColor = text.getColor() == primary ? secondary : primary;
        sign.setText(text.setColor(visibleColor), front);
        sign.setChanged();
        var state = level.getBlockState(markerPosition);
        level.sendBlockUpdated(markerPosition, state, state, 3);

        player.sendSystemMessage(Component.literal(commit.firstEstablishment()
                ? town.name() + " has established its first Notice Board."
                : town.name() + " Notice Board registration has been restored."));
        return true;
    }

    static Commit commit(HometownSavedData data, Settlement town, FacilityMarker marker) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(town);
        Objects.requireNonNull(marker);
        if (marker.type() != FacilityType.NOTICE_BOARD || !marker.settlementId().equals(town.id())
                || !marker.dimension().equals(town.dimension())) {
            throw new IllegalArgumentException("Invalid Notice Board facility marker");
        }
        TownCivicState current = data.civicState(town.id());
        if (!current.colorsConfigured()) throw new IllegalStateException("Town colors must be configured first");
        if (!current.isUnlocked(ProgressionUnlock.NOTICE_BOARD)) {
            throw new IllegalStateException("Notice Board progression is not unlocked");
        }
        boolean first = current.facility(FacilityType.NOTICE_BOARD).isEmpty();
        boolean changed = data.updateCivicState(town.id(), state -> state.withFacility(marker));
        return new Commit(changed, changed && first);
    }

    /** Current validity is deliberately derived from the registered sign and never persisted. */
    public static Validation revalidate(ServerLevel level, Settlement town, FacilityMarker marker) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(town);
        if (marker == null || marker.type() != FacilityType.NOTICE_BOARD
                || !marker.settlementId().equals(town.id())) return Validation.INVALID;
        if (!marker.dimension().equals(level.dimension())) return Validation.UNAVAILABLE;
        LevelChunk chunk = chunk(level, marker.markerPosition());
        if (chunk == null) return Validation.UNAVAILABLE;
        var entity = chunk.getBlockEntities().get(marker.markerPosition());
        if (!(entity instanceof SignBlockEntity sign) || marker.side() == FacilityMarkerSide.NONE) {
            return Validation.INVALID;
        }
        boolean front = marker.side() == FacilityMarkerSide.FRONT;
        return NoticeBoardSignGrammar.matches(sign.getText(front)) ? Validation.ACTIVE : Validation.INVALID;
    }

    public static String validationMessage(Validation validation) {
        return switch (validation) {
            case ACTIVE -> "Notice Board active.";
            case INVALID -> "The registered Notice Board sign is missing or its first two lines no longer match the Notice Board marker.";
            case UNAVAILABLE -> "The registered Notice Board sign is currently unloaded or unavailable.";
        };
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

    private static String position(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return false;
    }
}
