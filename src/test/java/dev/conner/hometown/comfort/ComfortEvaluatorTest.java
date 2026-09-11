package dev.conner.hometown.comfort;

import dev.conner.hometown.observation.ObservationMetadata;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.conner.hometown.comfort.ComfortSnapshot.*;

class ComfortEvaluatorTest {
    private ComfortSettings settings() { return settings(false); }
    private ComfortSettings settings(boolean allEnabled) {
        var categories=new EnumMap<ComfortCategory,ComfortSettings.Category>(ComfortCategory.class);
        for(var c:ComfortCategory.values()) categories.put(c,new ComfortSettings.Category(allEnabled||c.defaultEnabled,c.defaultWeight));
        return new ComfortSettings(true,categories,512,65536);
    }
    private ObservationMetadata metadata(){return new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",9,123,4,5,6);}
    private Map<ComfortCategory,Integer> hits(ComfortCategory... categories) {
        var hits=new EnumMap<ComfortCategory,Integer>(ComfortCategory.class);
        for(var category:categories)hits.put(category,1);
        return hits;
    }
    private Room room(int beds,Map<ComfortCategory,Integer> hits,ComfortSettings settings) {
        return ComfortEvaluator.room(new BlockPos(beds,64,0),beds,3,4,Status.COMPLETE,Map.of(),hits,0,0,settings);
    }

    @Test void C01_bareRoomIsZeroAndBare() {
        var room=room(1,Map.of(),settings());
        assertEquals(75,room.enabledWeight());
        assertEquals(0,room.presentWeight());
        assertEquals(0,room.score().orElseThrow());
        assertEquals(Band.BARE,room.band().orElseThrow());
        assertEquals(Presence.FALSE,room.categoryPresence().get(ComfortCategory.STORAGE));
        assertEquals(Presence.DISABLED,room.categoryPresence().get(ComfortCategory.SEATING));
    }

    @Test void C02_capsDefaultsAndDisabledHits() {
        var twentyBooks=hits(ComfortCategory.STORAGE,ComfortCategory.BOOKS,ComfortCategory.SEATING);
        twentyBooks.put(ComfortCategory.BOOKS,20);
        var room=room(1,twentyBooks,settings());
        assertEquals(20,room.presentWeight());
        assertEquals(20.0/75*100,room.score().orElseThrow(),1e-12);
        assertEquals(27,(int)Math.floor(room.score().orElseThrow()+0.5));
        assertEquals(Band.BASIC,room.band().orElseThrow());
        assertEquals(19,room.ignoredDuplicateHits());
        assertEquals(Presence.DISABLED,room.categoryPresence().get(ComfortCategory.SEATING));
    }

    @Test void C03_sixCoreCategoriesReachStandaloneHundred() {
        var core=hits(ComfortCategory.STORAGE,ComfortCategory.LIGHTING,ComfortCategory.DECOR,
                ComfortCategory.BOOKS,ComfortCategory.PLANTS,ComfortCategory.AMENITIES);
        var full=room(1,core,settings());
        assertEquals(100,full.score().orElseThrow());
        assertEquals(Band.WELL_FURNISHED,full.band().orElseThrow());
        var basic=room(1,hits(ComfortCategory.STORAGE,ComfortCategory.LIGHTING),settings());
        assertEquals(100.0/3,basic.score().orElseThrow(),1e-12);
        assertEquals(33,(int)Math.floor(basic.score().orElseThrow()+0.5));
        assertEquals(Band.BASIC,basic.band().orElseThrow());
    }

    @Test void C04_enabledEmptyCategoriesStayInDenominator() {
        var all=settings(true);
        var core=hits(ComfortCategory.STORAGE,ComfortCategory.LIGHTING,ComfortCategory.DECOR,
                ComfortCategory.BOOKS,ComfortCategory.PLANTS,ComfortCategory.AMENITIES);
        assertEquals(100,all.enabledWeight());
        assertEquals(75,room(1,core,all).score().orElseThrow());
        core.put(ComfortCategory.SEATING,1);core.put(ComfortCategory.TABLES,1);
        assertEquals(100,room(1,core,all).score().orElseThrow());
    }

    @Test void C05_bedWeightedTownAggregationUsesRawRoomScores() {
        var settings=settings();
        var one=room(1,hits(ComfortCategory.STORAGE,ComfortCategory.LIGHTING,ComfortCategory.DECOR,
                ComfortCategory.BOOKS,ComfortCategory.PLANTS,ComfortCategory.AMENITIES),settings);
        var twenty=ComfortEvaluator.room(new BlockPos(2,64,0),3,1,1,Status.COMPLETE,Map.of(),
                hits(ComfortCategory.LIGHTING),0,0,settings);
        var town=ComfortEvaluator.town(metadata(),settings,true,4,List.of(one,twenty),Map.of(),2,2,100);
        assertEquals(40,town.residentialComfortPercent().orElseThrow());
        assertEquals(Band.BASIC,town.band().orElseThrow());
        assertEquals(4,town.assessedEnclosedBeds());
    }

    @Test void C08_partialNoBedsAndNoWeightAreDistinctAndCodecStable() {
        var settings=settings();
        var complete=room(1,hits(ComfortCategory.STORAGE),settings);
        var partial=ComfortEvaluator.room(new BlockPos(3,64,0),1,1,0,Status.PARTIAL,
                Map.of(Reason.ROOM_DATA_INCOMPLETE,1),Map.of(),0,0,settings);
        assertEquals(Presence.UNKNOWN,partial.categoryPresence().get(ComfortCategory.STORAGE));
        var town=ComfortEvaluator.town(metadata(),settings,false,2,List.of(complete,partial),
                Map.of(Reason.ROOM_DATA_INCOMPLETE,1),2,2,100);
        assertEquals(Status.PARTIAL,town.scanStatus());
        assertEquals(Condition.INCOMPLETE,town.condition());
        assertTrue(town.residentialComfortPercent().isEmpty());
        assertTrue(town.observedRoomComfortPercent().isPresent());
        assertEquals(1,town.assessedRooms());

        var empty=ComfortEvaluator.town(metadata(),settings,true,0,List.of(),Map.of(),0,0,100);
        assertEquals(Condition.NO_ENCLOSED_BEDS,empty.condition());
        assertTrue(empty.residentialComfortPercent().isEmpty());

        var categories=new EnumMap<ComfortCategory,ComfortSettings.Category>(ComfortCategory.class);
        for(var c:ComfortCategory.values())categories.put(c,new ComfortSettings.Category(false,c.defaultWeight));
        var zeroWeight=new ComfortSettings(true,categories,512,65536);
        var noWeight=ComfortEvaluator.town(metadata(),zeroWeight,true,1,List.of(room(1,Map.of(),zeroWeight)),Map.of(),1,1,100);
        assertEquals(Condition.NO_ENABLED_WEIGHT,noWeight.condition());
        assertTrue(noWeight.residentialComfortPercent().isEmpty());

        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { town.write(buffer);assertEquals(town,ComfortSnapshot.read(buffer));assertEquals(0,buffer.readableBytes()); }
        finally { buffer.release(); }
    }
}
