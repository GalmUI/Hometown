package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** Requests one-time color configuration at the clicked founding bell for the town linked to the held Ledger. */
public record RequestTownColorsPayload(InteractionHand hand, BlockPos bellPosition) implements CustomPacketPayload {
    public RequestTownColorsPayload {
        bellPosition = bellPosition.immutable();
    }

    public static final Type<RequestTownColorsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "request_town_colors"));
    public static final StreamCodec<FriendlyByteBuf, RequestTownColorsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeEnum(value.hand());
                buffer.writeBlockPos(value.bellPosition());
            },
            buffer -> new RequestTownColorsPayload(buffer.readEnum(InteractionHand.class), buffer.readBlockPos()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
