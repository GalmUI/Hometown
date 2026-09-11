package dev.conner.hometown.safety;

import dev.conner.hometown.housing.*;
import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.settlement.*;
import java.util.*;
import java.io.InputStreamReader;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.*;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static dev.conner.hometown.safety.SafetySnapshot.*;

class SafetyCollectorTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static class World {
        final ServerLevel level=mock(ServerLevel.class);
        final ServerChunkCache source=mock(ServerChunkCache.class);
        final LevelChunk chunk=mock(LevelChunk.class);
        final LevelEntityGetter<Entity> getter=mock(LevelEntityGetter.class);
        final List<Entity> entities=new ArrayList<>();
        final Set<String> absent=new HashSet<>();
        final Set<BlockPos> solid=new HashSet<>();
        final Map<BlockPos,Integer> light=new HashMap<>();
        final Settlement town=new Settlement(UUID.randomUUID(),"Safety",Level.OVERWORLD,new BlockPos(0,64,0),8,UUID.randomUUID(),"Owner",0);
        World() {
            when(level.getChunkSource()).thenReturn(source);when(level.getEntities()).thenReturn(getter);
            when(source.getChunkNow(anyInt(),anyInt())).thenAnswer(c->absent.contains(c.getArgument(0)+":"+c.getArgument(1))?null:chunk);
            when(chunk.getBlockState(any(BlockPos.class))).thenAnswer(c->solid.contains(c.getArgument(0))?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            when(level.getBrightness(eq(LightLayer.BLOCK),any(BlockPos.class))).thenAnswer(c->light.getOrDefault(c.getArgument(1),0));
            doAnswer(c->{
                AbortableIterationConsumer<Entity> consumer=c.getArgument(2);
                for(var entity:entities)if(consumer.accept(entity)==AbortableIterationConsumer.Continuation.ABORT)break;
                return null;
            }).when(getter).get(any(EntityTypeTest.class),any(AABB.class),any(AbortableIterationConsumer.class));
        }
        Entity entity(EntityType<?> original,int flags,BlockPos pos) {
            var type=spy(original);
            doReturn((flags&1)!=0).when(type).is(SafetyCollector.THREATS);
            doReturn((flags&2)!=0).when(type).is(SafetyCollector.PROTECTORS);
            doReturn((flags&4)!=0).when(type).is(SafetyCollector.THREATS_EXCLUDED);
            doReturn((flags&8)!=0).when(type).is(SafetyCollector.PROTECTORS_EXCLUDED);
            var entity=original==EntityType.PLAYER?mock(Player.class):mock(Entity.class);
            doReturn(type).when(entity).getType();
            when(entity.getUUID()).thenReturn(UUID.randomUUID());when(entity.isAlive()).thenReturn(true);
            when(entity.blockPosition()).thenReturn(pos);when(entity.position()).thenReturn(Vec3.atCenterOf(pos));
            entities.add(entity);return entity;
        }
        ObservationMetadata metadata(){return new ObservationMetadata(town.id(),"minecraft:overworld",3,123,4,5,6);}
        HousingScanner.Observation housing(int beds,boolean complete) {
            Set<BlockPos> heads=new HashSet<>();for(int i=0;i<beds;i++)heads.add(new BlockPos(i-4,64,0));
            var snapshot=complete?new HousingSnapshot(10,beds,beds,0,beds,beds,0,0,0,true)
                :HousingSnapshot.unavailable(10,beds);
            return new HousingScanner.Observation(snapshot,heads);
        }
        SafetySnapshot scan(HousingScanner.Observation housing,SafetyCollector.Rules rules){
            return SafetyCollector.collect(level,town,8,housing,metadata(),rules);
        }
        SafetySnapshot scan(int beds){return scan(housing(beds,true),new SafetyCollector.Rules(true,1,4096,4096,262144));}
        void noLoads(){
            assertTrue(mockingDetails(source).getInvocations().stream().allMatch(i->i.getMethod().getName().equals("getChunkNow")));
            verify(level,never()).getBrightness(eq(LightLayer.SKY),any());
        }
    }
    @Test void S01_countsAndDeduplicates() {
        var w=new World();var zombie=w.entity(EntityType.ZOMBIE,1,BlockPos.ZERO.above(64));
        w.entity(EntityType.CREEPER,1,new BlockPos(1,64,1));w.entity(EntityType.IRON_GOLEM,2,new BlockPos(-1,64,1));
        w.entities.add(zombie);var s=w.scan(0);
        assertEquals(2,s.threatsObserved());assertEquals(1,s.protectorsObserved());assertEquals(1,s.duplicateEntities());
        assertEquals(4,s.entitiesInspected());assertEquals(Status.COMPLETE,s.entityScanStatus());w.noLoads();
    }
    @Test void S02_lifePlayerAndAllBounds() {
        var w=new World();
        for(var pos:List.of(new BlockPos(9,64,0),new BlockPos(-9,64,0),new BlockPos(0,73,0),new BlockPos(0,55,0),new BlockPos(0,64,9),new BlockPos(0,64,-9)))
            w.entity(EntityType.ZOMBIE,1,pos);
        when(w.entity(EntityType.ZOMBIE,1,new BlockPos(0,64,0)).isAlive()).thenReturn(false);
        when(w.entity(EntityType.ZOMBIE,1,new BlockPos(0,64,0)).isRemoved()).thenReturn(true);
        w.entity(EntityType.PLAYER,3,new BlockPos(0,64,0));
        w.entity(EntityType.ZOMBIE,1,new BlockPos(-8,56,-8));
        var s=w.scan(0);assertEquals(1,s.threatsObserved());assertEquals(9,s.rejectedEntities());w.noLoads();
    }
    @Test void S03_exactResourcesAndNoNeutralInference() throws Exception {
        var expected=Set.of("blaze","bogged","breeze","cave_spider","creeper","drowned","elder_guardian","ender_dragon","endermite","evoker","ghast","guardian","hoglin","husk","magma_cube","phantom","piglin_brute","pillager","ravager","shulker","silverfish","skeleton","slime","spider","stray","vex","vindicator","warden","witch","wither","wither_skeleton","zoglin","zombie");
        assertEquals(33,expected.size());assertEquals(expected,tag("threats"));assertEquals(Set.of("iron_golem"),tag("protectors"));
        assertTrue(tag("threats_excluded").isEmpty());assertTrue(tag("protectors_excluded").isEmpty());
        var w=new World();for(var type:List.of(EntityType.ENDERMAN,EntityType.PIGLIN,EntityType.ZOMBIFIED_PIGLIN,EntityType.WOLF,EntityType.POLAR_BEAR,EntityType.BEE))w.entity(type,0,new BlockPos(0,64,0));
        var s=w.scan(0);assertEquals(0,s.threatsObserved());assertEquals(0,s.protectorsObserved());
        for(var e:w.entities)assertTrue(mockingDetails(e).getInvocations().stream().allMatch(i->Set.of("isAlive","isRemoved","position","blockPosition","getUUID","getType").contains(i.getMethod().getName())));
    }
    private Set<String> tag(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream("/data/hometown/tags/entity_type/safety/"+name+".json")) {
            assertNotNull(stream);var json=JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
            assertFalse(json.get("replace").getAsBoolean());var ids=new HashSet<String>();
            for(var v:json.getAsJsonArray("values"))assertTrue(ids.add(v.getAsString().replace("minecraft:","")));
            return ids;
        }
    }
    @Test void S04_exclusionsAreIndependent() {
        for(int flags:new int[]{3,7,15}) {
            var w=new World();w.entity(EntityType.ZOMBIE,flags,new BlockPos(0,64,0));var s=w.scan(0);
            assertEquals(flags==3?1:0,s.threatsObserved());assertEquals(flags==7?1:0,s.protectorsObserved());
            assertEquals(1,s.collisions().size());
        }
    }
    @Test void S05_S06_onlyBlockLightAndThreshold() {
        var w=new World();for(int i=-4;i<0;i++)w.light.put(new BlockPos(i,65,0),1);
        assertEquals(100,w.scan(4).residentialLightingPercent().orElseThrow());
        w.light.remove(new BlockPos(-4,65,0));assertEquals(75,w.scan(4).residentialLightingPercent().orElseThrow());
        w.light.clear();assertEquals(0,w.scan(4).residentialLightingPercent().orElseThrow());
        assertEquals(100,w.scan(w.housing(4,true),new SafetyCollector.Rules(true,0,4096,4096,100)).residentialLightingPercent().orElseThrow());
        w.noLoads();
    }
    @Test void S07_invalidSampleHasNoAlternate() {
        var w=new World();w.solid.add(new BlockPos(-4,65,0));w.light.put(new BlockPos(-3,65,0),1);
        var s=w.scan(2);assertEquals(Status.PARTIAL,s.lightingScanStatus());
        assertEquals(1,s.unassessedBedSamples());assertEquals(100,s.observedLightingPercent().orElseThrow());
        assertTrue(s.residentialLightingPercent().isEmpty());assertEquals(1,s.lightingReasonCounts().get(Reason.INVALID_SAMPLE_POSITION));
        verify(w.level,never()).getBrightness(any(),eq(new BlockPos(-4,65,0)));assertEquals(2,s.blocksInspected());
    }
    @Test void S08_emptyAndUnknownRemainDistinct() {
        var w=new World();var empty=w.scan(0);
        assertEquals(Status.COMPLETE,empty.lightingScanStatus());assertEquals(LightingCondition.NO_ENCLOSED_BEDS,empty.lightingCondition());
        assertTrue(empty.residentialLightingPercent().isEmpty());
        var unknown=w.scan(w.housing(0,false),new SafetyCollector.Rules(true,1,10,10,10));
        assertEquals(Status.UNAVAILABLE,unknown.lightingScanStatus());assertEquals(LightingCondition.INCOMPLETE,unknown.lightingCondition());
    }
    @Test void S09_S10_panelsStayIndependentAndHousingUnchanged() {
        var w=new World();var housing=w.housing(2,true);var before=housing.snapshot();
        w.absent.add("0:0"); // beds at negative x are loaded while another required town chunk is missing
        var s=w.scan(housing,new SafetyCollector.Rules(true,1,10,10,10));
        assertEquals(Status.PARTIAL,s.entityScanStatus());assertEquals(Status.COMPLETE,s.lightingScanStatus());
        assertEquals(0,s.residentialLightingPercent().orElseThrow());
        w.light.put(new BlockPos(-4,65,0),1);
        assertEquals(50,w.scan(housing,new SafetyCollector.Rules(true,1,10,10,10)).residentialLightingPercent().orElseThrow());
        assertEquals(before,housing.snapshot());w.noLoads();
        w.absent.add("-1:0");var unavailable=w.scan(2);
        assertEquals(Status.UNAVAILABLE,unavailable.lightingScanStatus());assertTrue(unavailable.lightingReasonCounts().containsKey(Reason.UNLOADED_CHUNKS));
    }
    @Test void S11_rawPrecisionAndMissingHousingScope() {
        var w=new World();for(int x=-4;x<4;x++)w.light.put(new BlockPos(x,65,0),1);
        var s=w.scan(9);assertEquals(800.0/9,s.residentialLightingPercent().orElseThrow());assertEquals(89,Math.round(s.residentialLightingPercent().orElseThrow()));
        var p=w.scan(w.housing(9,false),new SafetyCollector.Rules(true,1,20,20,20));
        assertEquals(Status.PARTIAL,p.lightingScanStatus());assertEquals(800.0/9,p.observedLightingPercent().orElseThrow());assertTrue(p.residentialLightingPercent().isEmpty());
    }
    @Test void S12_entityLimitsCountRejectionsAndRetainKnownFacts() {
        for(var rules:List.of(new SafetyCollector.Rules(true,1,1,10,10),new SafetyCollector.Rules(true,1,10,1,10))) {
            var w=new World();w.entity(EntityType.ZOMBIE,1,new BlockPos(0,64,0));w.entity(EntityType.CREEPER,1,new BlockPos(1,64,0));
            var s=w.scan(w.housing(0,true),rules);
            assertEquals(1,s.entitiesInspected());assertEquals(1,s.threatsObserved());assertEquals(Status.PARTIAL,s.entityScanStatus());
            assertTrue(s.entityReasonCounts().containsKey(Reason.SCAN_LIMIT_REACHED));
        }
        var w=new World();w.entity(EntityType.ZOMBIE,1,new BlockPos(0,64,0));
        var zero=w.scan(w.housing(1,true),new SafetyCollector.Rules(true,1,0,10,0));
        assertEquals(0,zero.entitiesInspected());assertEquals(Status.UNAVAILABLE,zero.entityScanStatus());
        assertEquals(0,zero.blocksInspected());assertEquals(Status.UNAVAILABLE,zero.lightingScanStatus());
    }
    @Test void S12_blockLimitsAndDisabledHaveNoWorldEffects() {
        var w=new World();var s=w.scan(w.housing(3,true),new SafetyCollector.Rules(true,1,10,10,1));
        assertEquals(1,s.blocksInspected());assertEquals(Status.PARTIAL,s.lightingScanStatus());assertEquals(2,s.unassessedBedSamples());
        clearInvocations(w.level,w.source,w.getter,w.chunk);
        var disabled=w.scan(w.housing(2,true),new SafetyCollector.Rules(false,1,10,10,1));
        assertEquals(Status.DISABLED,disabled.entityScanStatus());assertEquals(Status.DISABLED,disabled.lightingScanStatus());
        verifyNoInteractions(w.level,w.source,w.getter,w.chunk);
    }
    @Test void Q05_sampleBoundaryAndCodecPreserveUnknown() {
        var w=new World();var housing=new HousingScanner.Observation(new HousingSnapshot(1,1,1,0,1,1,0,0,0,true),Set.of(new BlockPos(0,72,0)));
        var s=w.scan(housing,new SafetyCollector.Rules(true,1,10,10,10));
        assertEquals(Reason.SAMPLE_OUT_OF_BOUNDS,s.lightingReasonCounts().keySet().iterator().next());
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{s.write(buffer);assertEquals(s,SafetySnapshot.read(buffer));}finally{buffer.release();}
        assertTrue(SafetyDebugReport.format(s,0).contains("SAMPLE_OUT_OF_BOUNDS"));
        assertTrue(SafetyDebugReport.format(s,0).lines().count()<=32);w.noLoads();
    }
}
