package dev.conner.hometown.civic;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Durable facility anchor. Current facility validity is always derived live. */
public record FacilityMarker(UUID settlementId, FacilityType type, ResourceKey<Level> dimension,
                             BlockPos markerPosition, FacilityMarkerSide side) {
    public FacilityMarker {
        Objects.requireNonNull(settlementId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(dimension);
        markerPosition = Objects.requireNonNull(markerPosition).immutable();
        Objects.requireNonNull(side);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("SettlementId", settlementId);
        tag.putString("Type", type.name());
        tag.putString("Dimension", dimension.location().toString());
        tag.putLong("MarkerPosition", markerPosition.asLong());
        tag.putString("Side", side.name());
        return tag;
    }

    public static FacilityMarker fromTag(CompoundTag tag) {
        if (!tag.hasUUID("SettlementId") || !tag.contains("Type", Tag.TAG_STRING)
                || !tag.contains("Dimension", Tag.TAG_STRING) || !tag.contains("MarkerPosition", Tag.TAG_LONG)
                || !tag.contains("Side", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Incomplete civic facility marker");
        }
        try {
            return new FacilityMarker(tag.getUUID("SettlementId"), FacilityType.valueOf(tag.getString("Type")),
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                    BlockPos.of(tag.getLong("MarkerPosition")), FacilityMarkerSide.valueOf(tag.getString("Side")));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid civic facility marker", ex);
        }
    }
}
