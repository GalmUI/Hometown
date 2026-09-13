package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/** Immutable server-authoritative snapshot used by the R3 Town Administration screen. */
public record TownAdministrationSnapshotPayload(
        UUID settlementId,
        String townName,
        DyeColor primaryColor,
        DyeColor secondaryColor,
        boolean townHallEstablished,
        boolean townHallActive,
        boolean noticeBoardUnlocked,
        boolean civicProjectsUnlocked,
        boolean storageUnlocked,
        boolean storageEstablished,
        boolean storageActive,
        boolean animalFarmsUnlocked,
        boolean animalFarmEstablished,
        boolean animalFarmActive,
        String dailyMealSummary,
        boolean dailyMealWarning) implements CustomPacketPayload {

    public static final int WIRE_NAME_LIMIT = 256;
    public static final Type<TownAdministrationSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "town_administration_snapshot"));

    /** Compatibility constructor for pre-0.11 tests/callers that do not yet supply meal state. */
    public TownAdministrationSnapshotPayload(
            UUID settlementId, String townName, DyeColor primaryColor, DyeColor secondaryColor,
            boolean townHallEstablished, boolean townHallActive, boolean noticeBoardUnlocked,
            boolean civicProjectsUnlocked, boolean storageUnlocked, boolean storageEstablished,
            boolean storageActive, boolean animalFarmsUnlocked, boolean animalFarmEstablished,
            boolean animalFarmActive) {
        this(settlementId, townName, primaryColor, secondaryColor, townHallEstablished, townHallActive,
                noticeBoardUnlocked, civicProjectsUnlocked, storageUnlocked, storageEstablished, storageActive,
                animalFarmsUnlocked, animalFarmEstablished, animalFarmActive, "Not yet processed", false);
    }

    public static final StreamCodec<FriendlyByteBuf, TownAdministrationSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.settlementId());
                buffer.writeUtf(value.townName(), WIRE_NAME_LIMIT);
                buffer.writeEnum(value.primaryColor());
                buffer.writeEnum(value.secondaryColor());
                buffer.writeBoolean(value.townHallEstablished());
                buffer.writeBoolean(value.townHallActive());
                buffer.writeBoolean(value.noticeBoardUnlocked());
                buffer.writeBoolean(value.civicProjectsUnlocked());
                buffer.writeBoolean(value.storageUnlocked());
                buffer.writeBoolean(value.storageEstablished());
                buffer.writeBoolean(value.storageActive());
                buffer.writeBoolean(value.animalFarmsUnlocked());
                buffer.writeBoolean(value.animalFarmEstablished());
                buffer.writeBoolean(value.animalFarmActive());
                buffer.writeUtf(value.dailyMealSummary(), WIRE_NAME_LIMIT);
                buffer.writeBoolean(value.dailyMealWarning());
            },
            buffer -> new TownAdministrationSnapshotPayload(
                    buffer.readUUID(),
                    buffer.readUtf(WIRE_NAME_LIMIT),
                    buffer.readEnum(DyeColor.class),
                    buffer.readEnum(DyeColor.class),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(WIRE_NAME_LIMIT),
                    buffer.readBoolean()));

    public TownAdministrationSnapshotPayload {
        if (settlementId == null || townName == null || primaryColor == null || secondaryColor == null
                || dailyMealSummary == null) {
            throw new IllegalArgumentException("Town Administration snapshot requires complete identity data");
        }
        if (primaryColor == secondaryColor) {
            throw new IllegalArgumentException("Town Administration colors must differ");
        }
        if (townName.length() > WIRE_NAME_LIMIT || dailyMealSummary.length() > WIRE_NAME_LIMIT) {
            throw new IllegalArgumentException("Town Administration text exceeds wire limit");
        }
        if (storageEstablished && !storageUnlocked) {
            throw new IllegalArgumentException("Established Storage must be unlocked");
        }
        if (storageActive && !storageEstablished) {
            throw new IllegalArgumentException("Active Storage must be established");
        }
        if (animalFarmEstablished && !animalFarmsUnlocked) {
            throw new IllegalArgumentException("Established Animal Farm must be unlocked");
        }
        if (animalFarmActive && !animalFarmEstablished) {
            throw new IllegalArgumentException("Active Animal Farm must be established");
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
