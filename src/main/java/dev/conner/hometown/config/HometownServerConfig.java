package dev.conner.hometown.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HometownServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SETTLEMENT_RADIUS;
    public static final ModConfigSpec.IntValue NUTRITION_PER_RESIDENT_PER_DAY;
    public static final ModConfigSpec.IntValue VERTICAL_SCAN_RADIUS;
    public static final ModConfigSpec.IntValue MINIMUM_VILLAGERS;
    public static final ModConfigSpec.IntValue MINIMUM_BEDS;
    public static final ModConfigSpec.BooleanValue PREVENT_OVERLAP;
    public static final ModConfigSpec.BooleanValue CONSUME_BOOK;
    public static final ModConfigSpec.BooleanValue SAFETY_ENABLED;
    public static final ModConfigSpec.BooleanValue COMFORT_ENABLED;
    public static final ModConfigSpec.IntValue COMFORT_MAX_ROOMS, COMFORT_MAX_CELLS;
    public static final java.util.Map<dev.conner.hometown.comfort.ComfortCategory,ModConfigSpec.BooleanValue> COMFORT_CATEGORY_ENABLED=new java.util.EnumMap<>(dev.conner.hometown.comfort.ComfortCategory.class);
    public static final java.util.Map<dev.conner.hometown.comfort.ComfortCategory,ModConfigSpec.IntValue> COMFORT_CATEGORY_WEIGHT=new java.util.EnumMap<>(dev.conner.hometown.comfort.ComfortCategory.class);
    public static final ModConfigSpec.BooleanValue FOOD_VARIETY_ENABLED, FOOD_GROWING_ENABLED;
    public static final java.util.Map<dev.conner.hometown.food.FoodGroup,ModConfigSpec.BooleanValue> FOOD_VARIETY_GROUP_ENABLED=new java.util.EnumMap<>(dev.conner.hometown.food.FoodGroup.class);
    public static final ModConfigSpec.IntValue FOOD_VARIETY_NUTRITION_PER_RESIDENT_GROUP;
    public static final ModConfigSpec.ConfigValue<java.util.List<String>> FOOD_VARIETY_GROUP_PRIORITY;
    public static final ModConfigSpec.IntValue MINIMUM_BLOCK_LIGHT, SAFETY_ENTITY_LIMIT, NEW_ENTITY_LIMIT, NEW_BLOCK_LIMIT, REQUEST_COOLDOWN;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SETTLEMENT_RADIUS = builder.comment("Radius stored on new settlements; existing radii never change.")
                .defineInRange("settlementRadius", 64, 16, 256);
        VERTICAL_SCAN_RADIUS = builder.defineInRange("verticalScanRadius", 32, 8, 128);
        MINIMUM_VILLAGERS = builder.defineInRange("minimumVillagers", 2, 0, 100);
        MINIMUM_BEDS = builder.defineInRange("minimumBeds", 2, 0, 100);
        PREVENT_OVERLAP = builder.define("preventSettlementOverlap", true);
        CONSUME_BOOK = builder.define("consumeFoundingBook", true);
        NUTRITION_PER_RESIDENT_PER_DAY = builder.comment("Diagnostic nutrition points per resident per Minecraft day; never consumes items.")
                .defineInRange("nutritionPerResidentPerDay", 20, 1, 1000000);
        builder.push("food").push("variety");
        FOOD_VARIETY_ENABLED=builder.define("enabled",true);
        FOOD_VARIETY_NUTRITION_PER_RESIDENT_GROUP=builder.defineInRange("nutritionPerResidentPerGroup",2,1,100);
        var defaultPriority=dev.conner.hometown.food.FoodGroup.DEFAULT_PRIORITY.stream().map(group->group.id).toList();
        FOOD_VARIETY_GROUP_PRIORITY=builder.define("groupPriority",defaultPriority,
                value->value instanceof java.util.List<?> list && dev.conner.hometown.food.FoodGroup.isPermutation(list));
        builder.push("groups");
        for(var group:dev.conner.hometown.food.FoodGroup.values()) {
            builder.push(group.id);FOOD_VARIETY_GROUP_ENABLED.put(group,builder.define("enabled",true));builder.pop();
        }
        builder.pop().pop().push("growing");
        FOOD_GROWING_ENABLED=builder.define("enabled",true);
        builder.pop().pop();
        builder.push("safety");
        SAFETY_ENABLED = builder.define("enabled", true);
        MINIMUM_BLOCK_LIGHT = builder.defineInRange("minimumResidentialBlockLight", 1, 0, 15);
        SAFETY_ENTITY_LIMIT = builder.defineInRange("maxEntityInspections", 4096, 128, 65536);
        builder.pop().push("scan");
        NEW_ENTITY_LIMIT = builder.defineInRange("maxNewEntityInspections", 4096, 128, 65536);
        NEW_BLOCK_LIMIT = builder.defineInRange("maxNewBlockInspections", 262144, 1024, 1048576);
        builder.pop().push("ledger");
        REQUEST_COOLDOWN = builder.defineInRange("requestCooldownTicks", 40, 1, 1200);
        builder.pop();
        builder.push("comfort");
        COMFORT_ENABLED=builder.define("enabled",true);
        COMFORT_MAX_ROOMS=builder.defineInRange("maxRooms",512,1,4096);
        COMFORT_MAX_CELLS=builder.defineInRange("maxCellsPerRoom",65536,64,262144);
        builder.push("categories");
        for(var category:dev.conner.hometown.comfort.ComfortCategory.values()) {
            builder.push(category.id());
            COMFORT_CATEGORY_ENABLED.put(category,builder.define("enabled",category.defaultEnabled));
            COMFORT_CATEGORY_WEIGHT.put(category,builder.defineInRange("weight",category.defaultWeight,0,100));
            builder.pop();
        }
        builder.pop().pop();
        SPEC = builder.build();
    }
    private HometownServerConfig() {}
}
