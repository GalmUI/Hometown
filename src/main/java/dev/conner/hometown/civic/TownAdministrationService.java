package dev.conner.hometown.civic;

import dev.conner.hometown.component.HometownDataComponents;
import dev.conner.hometown.component.SettlementIdComponent;
import dev.conner.hometown.item.HometownItems;
import dev.conner.hometown.network.TownAdministrationSnapshotPayload;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/** Server-authoritative gate and immutable snapshot owner for Town Administration. */
public final class TownAdministrationService {
    private TownAdministrationService() {}

    public static Optional<TownAdministrationSnapshotPayload> open(
            ServerPlayer player, InteractionHand hand, BlockPos lecternPosition) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(hand);
        Objects.requireNonNull(lecternPosition);

        var server = player.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Town Administration access requires the server thread");
        }
        if (!player.isAlive() || player.isSpectator()) {
            return fail(player, "You must be an active player to use Town Administration.");
        }
        if (player.distanceToSqr(lecternPosition.getX() + 0.5, lecternPosition.getY() + 0.5,
                lecternPosition.getZ() + 0.5) > 64.0) {
            return fail(player, "You must be within 8 blocks of the Town Hall lectern.");
        }

        ItemStack ledger = player.getItemInHand(hand);
        if (!ledger.is(HometownItems.TOWN_LEDGER.get())) return Optional.empty();
        SettlementIdComponent link = ledger.get(HometownDataComponents.SETTLEMENT_ID.get());
        if (link == null) return fail(player, "This Town Ledger is not linked to a known Hometown.");

        HometownSavedData data = HometownSavedData.get(server);
        Settlement town = data.getSettlement(link.settlementId()).orElse(null);
        if (town == null) return fail(player, "This Town Ledger no longer points to a known Hometown.");

        ServerLevel level = player.serverLevel();
        if (!town.dimension().equals(level.dimension()) || !town.contains(level.dimension(), lecternPosition)) {
            return fail(player, "That lectern is not inside the Hometown linked to this Ledger.");
        }

        LevelChunk lecternChunk = chunk(level, lecternPosition);
        if (lecternChunk == null) {
            return fail(player, "The Town Hall lectern is currently unavailable because its chunk is not loaded.");
        }
        if (!lecternChunk.getBlockState(lecternPosition).is(Blocks.LECTERN)) {
            return fail(player, "That block is no longer a Town Hall lectern.");
        }

        TownCivicState civic = data.civicState(town.id());
        FacilityMarker marker = civic.facility(FacilityType.TOWN_HALL).orElse(null);
        if (marker == null || !civic.isUnlocked(ProgressionUnlock.TOWN_HALL)) {
            return fail(player, "This Hometown has not established a Town Hall yet.");
        }

        TownHallQualifier.Result validation = TownHallService.revalidate(level, town, marker);
        if (!validation.qualified()) {
            return fail(player, TownHallService.qualificationMessage(validation));
        }
        if (!isLecternInValidatedHall(validation, lecternPosition)) {
            return fail(player, "Use the lectern inside the registered Town Hall to open Administration.");
        }

        // Build one immutable Administration snapshot. Storage is revalidated once here when it exists;
        // client-side page changes operate only on this snapshot and never rescan the world.
        boolean storageActive = civic.facility(FacilityType.STORAGE)
                .map(storage -> StorageService.revalidate(level, town, storage).qualified())
                .orElse(false);
        return Optional.of(snapshotFor(town, civic, storageActive));
    }

    static TownAdministrationSnapshotPayload snapshotFor(Settlement town, TownCivicState civic) {
        return snapshotFor(town, civic, false);
    }

    static TownAdministrationSnapshotPayload snapshotFor(
            Settlement town, TownCivicState civic, boolean storageActive) {
        Objects.requireNonNull(town);
        Objects.requireNonNull(civic);
        var primary = civic.primaryColor().orElseThrow();
        var secondary = civic.secondaryColor().orElseThrow();
        boolean storageEstablished = civic.facility(FacilityType.STORAGE).isPresent();
        return new TownAdministrationSnapshotPayload(
                town.id(),
                town.name(),
                primary,
                secondary,
                civic.isUnlocked(ProgressionUnlock.TOWN_HALL),
                true,
                civic.isUnlocked(ProgressionUnlock.NOTICE_BOARD),
                civic.isUnlocked(ProgressionUnlock.CIVIC_PROJECTS),
                civic.isUnlocked(ProgressionUnlock.STORAGE),
                storageEstablished,
                storageEstablished && storageActive,
                civic.isUnlocked(ProgressionUnlock.ANIMAL_FARMS));
    }

    static boolean isLecternInValidatedHall(TownHallQualifier.Result validation, BlockPos lecternPosition) {
        if (validation == null || !validation.qualified() || validation.room() == null || lecternPosition == null) return false;
        return validation.room().interior().contains(lecternPosition)
                || validation.room().boundary().contains(lecternPosition);
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
    }

    private static Optional<TownAdministrationSnapshotPayload> fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return Optional.empty();
    }
}
