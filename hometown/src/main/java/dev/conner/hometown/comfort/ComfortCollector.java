package dev.conner.hometown.comfort;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import dev.conner.hometown.housing.HousingScanner;
import dev.conner.hometown.observation.*;
import static dev.conner.hometown.comfort.ComfortSnapshot.*;

/** Reads only proven existing room interiors and their first face-adjacent boundaries. */
public final class ComfortCollector {
    public static final TagKey<Block> EXCLUDED=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("hometown","comfort/excluded"));
    private ComfortCollector(){}
    private static void reason(Map<Reason,Integer> map,Reason reason){map.merge(reason,1,Integer::sum);}
    public static ComfortSnapshot collect(HousingScanner.Observation housing,ObservationMetadata metadata,
            ComfortSettings settings,ComfortRules.Definitions definitions,BlockObservationCache cache) {
        var rooms=new ArrayList<Room>();var reasons=new EnumMap<Reason,Integer>(Reason.class);
        int attempted=0,start=cache.inspections();
        if(!settings.enabled())return ComfortEvaluator.town(metadata,settings,housing.snapshot().scanComplete(),
            housing.snapshot().enclosedBeds(),rooms,reasons,0,0,cache.limit());
        if(!housing.snapshot().scanComplete())reason(reasons,Reason.ROOM_DATA_INCOMPLETE);
        var geometry=housing.rooms().stream().sorted(Comparator.comparing(dev.conner.hometown.room.RoomGeometry::key)).toList();
        if(geometry.stream().mapToInt(room->room.beds().size()).sum()!=housing.enclosedBeds().size()
            ||housing.snapshot().enclosedBeds()!=housing.enclosedBeds().size())reason(reasons,Reason.ROOM_DATA_INCOMPLETE);
        for(var room:geometry) {
            var rr=new EnumMap<Reason,Integer>(Reason.class);
            var hits=new EnumMap<ComfortCategory,Integer>(ComfortCategory.class);
            int interior=0,boundary=0,meaningful=0,failed=0,excluded=0;
            if(attempted>=settings.maxRooms())reason(rr,Reason.SCAN_LIMIT_REACHED);
            else {
                attempted++;
                if(!room.complete())reason(rr,Reason.ROOM_DATA_INCOMPLETE);
                var positions=new HashSet<>(room.interior());positions.addAll(room.boundary());
                var ordered=positions.stream().sorted(Comparator.comparingInt((BlockPos p)->p.getX())
                    .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ)).toList();
                for(var position:ordered) {
                    if(interior+boundary>=settings.maxCellsPerRoom()){reason(rr,Reason.SCAN_LIMIT_REACHED);break;}
                    // A room's logical budget includes a reused physical position.
                    var copy=cache.read(position,false);
                    if(copy.failure()==BlockObservationCache.Failure.SCAN_LIMIT_REACHED){reason(rr,Reason.SCAN_LIMIT_REACHED);break;}
                    if(room.interior().contains(position))interior++;else boundary++;
                    if(copy.failure()!=BlockObservationCache.Failure.NONE){reason(rr,Reason.valueOf(copy.failure().name()));continue;}
                    meaningful++;
                    if(copy.state().is(EXCLUDED)){excluded++;continue;}
                    for(var category:ComfortCategory.values()) {
                        if(!settings.categories().get(category).enabled()||!copy.state().is(category.tag))continue;
                        if(definitions.matches(category,copy.state()))hits.merge(category,1,Integer::sum);else failed++;
                    }
                }
            }
            var status=rr.isEmpty()?Status.COMPLETE:meaningful>0?Status.PARTIAL:Status.UNAVAILABLE;
            rooms.add(ComfortEvaluator.room(room.key(),room.beds().size(),interior,boundary,status,rr,hits,failed,excluded,settings));
            rr.forEach((key,value)->reasons.merge(key,value,Integer::sum));
        }
        return ComfortEvaluator.town(metadata,settings,housing.snapshot().scanComplete(),housing.snapshot().enclosedBeds(),
            rooms,reasons,attempted,cache.inspections()-start,cache.limit());
    }
}
