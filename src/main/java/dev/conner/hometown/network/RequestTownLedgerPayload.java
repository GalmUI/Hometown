package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** No client-supplied town identity. Hand is checked against the server inventory. */
public record RequestTownLedgerPayload(InteractionHand hand, int page, int requestId, long generation) implements CustomPacketPayload {
    public RequestTownLedgerPayload(InteractionHand hand,int page,int requestId) { this(hand,page,requestId,-1); }
    public RequestTownLedgerPayload { if(page < -1 || page > 1000000 || generation < -1) throw new IllegalArgumentException("Invalid Ledger request"); }
    public static final Type<RequestTownLedgerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "request_ledger"));
    public static final StreamCodec<FriendlyByteBuf, RequestTownLedgerPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeEnum(p.hand()); b.writeVarInt(p.page()); b.writeInt(p.requestId()); b.writeLong(p.generation()); },
            b -> new RequestTownLedgerPayload(b.readEnum(InteractionHand.class), b.readVarInt(), b.readInt(), b.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
