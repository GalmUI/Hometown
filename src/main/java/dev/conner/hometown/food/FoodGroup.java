package dev.conner.hometown.food;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Fixed Revision 2 Food Variety groups. Display order is enum order; priority is explicit. */
public enum FoodGroup {
    GRAINS("grains"),
    VEGETABLES("vegetables"),
    FRUIT("fruit"),
    PROTEIN("protein"),
    PREPARED_MEALS("prepared_meals");

    public final String id;
    public final TagKey<Item> tag;

    FoodGroup(String id) {
        this.id = id;
        this.tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("hometown", "food_groups/" + id));
    }

    public static final List<FoodGroup> DEFAULT_PRIORITY = List.of(PREPARED_MEALS, PROTEIN, VEGETABLES, FRUIT, GRAINS);

    public static Optional<FoodGroup> byId(String id) {
        return Arrays.stream(values()).filter(group -> group.id.equals(id)).findFirst();
    }

    public static boolean isPermutation(Collection<?> values) {
        if (values.size() != values().length) return false;
        var groups = EnumSet.noneOf(FoodGroup.class);
        for (Object value : values) {
            if (!(value instanceof String id)) return false;
            var group = byId(id);
            if (group.isEmpty() || !groups.add(group.get())) return false;
        }
        return groups.size() == values().length;
    }
}
