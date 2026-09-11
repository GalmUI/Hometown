package dev.conner.hometown.safety;

import dev.conner.hometown.housing.HousingScanner;
import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import static dev.conner.hometown.safety.SafetySnapshot.*;

/** One bounded explicit request. Does not call AI, inventory, or chunk-loading APIs. */
public final class SafetyCollector {
    public static final TagKey<EntityType<?>> THREATS=tag("threats"), PROTECTORS=tag("protectors"),
            THREATS_EXCLUDED=tag("threats_excluded"), PROTECTORS_EXCLUDED=tag("protectors_excluded");
    private static TagKey<EntityType<?>> tag(String name) { return TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath("hometown","safety/"+name)); }
    public record Rules(boolean enabled,int minimumBlockLight,int entityLimit,int sharedEntityLimit,int blockLimit) {
        public Rules { if(minimumBlockLight<0||minimumBlockLight>15||entityLimit<0||sharedEntityLimit<0||blockLimit<0)throw new IllegalArgumentException("Invalid Safety rules"); }
        public static Rules current() {
            return new Rules(dev.conner.hometown.config.HometownServerConfig.SAFETY_ENABLED.get(),
                dev.conner.hometown.config.HometownServerConfig.MINIMUM_BLOCK_LIGHT.get(),
                dev.conner.hometown.config.HometownServerConfig.SAFETY_ENTITY_LIMIT.get(),
                dev.conner.hometown.config.HometownServerConfig.NEW_ENTITY_LIMIT.get(),
                dev.conner.hometown.config.HometownServerConfig.NEW_BLOCK_LIMIT.get());
        }
    }
    private SafetyCollector() {}
    private static void reason(Map<Reason,Integer> m,Reason r) { m.merge(r,1,Integer::sum); }
    public static SafetySnapshot collect(ServerLevel level,Settlement town,int vertical,HousingScanner.Observation housing,
            ObservationMetadata metadata,Rules rules) {
        return collect(level,town,vertical,housing,metadata,rules,new dev.conner.hometown.observation.BlockObservationCache(
            level,SettlementQueries.bounds(town.bellPosition(),town.radius(),vertical),rules.blockLimit()));
    }
    public static SafetySnapshot collect(ServerLevel level,Settlement town,int vertical,HousingScanner.Observation housing,
            ObservationMetadata metadata,Rules rules,dev.conner.hometown.observation.BlockObservationCache cache) {
        var er=new EnumMap<Reason,Integer>(Reason.class);var lr=new EnumMap<Reason,Integer>(Reason.class);
        var threats=new TreeMap<String,Integer>();var protectors=new TreeMap<String,Integer>();var collisions=new TreeMap<String,Integer>();
        var invalid=new ArrayList<BlockPos>();var seen=new HashSet<UUID>();
        int limit=Math.min(rules.entityLimit(),rules.sharedEntityLimit());
        int[] counters=new int[3]; // attempted, rejected, duplicate
        int loaded=0,required=0,lit=0,unlit=0,blocks=0;
        var samples=housing.enclosedBeds().stream().map(BlockPos::above).distinct()
            .sorted(Comparator.comparingInt((BlockPos p)->p.getX()).thenComparingInt(p->p.getY()).thenComparingInt(p->p.getZ())).toList();
        var bounds=SettlementQueries.bounds(town.bellPosition(),town.radius(),vertical);
        if(rules.enabled()) {
            if(level==null) reason(er,Reason.NO_SETTLEMENT_DATA);
            else {
                for(int x=(town.bellPosition().getX()-town.radius())>>4;x<=(town.bellPosition().getX()+town.radius())>>4;x++)
                    for(int z=(town.bellPosition().getZ()-town.radius())>>4;z<=(town.bellPosition().getZ()+town.radius())>>4;z++){
                        required++;
                        if(level.getChunkSource().getChunkNow(x,z)!=null)loaded++;else reason(er,Reason.UNLOADED_CHUNKS);
                    }
                try {
                    // Abort the native spatial query itself: never allocate an unbounded list to sort.
                    // Complete counts are order-independent; capped observations retain native traversal order.
                    level.getEntities().get(EntityTypeTest.forClass(Entity.class),bounds,entity -> {
                        if(counters[0]>=limit){reason(er,Reason.SCAN_LIMIT_REACHED);return Continuation.ABORT;}
                        counters[0]++;
                        if(!entity.isAlive()||entity.isRemoved()||!bounds.contains(entity.position())
                                || !SettlementQueries.loaded(level,entity.blockPosition())) { counters[1]++;return Continuation.CONTINUE; }
                        if(!seen.add(entity.getUUID())) { counters[2]++;return Continuation.CONTINUE; }
                        var type=entity.getType();boolean t=type.is(THREATS),p=type.is(PROTECTORS);
                        boolean te=type.is(THREATS_EXCLUDED),pe=type.is(PROTECTORS_EXCLUDED);
                        String id=type.builtInRegistryHolder().key().location().toString();
                        var classification=SafetyEvaluator.classify(entity instanceof Player,t,te,p,pe);
                        if(t&&p)collisions.merge(id+" -> "+classification.name(),1,Integer::sum);
                        switch(classification) {
                            case THREAT -> threats.merge(id,1,Integer::sum);
                            case PROTECTOR -> protectors.merge(id,1,Integer::sum);
                            case NEITHER -> counters[1]++;
                        }
                        return Continuation.CONTINUE;
                    });
                } catch(RuntimeException ex) { reason(er,Reason.INTERNAL_ERROR); }
            }
            if(!housing.snapshot().scanComplete()) reason(lr,Reason.ROOM_DATA_INCOMPLETE);
            if(level==null) reason(lr,Reason.NO_SETTLEMENT_DATA);
            for(BlockPos sample:samples) {
                if(blocks>=rules.blockLimit()){reason(lr,Reason.SCAN_LIMIT_REACHED);break;}
                blocks++;
                Reason failure=null;
                var copied=cache.read(sample,true);
                if(copied.failure()!=dev.conner.hometown.observation.BlockObservationCache.Failure.NONE)
                    failure=Reason.valueOf(copied.failure().name());
                else if(!copied.state().isAir())failure=Reason.INVALID_SAMPLE_POSITION;
                else if(copied.blockLight().orElseThrow()>=rules.minimumBlockLight())lit++;
                else unlit++;
                if(failure!=null){reason(lr,failure);if(invalid.size()<32)invalid.add(sample);}
            }
        }
        boolean enabled=rules.enabled();
        Status es=enabled?SafetyEvaluator.status(er.isEmpty(),counters[0]>0||(loaded>0&&!er.containsKey(Reason.INTERNAL_ERROR)&&!er.containsKey(Reason.SCAN_LIMIT_REACHED))):Status.DISABLED;
        Status ls=enabled?SafetyEvaluator.status(lr.isEmpty(),lit+unlit>0):Status.DISABLED;
        var observed=enabled?SafetyEvaluator.coverage(lit,lit+unlit):OptionalDouble.empty();
        var authoritative=enabled&&ls==Status.COMPLETE&&samples.size()>0?observed:OptionalDouble.empty();
        var condition=!enabled?LightingCondition.DISABLED:ls!=Status.COMPLETE?LightingCondition.INCOMPLETE:
            samples.isEmpty()?LightingCondition.NO_ENCLOSED_BEDS:LightingCondition.OBSERVED;
        return new SafetySnapshot(metadata,enabled,es,er,threats,protectors,collisions,counters[0],counters[1],counters[2],
            limit,loaded,required,ls,lr,samples.size(),lit,unlit,observed,authoritative,condition,rules.minimumBlockLight(),blocks,rules.blockLimit(),invalid);
    }
}
