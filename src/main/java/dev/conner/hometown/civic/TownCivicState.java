package dev.conner.hometown.civic;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;

/** Immutable per-town R3 civic/progression state. Current facility validity is deliberately not persisted. */
public final class TownCivicState {
    private final DyeColor primaryColor;
    private final DyeColor secondaryColor;
    private final EnumSet<ProgressionUnlock> unlocks;
    private final EnumMap<FacilityType, FacilityMarker> facilities;

    private TownCivicState(DyeColor primaryColor, DyeColor secondaryColor,
                           Collection<ProgressionUnlock> unlocks,
                           Map<FacilityType, FacilityMarker> facilities) {
        if ((primaryColor == null) != (secondaryColor == null)) {
            throw new IllegalArgumentException("Town colors must both be configured or both be absent");
        }
        if (primaryColor != null && primaryColor == secondaryColor) {
            throw new IllegalArgumentException("Town primary and secondary colors must differ");
        }
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.unlocks = unlocks.isEmpty() ? EnumSet.noneOf(ProgressionUnlock.class) : EnumSet.copyOf(unlocks);
        this.facilities = new EnumMap<>(FacilityType.class);
        facilities.forEach((type, marker) -> {
            if (type == null || marker == null || marker.type() != type) {
                throw new IllegalArgumentException("Civic facility map/type mismatch");
            }
            this.facilities.put(type, marker);
        });
    }

    public static TownCivicState empty() {
        return new TownCivicState(null, null, List.of(), Map.of());
    }

    public boolean colorsConfigured() { return primaryColor != null; }
    public Optional<DyeColor> primaryColor() { return Optional.ofNullable(primaryColor); }
    public Optional<DyeColor> secondaryColor() { return Optional.ofNullable(secondaryColor); }
    public Set<ProgressionUnlock> unlocks() { return Collections.unmodifiableSet(unlocks); }
    public boolean isUnlocked(ProgressionUnlock unlock) { return unlocks.contains(Objects.requireNonNull(unlock)); }
    public Map<FacilityType, FacilityMarker> facilities() { return Collections.unmodifiableMap(facilities); }
    public Optional<FacilityMarker> facility(FacilityType type) { return Optional.ofNullable(facilities.get(type)); }

    /** M1 color selection is one-time. Repeating the exact same selection is idempotent. */
    public TownCivicState withColors(DyeColor primary, DyeColor secondary) {
        Objects.requireNonNull(primary);
        Objects.requireNonNull(secondary);
        if (primary == secondary) throw new IllegalArgumentException("Town primary and secondary colors must differ");
        if (colorsConfigured()) {
            if (primaryColor == primary && secondaryColor == secondary) return this;
            throw new IllegalStateException("Town colors are already configured");
        }
        return new TownCivicState(primary, secondary, unlocks, facilities);
    }

    public TownCivicState withUnlocked(Collection<ProgressionUnlock> additions) {
        Objects.requireNonNull(additions);
        EnumSet<ProgressionUnlock> next = unlocks.isEmpty()
                ? EnumSet.noneOf(ProgressionUnlock.class) : EnumSet.copyOf(unlocks);
        if (!next.addAll(additions)) return this;
        return new TownCivicState(primaryColor, secondaryColor, next, facilities);
    }

    public TownCivicState withFacility(FacilityMarker marker) {
        Objects.requireNonNull(marker);
        FacilityMarker existing = facilities.get(marker.type());
        if (marker.equals(existing)) return this;
        EnumMap<FacilityType, FacilityMarker> next = new EnumMap<>(facilities);
        next.put(marker.type(), marker);
        return new TownCivicState(primaryColor, secondaryColor, unlocks, next);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("ColorsConfigured", colorsConfigured());
        if (colorsConfigured()) {
            tag.putString("PrimaryColor", primaryColor.name());
            tag.putString("SecondaryColor", secondaryColor.name());
        }

        ListTag unlockList = new ListTag();
        for (ProgressionUnlock unlock : ProgressionUnlock.values()) {
            if (unlocks.contains(unlock)) unlockList.add(StringTag.valueOf(unlock.name()));
        }
        tag.put("Unlocks", unlockList);

        ListTag facilityList = new ListTag();
        for (FacilityType type : FacilityType.values()) {
            FacilityMarker marker = facilities.get(type);
            if (marker != null) facilityList.add(marker.toTag());
        }
        tag.put("Facilities", facilityList);
        return tag;
    }

    public static TownCivicState fromTag(CompoundTag tag, UUID ownerId) {
        Objects.requireNonNull(ownerId);
        if (!tag.contains("ColorsConfigured", Tag.TAG_BYTE)
                || !tag.contains("Unlocks", Tag.TAG_LIST) || !tag.contains("Facilities", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Incomplete civic state");
        }

        DyeColor primary = null;
        DyeColor secondary = null;
        boolean configured = tag.getBoolean("ColorsConfigured");
        boolean hasPrimary = tag.contains("PrimaryColor", Tag.TAG_STRING);
        boolean hasSecondary = tag.contains("SecondaryColor", Tag.TAG_STRING);
        if (configured != (hasPrimary && hasSecondary) || (!configured && (hasPrimary || hasSecondary))) {
            throw new IllegalArgumentException("Inconsistent civic color state");
        }
        if (configured) {
            try {
                primary = DyeColor.valueOf(tag.getString("PrimaryColor"));
                secondary = DyeColor.valueOf(tag.getString("SecondaryColor"));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown town color", ex);
            }
        }

        EnumSet<ProgressionUnlock> unlocks = EnumSet.noneOf(ProgressionUnlock.class);
        ListTag unlockList = tag.getList("Unlocks", Tag.TAG_STRING);
        for (int i = 0; i < unlockList.size(); i++) {
            try {
                ProgressionUnlock unlock = ProgressionUnlock.valueOf(unlockList.getString(i));
                if (!unlocks.add(unlock)) throw new IllegalArgumentException("Duplicate civic progression unlock");
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid civic progression unlock", ex);
            }
        }

        EnumMap<FacilityType, FacilityMarker> facilities = new EnumMap<>(FacilityType.class);
        ListTag facilityList = tag.getList("Facilities", Tag.TAG_COMPOUND);
        for (int i = 0; i < facilityList.size(); i++) {
            FacilityMarker marker = FacilityMarker.fromTag(facilityList.getCompound(i));
            if (!marker.settlementId().equals(ownerId)) throw new IllegalArgumentException("Foreign civic facility owner");
            if (facilities.putIfAbsent(marker.type(), marker) != null) {
                throw new IllegalArgumentException("Duplicate civic facility type");
            }
        }
        return new TownCivicState(primary, secondary, unlocks, facilities);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TownCivicState that)) return false;
        return primaryColor == that.primaryColor && secondaryColor == that.secondaryColor
                && unlocks.equals(that.unlocks) && facilities.equals(that.facilities);
    }

    @Override public int hashCode() {
        return Objects.hash(primaryColor, secondaryColor, unlocks, facilities);
    }
}
