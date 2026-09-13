package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** Requests one-time color configuration for the town linked to the held Ledger. */
public record RequestTownColorsPayload(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<RequestTownColorsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "request_town_colors"));
    public static final StreamCodec<FriendlyByteBuf, RequestTownColorsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeEnum(value.hand()),
            buffer -> new RequestTownColorsPayload(buffer.readEnum(InteractionHand.class)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
