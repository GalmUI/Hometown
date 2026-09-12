package dev.conner.hometown.food;

import dev.conner.hometown.observation.*;
import dev.conner.hometown.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Loaded-only, bounded crop discovery. Uses palette filtering before position reads and never loads chunks. */
public final class FoodGrowingCollector {
    private FoodGrowingCollector() {}
    private static final class Family {
        int growing,mature,unassessed;
    }
    private static void reason(Map<FoodGrowingSnapshot.Reason,Integer> reasons,FoodGrowingSnapshot.Reason reason) {
        reasons.merge(reason,1,Integer::sum);
    }

    public static FoodGrowingSnapshot collect(ServerLevel level, Settlement town, int vertical, ObservationMetadata metadata,
            boolean enabled, CropRules.Definitions definitions, BlockObservationCache cache) {
        Objects.requireNonNull(town);Objects.requireNonNull(metadata);Objects.requireNonNull(definitions);Objects.requireNonNull(cache);
        var reasons=new EnumMap<FoodGrowingSnapshot.Reason,Integer>(FoodGrowingSnapshot.Reason.class);
        var families=new TreeMap<ResourceLocation,Family>();
        int growing=0,mature=0,unassessed=0,chunks=0,loaded=0,candidateSections=0,paletteInspections=0,blockInspections=0;
        if(!enabled)return new FoodGrowingSnapshot(metadata,false,FoodGrowingSnapshot.Status.DISABLED,Map.of(),0,0,0,0,List.of(),0,0,0,0,0,cache.inspections(),cache.limit());
        if(level==null){reason(reasons,FoodGrowingSnapshot.Reason.NO_SETTLEMENT_DATA);return new FoodGrowingSnapshot(metadata,true,FoodGrowingSnapshot.Status.UNAVAILABLE,reasons,0,0,0,0,List.of(),0,0,0,0,0,cache.inspections(),cache.limit());}

        var bell=town.bellPosition();int radius=town.radius();
        int minX=bell.getX()-radius,maxX=bell.getX()+radius,minZ=bell.getZ()-radius,maxZ=bell.getZ()+radius;
        int minY=Math.max(level.getMinBuildHeight(),bell.getY()-vertical),maxY=Math.min(level.getMaxBuildHeight()-1,bell.getY()+vertical);
        outer:
        for(int chunkX=minX>>4;chunkX<=maxX>>4;chunkX++) {
            for(int chunkZ=minZ>>4;chunkZ<=maxZ>>4;chunkZ++) {
                chunks++;
                net.minecraft.world.level.chunk.LevelChunk chunk;
                try { chunk=level.getChunkSource().getChunkNow(chunkX,chunkZ); }
                catch(RuntimeException error){reason(reasons,FoodGrowingSnapshot.Reason.INTERNAL_ERROR);continue;}
                if(chunk==null){reason(reasons,FoodGrowingSnapshot.Reason.UNLOADED_CHUNKS);continue;}
                loaded++;
                net.minecraft.world.level.chunk.LevelChunkSection[] sections;
                try { sections=chunk.getSections(); }
                catch(RuntimeException error){reason(reasons,FoodGrowingSnapshot.Reason.INTERNAL_ERROR);continue;}
                if(sections==null){reason(reasons,FoodGrowingSnapshot.Reason.INTERNAL_ERROR);continue;}
                for(int sectionIndex=0;sectionIndex<sections.length;sectionIndex++) {
                    int sectionY=level.getSectionYFromSectionIndex(sectionIndex),baseY=sectionY<<4;
                    int sy0=Math.max(minY,baseY),sy1=Math.min(maxY,baseY+15);
                    if(sy0>sy1)continue;
                    var section=sections[sectionIndex];
                    if(section==null||section.hasOnlyAir()||definitions.rules().isEmpty())continue;
                    final boolean[] budget={false};final int[] palette={0};
                    boolean candidate;
                    try {
                        candidate=section.maybeHas(state->{
                            if(cache.consumeInspection()==BlockObservationCache.Failure.SCAN_LIMIT_REACHED){budget[0]=true;return false;}
                            palette[0]++;
                            return definitions.contains(state.getBlock());
                        });
                    } catch(RuntimeException error){reason(reasons,FoodGrowingSnapshot.Reason.INTERNAL_ERROR);continue;}
                    paletteInspections+=palette[0];
                    if(budget[0]){reason(reasons,FoodGrowingSnapshot.Reason.SCAN_LIMIT_REACHED);break outer;}
                    if(!candidate)continue;
                    candidateSections++;
                    int sx0=Math.max(minX,chunkX<<4),sx1=Math.min(maxX,(chunkX<<4)+15);
                    int sz0=Math.max(minZ,chunkZ<<4),sz1=Math.min(maxZ,(chunkZ<<4)+15);
                    for(int x=sx0;x<=sx1;x++)for(int y=sy0;y<=sy1;y++)for(int z=sz0;z<=sz1;z++) {
                        blockInspections++;
                        var sample=cache.read(new BlockPos(x,y,z),false);
                        if(sample.failure()==BlockObservationCache.Failure.SCAN_LIMIT_REACHED){reason(reasons,FoodGrowingSnapshot.Reason.SCAN_LIMIT_REACHED);break outer;}
                        if(sample.failure()!=BlockObservationCache.Failure.NONE){
                            reason(reasons,sample.failure()==BlockObservationCache.Failure.UNLOADED_CHUNKS?FoodGrowingSnapshot.Reason.UNLOADED_CHUNKS:FoodGrowingSnapshot.Reason.INTERNAL_ERROR);continue;
                        }
                        var rule=definitions.rule(sample.state());if(rule.isEmpty())continue;
                        growing++;var family=families.computeIfAbsent(rule.get().family(),ignored->new Family());family.growing++;
                        var isMature=definitions.mature(rule.get(),sample.state());
                        if(isMature.isEmpty()){unassessed++;family.unassessed++;reason(reasons,FoodGrowingSnapshot.Reason.MATURITY_UNASSESSED);}
                        else if(isMature.get()){mature++;family.mature++;}
                    }
                }
            }
        }
        var records=new ArrayList<FoodGrowingSnapshot.FamilyRecord>();
        families.forEach((id,family)->records.add(new FoodGrowingSnapshot.FamilyRecord(id,family.growing,
                family.unassessed==0?OptionalInt.of(family.mature):OptionalInt.empty(),family.unassessed==0?FoodGrowingSnapshot.MaturityStatus.ASSESSED:FoodGrowingSnapshot.MaturityStatus.UNASSESSED)));
        boolean discoveryIssue=reasons.keySet().stream().anyMatch(reason->reason!=FoodGrowingSnapshot.Reason.MATURITY_UNASSESSED);
        boolean known=loaded>0&&(paletteInspections>0||blockInspections>0||definitions.rules().isEmpty());
        var status=!discoveryIssue?FoodGrowingSnapshot.Status.COMPLETE:known?FoodGrowingSnapshot.Status.PARTIAL:FoodGrowingSnapshot.Status.UNAVAILABLE;
        return new FoodGrowingSnapshot(metadata,true,status,reasons,growing,mature,unassessed,records.size(),records,
                chunks,loaded,candidateSections,paletteInspections,blockInspections,cache.inspections(),cache.limit());
    }
}
