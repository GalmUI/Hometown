package dev.conner.hometown.civic;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Single owner for the first R3 Animal Farm facility playtest rules. */
public final class AnimalFarmRules {
    public static final int MIN_STORAGE_BLOCKS = 1;
    public static final int MIN_LOOMS = 1;
    public static final int MIN_PADDOCK_ATTACHMENTS = 2;
    public static final int MIN_PADDOCK_BARRIERS = 16;

    /** Hard caps keep explicit qualification bounded even with pathological fence networks. */
    public static final int MAX_PADDOCK_BARRIERS = 512;
    public static final int MAX_ENCLOSURE_CELLS = 16384;

    /** Vanilla fences and fence gates by default; datapacks/mod integrations may extend this semantic. */
    public static final TagKey<Block> PADDOCK_BARRIERS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("hometown", "animal_farm_paddock_barriers"));

    private AnimalFarmRules() {}
}
