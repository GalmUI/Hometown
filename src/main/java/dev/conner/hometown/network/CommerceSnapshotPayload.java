package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.commerce.CommerceSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cached Commerce companion for one Ledger generation. */
public record CommerceSnapshotPayload(int requestId, CommerceSnapshot commerce) implements CustomPacketPayload {
    public static final Type<CommerceSnapshotPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID,"ledger_commerce"));
    public static final StreamCodec<FriendlyByteBuf,CommerceSnapshotPayload> STREAM_CODEC=StreamCodec.of(CommerceSnapshotPayload::write,CommerceSnapshotPayload::read);
    public CommerceSnapshotPayload { java.util.Objects.requireNonNull(commerce); }
    private static void write(FriendlyByteBuf buffer,CommerceSnapshotPayload payload){buffer.writeInt(payload.requestId());payload.commerce().write(buffer);}
    private static CommerceSnapshotPayload read(FriendlyByteBuf buffer){return new CommerceSnapshotPayload(buffer.readInt(),CommerceSnapshot.read(buffer));}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
