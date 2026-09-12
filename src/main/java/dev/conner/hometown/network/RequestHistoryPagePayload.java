package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests one page from the immutable History view captured by an existing Ledger generation. */
public record RequestHistoryPagePayload(int requestId,UUID settlementId,long generation,int page) implements CustomPacketPayload {
    public static final Type<RequestHistoryPagePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID,"ledger_history_request"));
    public static final StreamCodec<FriendlyByteBuf,RequestHistoryPagePayload> STREAM_CODEC=StreamCodec.of(RequestHistoryPagePayload::write,RequestHistoryPagePayload::read);
    public RequestHistoryPagePayload { java.util.Objects.requireNonNull(settlementId);if(page<0||page>4096)throw new IllegalArgumentException("Invalid History page"); }
    private static void write(FriendlyByteBuf b,RequestHistoryPagePayload p){b.writeInt(p.requestId());b.writeUUID(p.settlementId());b.writeLong(p.generation());b.writeVarInt(p.page());}
    private static RequestHistoryPagePayload read(FriendlyByteBuf b){return new RequestHistoryPagePayload(b.readInt(),b.readUUID(),b.readLong(),b.readVarInt());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
