package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;

/** Server-authoritative one-time color configuration request for an existing town. */
public record SubmitTownColorsPayload(UUID settlementId, InteractionHand hand,
                                      DyeColor primary, DyeColor secondary) implements CustomPacketPayload {
    public SubmitTownColorsPayload {
        if (settlementId == null || hand == null || primary == null || secondary == null) {
            throw new IllegalArgumentException("Incomplete town color submission");
        }
    }

    public static final Type<SubmitTownColorsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "submit_town_colors"));
    public static final StreamCodec<FriendlyByteBuf, SubmitTownColorsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.settlementId());
                buffer.writeEnum(value.hand());
                buffer.writeEnum(value.primary());
                buffer.writeEnum(value.secondary());
            },
            buffer -> new SubmitTownColorsPayload(buffer.readUUID(), buffer.readEnum(InteractionHand.class),
                    buffer.readEnum(DyeColor.class), buffer.readEnum(DyeColor.class)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
