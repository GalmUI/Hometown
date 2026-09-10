package dev.conner.hometown.comfort;

import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public enum ComfortCategory {
    STORAGE(10,true), LIGHTING(15,true), DECOR(10,true), BOOKS(10,true),
    PLANTS(10,true), AMENITIES(20,true), SEATING(15,false), TABLES(10,false);
    public final int defaultWeight;
    public final boolean defaultEnabled;
    public final TagKey<Block> tag;
    ComfortCategory(int weight,boolean enabled) {
        defaultWeight=weight;defaultEnabled=enabled;
        tag=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("hometown","comfort/"+id()));
    }
    public String id(){return name().toLowerCase(Locale.ROOT);}
}
