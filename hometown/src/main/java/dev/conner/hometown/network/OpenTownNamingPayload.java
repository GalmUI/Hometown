package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The nonce identifies a naming attempt, never a settlement. */
public record OpenTownNamingPayload(BlockPos bellPosition, long nonce) implements CustomPacketPayload {
    public static final Type<OpenTownNamingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "open_town_naming"));
    public static final StreamCodec<FriendlyByteBuf, OpenTownNamingPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeBlockPos(value.bellPosition()); buffer.writeLong(value.nonce()); },
            buffer -> new OpenTownNamingPayload(buffer.readBlockPos(), buffer.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
