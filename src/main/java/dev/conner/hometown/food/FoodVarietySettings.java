package dev.conner.hometown.food;

import dev.conner.hometown.config.HometownServerConfig;
import java.util.*;

public record FoodVarietySettings(boolean enabled, Map<FoodGroup,Boolean> groupEnabled,
        int nutritionPerResidentPerGroup, List<FoodGroup> priority) {
    public static final FoodVarietySettings DEFAULT = new FoodVarietySettings(true, defaultEnabled(), 2, FoodGroup.DEFAULT_PRIORITY);

    public FoodVarietySettings {
        groupEnabled = Map.copyOf(groupEnabled);
        priority = List.copyOf(priority);
        if (nutritionPerResidentPerGroup < 1 || nutritionPerResidentPerGroup > 100)
            throw new IllegalArgumentException("Invalid Food Variety threshold multiplier");
        if (!groupEnabled.keySet().containsAll(EnumSet.allOf(FoodGroup.class)))
            throw new IllegalArgumentException("Missing Food Variety group setting");
        if (priority.size() != FoodGroup.values().length || EnumSet.copyOf(priority).size() != FoodGroup.values().length)
            throw new IllegalArgumentException("Food Variety priority must be a permutation of all groups");
    }

    private static Map<FoodGroup,Boolean> defaultEnabled() {
        var map = new EnumMap<FoodGroup,Boolean>(FoodGroup.class);
        for (var group : FoodGroup.values()) map.put(group, true);
        return map;
    }

    public static FoodVarietySettings current() {
        var enabled = new EnumMap<FoodGroup,Boolean>(FoodGroup.class);
        for (var group : FoodGroup.values()) enabled.put(group, HometownServerConfig.FOOD_VARIETY_GROUP_ENABLED.get(group).get());
        var ids = HometownServerConfig.FOOD_VARIETY_GROUP_PRIORITY.get();
        List<FoodGroup> priority;
        if (FoodGroup.isPermutation(ids)) priority = ids.stream().map(id -> FoodGroup.byId(id).orElseThrow()).toList();
        else priority = FoodGroup.DEFAULT_PRIORITY;
        return new FoodVarietySettings(HometownServerConfig.FOOD_VARIETY_ENABLED.get(), enabled,
                HometownServerConfig.FOOD_VARIETY_NUTRITION_PER_RESIDENT_GROUP.get(), priority);
    }

    public Set<FoodGroup> enabledGroups() {
        var result = EnumSet.noneOf(FoodGroup.class);
        groupEnabled.forEach((group,on) -> { if (on) result.add(group); });
        return Set.copyOf(result);
    }
}
