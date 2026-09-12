package dev.conner.hometown.food;

import net.minecraft.resources.ResourceLocation;

/** Copied fact from one qualifying physical inventory stack; never retains an ItemStack or inventory reference. */
public record FoodStackFact(ResourceLocation itemId, long nutrition) {
    public FoodStackFact {
        java.util.Objects.requireNonNull(itemId);
        if (nutrition <= 0) throw new IllegalArgumentException("Food stack fact must contain positive nutrition");
    }
}
