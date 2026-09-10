package dev.conner.hometown.observation;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Request-local copied block/light evidence shared by new modules only. Never persisted. */
public final class BlockObservationCache {
    public enum Failure { NONE, UNLOADED_CHUNKS, SAMPLE_OUT_OF_BOUNDS, SCAN_LIMIT_REACHED, INTERNAL_ERROR }
    public record Sample(BlockState state,OptionalInt blockLight,Failure failure) {}
    private final ServerLevel level;
    private final AABB bounds;
    private final int limit;
    private int inspections;
    private final Map<BlockPos,Sample> copies=new HashMap<>();
    public BlockObservationCache(ServerLevel level,AABB bounds,int limit) {
        this.level=level;this.bounds=bounds;this.limit=limit;
        if(limit<0)throw new IllegalArgumentException("Negative block budget");
    }
    public int inspections(){return inspections;}
    public int limit(){return limit;}
    public Sample read(BlockPos position,boolean lighting) {
        var old=copies.get(position);
        if(old!=null)return old;
        if(inspections>=limit)return new Sample(null,OptionalInt.empty(),Failure.SCAN_LIMIT_REACHED);
        inspections++;
        Sample sample;
        if(!bounds.contains(Vec3.atCenterOf(position)))sample=new Sample(null,OptionalInt.empty(),Failure.SAMPLE_OUT_OF_BOUNDS);
        else if(level==null)sample=new Sample(null,OptionalInt.empty(),Failure.UNLOADED_CHUNKS);
        else {
            try {
                var chunk=level.getChunkSource().getChunkNow(position.getX()>>4,position.getZ()>>4);
                if(chunk==null)sample=new Sample(null,OptionalInt.empty(),Failure.UNLOADED_CHUNKS);
                else {
                    var state=chunk.getBlockState(position);
                    var light=lighting&&state.isAir()?OptionalInt.of(level.getBrightness(LightLayer.BLOCK,position)):OptionalInt.empty();
                    sample=new Sample(state,light,Failure.NONE);
                }
            } catch(RuntimeException error){sample=new Sample(null,OptionalInt.empty(),Failure.INTERNAL_ERROR);}
        }
        copies.put(position.immutable(),sample);
        return sample;
    }
}
