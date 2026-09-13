package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** Opens one-time town color selection for a server-validated linked Ledger. */
public record OpenTownColorsPayload(UUID settlementId, String townName, InteractionHand hand) implements CustomPacketPayload {
    public static final int WIRE_NAME_LIMIT = 256;
    public static final Type<OpenTownColorsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "open_town_colors"));
    public static final StreamCodec<FriendlyByteBuf, OpenTownColorsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.settlementId());
                buffer.writeUtf(value.townName(), WIRE_NAME_LIMIT);
                buffer.writeEnum(value.hand());
            },
            buffer -> new OpenTownColorsPayload(buffer.readUUID(), buffer.readUtf(WIRE_NAME_LIMIT),
                    buffer.readEnum(InteractionHand.class)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
