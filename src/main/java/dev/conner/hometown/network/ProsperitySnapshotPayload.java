package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.prosperity.ProsperitySnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cached Prosperity companion for one Ledger generation. */
public record ProsperitySnapshotPayload(int requestId, ProsperitySnapshot prosperity) implements CustomPacketPayload {
    public static final Type<ProsperitySnapshotPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID,"ledger_prosperity"));
    public static final StreamCodec<FriendlyByteBuf,ProsperitySnapshotPayload> STREAM_CODEC=StreamCodec.of(ProsperitySnapshotPayload::write,ProsperitySnapshotPayload::read);
    public ProsperitySnapshotPayload { java.util.Objects.requireNonNull(prosperity); }
    private static void write(FriendlyByteBuf buffer,ProsperitySnapshotPayload payload){buffer.writeInt(payload.requestId());payload.prosperity().write(buffer);}
    private static ProsperitySnapshotPayload read(FriendlyByteBuf buffer){return new ProsperitySnapshotPayload(buffer.readInt(),ProsperitySnapshot.read(buffer));}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
