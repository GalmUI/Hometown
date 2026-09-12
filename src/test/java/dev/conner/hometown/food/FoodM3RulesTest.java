package dev.conner.hometown.food;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FoodM3RulesTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private Set<String> group(String name)throws Exception{
        try(var stream=getClass().getResourceAsStream("/data/hometown/tags/item/food_groups/"+name+".json")){
            assertNotNull(stream);var json=JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();assertFalse(json.get("replace").getAsBoolean());
            var ids=new LinkedHashSet<String>();for(var value:json.getAsJsonArray("values"))assertTrue(ids.add(value.getAsString()));return ids;
        }
    }
    private JsonElement crop(String name)throws Exception{
        try(var stream=getClass().getResourceAsStream("/data/hometown/hometown/crops/"+name+".json")){assertNotNull(stream);return JsonParser.parseReader(new InputStreamReader(stream));}
    }
    @Test void F10_exactCoreFoodGroupMemberships()throws Exception{
        assertEquals(Set.of("minecraft:bread"),group("grains"));
        assertEquals(Set.of("minecraft:carrot","minecraft:potato","minecraft:baked_potato","minecraft:beetroot"),group("vegetables"));
        assertEquals(Set.of("minecraft:apple","minecraft:melon_slice","minecraft:sweet_berries","minecraft:glow_berries"),group("fruit"));
        assertEquals(Set.of("minecraft:beef","minecraft:cooked_beef","minecraft:porkchop","minecraft:cooked_porkchop","minecraft:chicken","minecraft:cooked_chicken","minecraft:mutton","minecraft:cooked_mutton","minecraft:rabbit","minecraft:cooked_rabbit","minecraft:cod","minecraft:cooked_cod","minecraft:salmon","minecraft:cooked_salmon"),group("protein"));
        assertEquals(Set.of("minecraft:mushroom_stew","minecraft:rabbit_stew","minecraft:beetroot_soup"),group("prepared_meals"));
        var all=new HashSet<String>();for(String id:new String[]{"cake","cookie","pumpkin_pie","golden_apple","enchanted_golden_apple","rotten_flesh","spider_eye","chorus_fruit","honey_bottle"})all.add("minecraft:"+id);
        for(var unsupported:all)for(var name:List.of("grains","vegetables","fruit","protein","prepared_meals"))assertFalse(group(name).contains(unsupported),unsupported+" must remain unclassified");
        assertEquals(List.of(FoodGroup.PREPARED_MEALS,FoodGroup.PROTEIN,FoodGroup.VEGETABLES,FoodGroup.FRUIT,FoodGroup.GRAINS),FoodGroup.DEFAULT_PRIORITY);
    }
    @Test void F07_exactVanillaCropAgesAndUnsupportedBlocks()throws Exception{
        var resources=new HashMap<ResourceLocation,JsonElement>();for(String name:List.of("wheat","carrots","potatoes","beetroots"))resources.put(ResourceLocation.fromNamespaceAndPath("hometown",name),crop(name));
        var defs=CropRules.parse(resources);assertEquals(4,defs.rules().size());
        var wheat6=Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,6),wheat7=wheat6.setValue(BlockStateProperties.AGE_7,7);
        var carrot6=Blocks.CARROTS.defaultBlockState().setValue(BlockStateProperties.AGE_7,6),carrot7=carrot6.setValue(BlockStateProperties.AGE_7,7);
        var potato6=Blocks.POTATOES.defaultBlockState().setValue(BlockStateProperties.AGE_7,6),potato7=potato6.setValue(BlockStateProperties.AGE_7,7);
        var beet2=Blocks.BEETROOTS.defaultBlockState().setValue(BlockStateProperties.AGE_3,2),beet3=beet2.setValue(BlockStateProperties.AGE_3,3);
        for(var pair:List.of(new net.minecraft.world.level.block.state.BlockState[]{wheat6,wheat7},new net.minecraft.world.level.block.state.BlockState[]{carrot6,carrot7},new net.minecraft.world.level.block.state.BlockState[]{potato6,potato7},new net.minecraft.world.level.block.state.BlockState[]{beet2,beet3})){
            var rule=defs.rule(pair[0]).orElseThrow();assertFalse(defs.mature(rule,pair[0]).orElseThrow());assertTrue(defs.mature(rule,pair[1]).orElseThrow());
        }
        for(var block:List.of(Blocks.FARMLAND,Blocks.MELON,Blocks.PUMPKIN,Blocks.COCOA,Blocks.SWEET_BERRY_BUSH,Blocks.SUGAR_CANE,Blocks.BROWN_MUSHROOM,Blocks.NETHER_WART))assertFalse(defs.contains(block));
    }
    @Test void F08_customAnchorAndOmittedMaturityRemainUnassessed(){
        var custom=JsonParser.parseString("{\"schemaVersion\":1,\"block\":\"minecraft:sweet_berry_bush\",\"family\":\"example:berries\",\"anchorProperties\":{\"age\":\"2\"}}");
        var defs=CropRules.parse(Map.of(ResourceLocation.fromNamespaceAndPath("example","berries"),custom));
        var age1=Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3,1),age2=age1.setValue(BlockStateProperties.AGE_3,2);
        assertTrue(defs.rule(age1).isEmpty());var rule=defs.rule(age2).orElseThrow();assertTrue(defs.mature(rule,age2).isEmpty());
    }
    @Test void F10_invalidReloadRetainsLastValidAndDuplicateBlockRejected()throws Exception{
        try{
            var valid=Map.of(ResourceLocation.fromNamespaceAndPath("hometown","wheat"),crop("wheat"));assertTrue(CropRules.install(valid));String fingerprint=CropRules.current().fingerprint();
            var invalid=JsonParser.parseString("{\"schemaVersion\":1,\"block\":\"minecraft:wheat\",\"family\":\"hometown:wheat\",\"matureProperties\":{\"age\":\"99\"}}");
            assertFalse(CropRules.install(Map.of(ResourceLocation.fromNamespaceAndPath("hometown","invalid"),invalid)));assertEquals(fingerprint,CropRules.current().fingerprint());
            assertThrows(IllegalArgumentException.class,()->CropRules.parse(Map.of(ResourceLocation.fromNamespaceAndPath("a","one"),crop("wheat"),ResourceLocation.fromNamespaceAndPath("b","two"),crop("wheat"))));
        }finally{CropRules.install(Map.of());}
    }
}
