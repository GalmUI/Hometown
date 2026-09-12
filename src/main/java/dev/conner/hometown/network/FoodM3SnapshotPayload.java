package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.food.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Companion to one Ledger response. Both snapshots are collected in the same server observation and never trigger a subpage scan. */
public record FoodM3SnapshotPayload(int requestId, FoodVarietySnapshot variety, FoodGrowingSnapshot growing) implements CustomPacketPayload {
    public static final Type<FoodM3SnapshotPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID,"ledger_food_m3"));
    public static final StreamCodec<FriendlyByteBuf,FoodM3SnapshotPayload> STREAM_CODEC=StreamCodec.of(FoodM3SnapshotPayload::write,FoodM3SnapshotPayload::read);
    public FoodM3SnapshotPayload {
        java.util.Objects.requireNonNull(variety);java.util.Objects.requireNonNull(growing);
        if(!variety.metadata().equals(growing.metadata()))throw new IllegalArgumentException("Mismatched Food M3 observation");
    }
    private static void write(FriendlyByteBuf buffer,FoodM3SnapshotPayload payload){buffer.writeInt(payload.requestId());payload.variety().write(buffer);payload.growing().write(buffer);}
    private static FoodM3SnapshotPayload read(FriendlyByteBuf buffer){return new FoodM3SnapshotPayload(buffer.readInt(),FoodVarietySnapshot.read(buffer),FoodGrowingSnapshot.read(buffer));}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
