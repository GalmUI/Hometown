package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/** Immutable server-authoritative snapshot used by the R3 M1 Town Administration screen. */
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
        boolean animalFarmsUnlocked) implements CustomPacketPayload {

    public static final int WIRE_NAME_LIMIT = 256;
    public static final Type<TownAdministrationSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "town_administration_snapshot"));

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
                buffer.writeBoolean(value.animalFarmsUnlocked());
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
                    buffer.readBoolean()));

    public TownAdministrationSnapshotPayload {
        if (settlementId == null || townName == null || primaryColor == null || secondaryColor == null) {
            throw new IllegalArgumentException("Town Administration snapshot requires complete identity data");
        }
        if (primaryColor == secondaryColor) {
            throw new IllegalArgumentException("Town Administration colors must differ");
        }
        if (townName.length() > WIRE_NAME_LIMIT) {
            throw new IllegalArgumentException("Town Administration town name exceeds wire limit");
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
