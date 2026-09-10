package dev.conner.hometown.comfort;

import java.util.*;
import dev.conner.hometown.config.HometownServerConfig;

public record ComfortSettings(boolean enabled,Map<ComfortCategory,Category> categories,int maxRooms,int maxCellsPerRoom) {
    public record Category(boolean enabled,int weight) {
        public Category {if(weight<0||weight>100)throw new IllegalArgumentException("Invalid category weight");}
    }
    public ComfortSettings {
        categories=Map.copyOf(categories);
        if(categories.size()!=ComfortCategory.values().length||maxRooms<0||maxCellsPerRoom<0)
            throw new IllegalArgumentException("Invalid Comfort settings");
    }
    public int enabledWeight(){return categories.values().stream().filter(Category::enabled).mapToInt(Category::weight).sum();}
    public static ComfortSettings current() {
        var categories=new EnumMap<ComfortCategory,Category>(ComfortCategory.class);
        for(var c:ComfortCategory.values()) categories.put(c,new Category(
            HometownServerConfig.COMFORT_CATEGORY_ENABLED.get(c).get(),HometownServerConfig.COMFORT_CATEGORY_WEIGHT.get(c).get()));
        return new ComfortSettings(HometownServerConfig.COMFORT_ENABLED.get(),categories,
            HometownServerConfig.COMFORT_MAX_ROOMS.get(),HometownServerConfig.COMFORT_MAX_CELLS.get());
    }
}
