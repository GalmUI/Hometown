package dev.conner.hometown.settlement;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Immutable identity record. Bell destruction does not change this record. */
public record Settlement(UUID id, String name, ResourceKey<Level> dimension, BlockPos bellPosition,
                         int radius, UUID founderUuid, String founderName, long foundedGameTime) {
    public Settlement {
        Objects.requireNonNull(id);
        name = TownNames.validate(name);
        Objects.requireNonNull(dimension);
        bellPosition = Objects.requireNonNull(bellPosition).immutable();
        if (radius < 1) throw new IllegalArgumentException("Settlement radius must be positive");
        Objects.requireNonNull(founderUuid);
        Objects.requireNonNull(founderName);
    }

    public long foundedDay() { return foundedGameTime / 24000L; }

    public boolean contains(ResourceKey<Level> level, BlockPos pos) {
        return dimension.equals(level) && horizontalDistanceSquared(bellPosition, pos) <= (double)radius * radius;
    }

    public boolean overlaps(ResourceKey<Level> level, BlockPos pos, int otherRadius) {
        double separation = (double)radius + otherRadius;
        return dimension.equals(level) && horizontalDistanceSquared(bellPosition, pos) < separation * separation;
    }

    private static double horizontalDistanceSquared(BlockPos a, BlockPos b) {
        double x = (double)a.getX() - b.getX();
        double z = (double)a.getZ() - b.getZ();
        return x * x + z * z;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Dimension", dimension.location().toString());
        tag.putLong("BellPosition", bellPosition.asLong());
        tag.putInt("Radius", radius);
        tag.putUUID("FounderUuid", founderUuid);
        tag.putString("FounderName", founderName);
        tag.putLong("FoundedGameTime", foundedGameTime);
        return tag;
    }

    public static Settlement fromTag(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("FounderUuid") || !tag.contains("Name", 8)
                || !tag.contains("Dimension", 8) || !tag.contains("BellPosition", 4)
                || !tag.contains("Radius", 3) || !tag.contains("FounderName", 8)
                || !tag.contains("FoundedGameTime", 4)) {
            throw new IllegalArgumentException("Incomplete settlement record");
        }
        return new Settlement(tag.getUUID("Id"), tag.getString("Name"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension"))),
                BlockPos.of(tag.getLong("BellPosition")), tag.getInt("Radius"), tag.getUUID("FounderUuid"),
                tag.getString("FounderName"), tag.getLong("FoundedGameTime"));
    }
}
