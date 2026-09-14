package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.civic.LivestockSpecies;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One bounded +/- adjustment requested from an Animal Farm facility screen. */
public record UpdateAnimalFarmPolicyPayload(
        UUID settlementId,
        LivestockSpecies species,
        Setting setting,
        int delta) implements CustomPacketPayload {

    public enum Setting { BREEDING_PAIRS, CULL_ABOVE }

    public static final Type<UpdateAnimalFarmPolicyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "update_animal_farm_policy"));

    public static final StreamCodec<FriendlyByteBuf, UpdateAnimalFarmPolicyPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.settlementId());
                buffer.writeEnum(value.species());
                buffer.writeEnum(value.setting());
                buffer.writeInt(value.delta());
            },
            buffer -> new UpdateAnimalFarmPolicyPayload(
                    buffer.readUUID(), buffer.readEnum(LivestockSpecies.class),
                    buffer.readEnum(Setting.class), buffer.readInt()));

    public UpdateAnimalFarmPolicyPayload {
        Objects.requireNonNull(settlementId);
        Objects.requireNonNull(species);
        Objects.requireNonNull(setting);
        if (delta != -1 && delta != 1) throw new IllegalArgumentException("Animal Farm policy delta must be +/-1");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
