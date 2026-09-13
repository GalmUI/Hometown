package dev.conner.hometown.civic;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Single owner for R3 M1 Town Hall qualification balance values and semantic tags. */
public final class TownHallRules {
    public static final int MIN_USABLE_FLOOR_POSITIONS = 20;
    public static final int MIN_BOOKSHELVES = 4;
    public static final int MIN_BLOCK_LIGHT = 1;

    public static final TagKey<Block> BOOKSHELVES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("hometown", "town_hall_bookshelves"));
    public static final TagKey<Block> STORAGE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("hometown", "food_storage"));

    private TownHallRules() {}
}
