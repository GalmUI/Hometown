package dev.conner.hometown.food;

import com.google.gson.*;
import dev.conner.hometown.Hometown;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Atomic version-1 crop definitions. One effective definition per block; no name/property inference. */
public final class CropRules extends SimpleJsonResourceReloadListener {
    public record Rule(ResourceLocation block, ResourceLocation family, Map<String,String> anchorProperties,
            Optional<Map<String,String>> matureProperties) {
        public Rule {
            anchorProperties=Map.copyOf(anchorProperties);
            matureProperties=matureProperties.map(Map::copyOf);
        }
    }
    public record Definitions(Map<ResourceLocation,Rule> rules) {
        public Definitions { rules=Map.copyOf(rules); }
        public boolean contains(Block block) { return rules.containsKey(BuiltInRegistries.BLOCK.getKey(block)); }
        public Optional<Rule> rule(BlockState state) {
            var rule=rules.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            return rule!=null&&matches(state,rule.anchorProperties())?Optional.of(rule):Optional.empty();
        }
        public Optional<Boolean> mature(Rule rule,BlockState state) {
            return rule.matureProperties().map(properties->matches(state,properties));
        }
        public String fingerprint() {
            var rows=new TreeSet<String>();
            rules.values().forEach(rule->rows.add(rule.block()+"=>"+rule.family()+" anchor="+new TreeMap<>(rule.anchorProperties())+
                    " mature="+rule.matureProperties().map(TreeMap::new).map(Object::toString).orElse("N/A")));
            return String.join("\n",rows);
        }
        private static boolean matches(BlockState state,Map<String,String> properties) {
            for(var entry:properties.entrySet()) {
                var property=state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if(property==null||!propertyName(state,property).equals(entry.getValue()))return false;
            }
            return true;
        }
    }

    private static volatile Definitions current=new Definitions(Map.of());
    public CropRules(){super(new Gson(),"hometown/crops");}
    public static Definitions current(){return current;}
    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){install(resources);}
    public static boolean install(Map<ResourceLocation,JsonElement> resources) {
        try { current=parse(resources);return true; }
        catch(RuntimeException invalid) {
            Hometown.LOGGER.error("Hometown crop rule reload rejected; retaining last valid definitions: {}",invalid.getMessage());
            return false;
        }
    }
    public static Definitions parse(Map<ResourceLocation,JsonElement> resources) {
        var rules=new HashMap<ResourceLocation,Rule>();
        for(var resource:new TreeMap<>(resources).entrySet()) {
            var json=resource.getValue().getAsJsonObject();
            var allowed=Set.of("schemaVersion","block","family","anchorProperties","matureProperties");
            if(!allowed.containsAll(json.keySet())||!json.has("schemaVersion")||!json.has("block")||!json.has("family"))
                throw new IllegalArgumentException("Invalid fields in "+resource.getKey());
            if(!json.get("schemaVersion").equals(new JsonPrimitive(1)))throw new IllegalArgumentException("Unsupported crop schema in "+resource.getKey());
            var blockId=ResourceLocation.parse(json.get("block").getAsString());
            if(!BuiltInRegistries.BLOCK.containsKey(blockId))throw new IllegalArgumentException("Unknown crop block "+blockId);
            var block=BuiltInRegistries.BLOCK.get(blockId);
            var family=ResourceLocation.parse(json.get("family").getAsString());
            var anchor=json.has("anchorProperties")?properties(block,json.getAsJsonObject("anchorProperties"),blockId):Map.<String,String>of();
            Optional<Map<String,String>> mature=json.has("matureProperties")?Optional.of(properties(block,json.getAsJsonObject("matureProperties"),blockId)):Optional.empty();
            var rule=new Rule(blockId,family,anchor,mature);
            if(rules.put(blockId,rule)!=null)throw new IllegalArgumentException("Duplicate crop definition for "+blockId);
        }
        return new Definitions(rules);
    }
    private static Map<String,String> properties(Block block,JsonObject json,ResourceLocation blockId) {
        var values=new TreeMap<String,String>();
        for(var entry:json.entrySet()) {
            if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("Crop property must be string: "+entry.getKey());
            var property=block.getStateDefinition().getProperty(entry.getKey());var value=entry.getValue().getAsString();
            if(property==null||property.getValue(value).isEmpty())throw new IllegalArgumentException("Invalid crop property "+blockId+"/"+entry.getKey()+"="+value);
            values.put(entry.getKey(),value);
        }
        return values;
    }
    private static <T extends Comparable<T>> String propertyName(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
