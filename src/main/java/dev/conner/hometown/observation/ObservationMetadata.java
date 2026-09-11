package dev.conner.hometown.observation;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Copied identity of one server-thread observation, never a persisted live score. */
public record ObservationMetadata(UUID settlementId, String dimension, long requestGeneration,
        long observedGameTime, long configurationRevision, long dataRevision, long boundsRevision) {
    public void write(FriendlyByteBuf b) {
        b.writeUUID(settlementId); b.writeUtf(dimension,256); b.writeLong(requestGeneration);
        b.writeLong(observedGameTime); b.writeLong(configurationRevision); b.writeLong(dataRevision); b.writeLong(boundsRevision);
    }
    public static ObservationMetadata read(FriendlyByteBuf b) {
        return new ObservationMetadata(b.readUUID(),b.readUtf(256),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readLong());
    }
}
