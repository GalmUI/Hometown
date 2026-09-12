package dev.conner.hometown.food;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class FoodVarietyDebugReport {
    public record Data(FoodVarietySnapshot snapshot,FoodVarietySettings settings,Map<ResourceLocation,List<FoodGroup>> collisions) {
        public Data { collisions=Map.copyOf(collisions); }
    }
    private FoodVarietyDebugReport() {}
    public static Data data(FoodVarietySnapshot snapshot,FoodVarietySettings settings,List<FoodStackFact> facts) {
        var collisions=new TreeMap<ResourceLocation,List<FoodGroup>>();
        for(var fact:facts){
            if(!BuiltInRegistries.ITEM.containsKey(fact.itemId()))continue;
            var holder=BuiltInRegistries.ITEM.get(fact.itemId()).builtInRegistryHolder();var matches=new ArrayList<FoodGroup>();
            for(var group:FoodGroup.values())if(holder.is(group.tag))matches.add(group);
            if(matches.size()>1)collisions.put(fact.itemId(),List.copyOf(matches));
        }
        return new Data(snapshot,settings,collisions);
    }
    public static String format(Data data) {
        var s=data.snapshot();var out=new StringBuilder("Food Variety Debug\n");
        out.append("State: ").append(s.varietyState()).append(" | Source: ").append(s.sourceStatus()).append('\n');
        out.append("Population: ").append(s.population()).append(" | populationComplete=").append(s.populationComplete()).append('\n');
        out.append("Priority: ").append(data.settings().priority().stream().map(g->g.id).toList()).append('\n');
        out.append("Required per group: ").append(s.requiredNutritionPerGroup().isPresent()?s.requiredNutritionPerGroup().getAsLong():"N/A").append('\n');
        for(var group:FoodGroup.values())out.append(group.id).append(" tag=").append(group.tag.location()).append(" enabled=").append(s.enabledGroups().contains(group))
                .append(" nutrition=").append(s.perGroupNutrition().get(group)).append(" qualification=").append(s.qualifications().get(group)).append('\n');
        out.append("Unique Foods: ").append(s.uniqueFoodCount()).append(" | Unclassified nutrition: ").append(s.unclassifiedNutrition()).append('\n');
        out.append("Collision stacks: ").append(s.collisionCount()).append('\n');
        data.collisions().forEach((item,groups)->out.append("  ").append(item).append(" matches ").append(groups.stream().map(g->g.id).toList()).append('\n'));
        return out.toString().stripTrailing();
    }
}
