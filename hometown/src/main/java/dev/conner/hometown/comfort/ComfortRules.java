package dev.conner.hometown.comfort;

import com.google.gson.*;
import dev.conner.hometown.Hometown;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;

/** Small, atomic version-1 equality predicate set. Membership remains controlled by block tags. */
public final class ComfortRules extends SimpleJsonResourceReloadListener {
    public record Key(ResourceLocation block,ComfortCategory category) {}
    public record Definitions(Map<Key,Map<String,String>> predicates) {
        public Definitions {
            var copy=new HashMap<Key,Map<String,String>>();
            predicates.forEach((key,value)->copy.put(key,Map.copyOf(value)));
            predicates=Map.copyOf(copy);
        }
        public boolean matches(ComfortCategory category,BlockState state) {
            var properties=predicates.get(new Key(BuiltInRegistries.BLOCK.getKey(state.getBlock()),category));
            if(properties==null)return true;
            for(var entry:properties.entrySet()) {
                var property=state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if(property==null||!propertyName(state,property).equals(entry.getValue()))return false;
            }
            return true;
        }
        public String fingerprint() {
            var rows=new TreeSet<String>();
            predicates.forEach((key,value)->rows.add(key.block()+"/"+key.category()+"="+new TreeMap<>(value)));
            return String.join("\n",rows);
        }
    }
    private static volatile Definitions current=new Definitions(Map.of());
    private static <T extends Comparable<T>> String propertyName(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }
    public static Definitions current(){return current;}
    public ComfortRules(){super(new Gson(),"hometown/comfort_rules");}
    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler) {
        install(resources);
    }
    public static boolean install(Map<ResourceLocation,JsonElement> resources) {
        try { current=parse(resources);return true; }
        catch(RuntimeException invalid) {
            Hometown.LOGGER.error("Hometown Comfort rule reload rejected; retaining last valid definitions: {}",invalid.getMessage());
            return false;
        }
    }
    public static Definitions parse(Map<ResourceLocation,JsonElement> resources) {
        var rules=new HashMap<Key,Map<String,String>>();
        for(var resource:new TreeMap<>(resources).entrySet()) {
            var json=resource.getValue().getAsJsonObject();
            if(!json.keySet().equals(Set.of("schemaVersion","block","category","properties")))
                throw new IllegalArgumentException("Invalid fields in "+resource.getKey());
            if(!json.get("schemaVersion").equals(new JsonPrimitive(1)))throw new IllegalArgumentException("Unsupported Comfort schema in "+resource.getKey());
            var blockId=ResourceLocation.parse(json.get("block").getAsString());
            if(!BuiltInRegistries.BLOCK.containsKey(blockId))throw new IllegalArgumentException("Unknown block "+blockId);
            var block=BuiltInRegistries.BLOCK.get(blockId);
            var category=ComfortCategory.valueOf(json.get("category").getAsString().toUpperCase(Locale.ROOT));
            if(!category.id().equals(json.get("category").getAsString()))throw new IllegalArgumentException("Invalid category ID");
            var values=new TreeMap<String,String>();
            for(var entry:json.getAsJsonObject("properties").entrySet()) {
                if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Property must be a string: "+entry.getKey());
                var property=block.getStateDefinition().getProperty(entry.getKey());
                var value=entry.getValue().getAsString();
                if(property==null||property.getValue(value).isEmpty())throw new IllegalArgumentException("Invalid property "+blockId+"/"+entry.getKey()+"="+value);
                values.put(entry.getKey(),value);
            }
            var key=new Key(blockId,category);
            if(rules.put(key,values)!=null)throw new IllegalArgumentException("Duplicate Comfort rule "+key);
        }
        return new Definitions(rules);
    }
}
