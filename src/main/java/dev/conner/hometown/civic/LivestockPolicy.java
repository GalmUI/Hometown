package dev.conner.hometown.civic;

import net.minecraft.nbt.CompoundTag;

/** Per-species Animal Farm herd-management policy. */
public record LivestockPolicy(int breedingPairs, int cullAbove) {
    public static final int DEFAULT_BREEDING_PAIRS = 1;
    public static final int DEFAULT_CULL_ABOVE = 4;
    public static final int MAX_BREEDING_PAIRS = 16;
    public static final int MAX_CULL_ABOVE = 64;

    public LivestockPolicy {
        if (breedingPairs < 1 || breedingPairs > MAX_BREEDING_PAIRS) {
            throw new IllegalArgumentException("Breeding pairs out of range");
        }
        int protectedAdults = breedingPairs * 2;
        if (cullAbove < protectedAdults || cullAbove > MAX_CULL_ABOVE) {
            throw new IllegalArgumentException("Cull threshold must preserve all breeding adults");
        }
    }

    public static LivestockPolicy defaults() {
        return new LivestockPolicy(DEFAULT_BREEDING_PAIRS, DEFAULT_CULL_ABOVE);
    }

    public int protectedAdults() { return breedingPairs * 2; }

    public LivestockPolicy withBreedingPairs(int value) {
        int pairs = Math.clamp(value, 1, MAX_BREEDING_PAIRS);
        int threshold = Math.max(cullAbove, pairs * 2);
        threshold = Math.min(threshold, MAX_CULL_ABOVE);
        if (pairs * 2 > MAX_CULL_ABOVE) pairs = MAX_CULL_ABOVE / 2;
        return new LivestockPolicy(pairs, Math.max(threshold, pairs * 2));
    }

    public LivestockPolicy withCullAbove(int value) {
        return new LivestockPolicy(breedingPairs,
                Math.clamp(value, protectedAdults(), MAX_CULL_ABOVE));
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("BreedingPairs", breedingPairs);
        tag.putInt("CullAbove", cullAbove);
        return tag;
    }

    static LivestockPolicy fromTag(CompoundTag tag) {
        return new LivestockPolicy(tag.getInt("BreedingPairs"), tag.getInt("CullAbove"));
    }
}
