package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public record SubmitTownNamePayload(BlockPos bellPosition, long nonce, String name,
                                    DyeColor primary, DyeColor secondary) implements CustomPacketPayload {
    // Allows 32 supplementary Unicode characters plus whitespace; semantic limit enforced on server.
    public static final int WIRE_NAME_LIMIT = 256;

    public SubmitTownNamePayload {
        if (bellPosition == null || name == null || primary == null || secondary == null) {
            throw new IllegalArgumentException("Incomplete founding submission");
        }
    }

    public static final Type<SubmitTownNamePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "submit_town_name"));
    public static final StreamCodec<FriendlyByteBuf, SubmitTownNamePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBlockPos(value.bellPosition());
                buffer.writeLong(value.nonce());
                buffer.writeUtf(value.name(), WIRE_NAME_LIMIT);
                buffer.writeEnum(value.primary());
                buffer.writeEnum(value.secondary());
            },
            buffer -> new SubmitTownNamePayload(buffer.readBlockPos(), buffer.readLong(), buffer.readUtf(WIRE_NAME_LIMIT),
                    buffer.readEnum(DyeColor.class), buffer.readEnum(DyeColor.class)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
