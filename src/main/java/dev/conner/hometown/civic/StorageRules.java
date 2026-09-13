package dev.conner.hometown.civic;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Single owner for the first R3 Storage facility playtest rules. */
public final class StorageRules {
    /** Initial playtest value: enough containers to make Storage a deliberate facility, not a spare chest. */
    public static final int MIN_STORAGE_BLOCKS = 4;
    /** A Storage room must contain at least one genuinely usable standing position. */
    public static final int MIN_USABLE_FLOOR_POSITIONS = 1;
    /** Keep civic facilities above the modern hostile-mob block-light spawn threshold. */
    public static final int MIN_BLOCK_LIGHT = 1;

    /** Reuse the authoritative R2 Food storage semantic instead of inventing a competing definition. */
    public static final TagKey<Block> STORAGE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("hometown", "food_storage"));

    private StorageRules() {}
}
