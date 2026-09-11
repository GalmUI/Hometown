package dev.conner.hometown.comfort;

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

class ComfortRulesTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    private Set<String> tag(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream("/data/hometown/tags/block/comfort/"+name+".json")) {
            assertNotNull(stream);var json=JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            assertFalse(json.get("replace").getAsBoolean());var ids=new LinkedHashSet<String>();
            for(var v:json.getAsJsonArray("values"))assertTrue(ids.add(v.getAsString()));
            return ids;
        }
    }
    private JsonElement rule(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream("/data/hometown/hometown/comfort_rules/"+name+".json")) {
            assertNotNull(stream);return JsonParser.parseReader(new InputStreamReader(stream));
        }
    }

    @Test void C07_exactCoreMemberships() throws Exception {
        assertEquals(Set.of("minecraft:chest","minecraft:trapped_chest","minecraft:barrel"),tag("storage"));
        assertEquals(Set.of("minecraft:torch","minecraft:wall_torch","minecraft:lantern","minecraft:soul_torch","minecraft:soul_wall_torch","minecraft:soul_lantern","minecraft:redstone_lamp"),tag("lighting"));
        assertEquals(Set.of("minecraft:bookshelf","minecraft:chiseled_bookshelf"),tag("books"));
        assertEquals(Set.of("minecraft:furnace","minecraft:smoker","minecraft:crafting_table","minecraft:campfire","minecraft:soul_campfire"),tag("amenities"));
        assertEquals(16,tag("decor").size());assertFalse(tag("decor").contains("minecraft:moss_carpet"));
        assertEquals(34,tag("plants").size());assertFalse(tag("plants").contains("minecraft:flower_pot"));
        assertTrue(tag("seating").isEmpty());assertTrue(tag("tables").isEmpty());assertTrue(tag("excluded").isEmpty());
    }

    @Test void C06_coreStatePredicatesAreExact() throws Exception {
        var resources=new HashMap<ResourceLocation,JsonElement>();
        resources.put(ResourceLocation.fromNamespaceAndPath("hometown","redstone_lamp"),rule("redstone_lamp"));
        resources.put(ResourceLocation.fromNamespaceAndPath("hometown","campfire"),rule("campfire"));
        resources.put(ResourceLocation.fromNamespaceAndPath("hometown","soul_campfire"),rule("soul_campfire"));
        var defs=ComfortRules.parse(resources);
        assertFalse(defs.matches(ComfortCategory.LIGHTING,Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT,false)));
        assertTrue(defs.matches(ComfortCategory.LIGHTING,Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT,true)));
        assertFalse(defs.matches(ComfortCategory.AMENITIES,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false)));
        assertTrue(defs.matches(ComfortCategory.AMENITIES,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,true)));
        assertFalse(defs.matches(ComfortCategory.AMENITIES,Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false)));
        assertTrue(defs.matches(ComfortCategory.AMENITIES,Blocks.FURNACE.defaultBlockState()));
        assertTrue(defs.matches(ComfortCategory.BOOKS,Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
    }

    @Test void C09_invalidReloadRetainsLastValidDefinitions() throws Exception {
        try {
            var valid=Map.of(ResourceLocation.fromNamespaceAndPath("hometown","lamp"),rule("redstone_lamp"));
            assertTrue(ComfortRules.install(valid));String fingerprint=ComfortRules.current().fingerprint();
            var invalid=JsonParser.parseString("{\"schemaVersion\":1,\"block\":\"minecraft:redstone_lamp\",\"category\":\"lighting\",\"properties\":{\"lit\":\"not_a_value\"}}");
            assertFalse(ComfortRules.install(Map.of(ResourceLocation.fromNamespaceAndPath("hometown","invalid"),invalid)));
            assertEquals(fingerprint,ComfortRules.current().fingerprint());
        } finally { ComfortRules.install(Map.of()); }
    }
}
