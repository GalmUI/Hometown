package dev.conner.hometown.settlement;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.component.HometownDataComponents;
import dev.conner.hometown.item.HometownItems;
import dev.conner.hometown.network.*;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import dev.conner.hometown.housing.*;
import dev.conner.hometown.food.*;
import dev.conner.hometown.safety.*;
import dev.conner.hometown.comfort.*;
import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.config.HometownServerConfig;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Existing Ledger owner: synchronous collection, bounded copied observations and session paging. */
public final class TownLedgerService {
    private TownLedgerService() {}
    private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
    private static final int MAX_CACHED_TOWNS=128, SESSION_EXPIRY=1200;
    private record Key(Settlement town,int vertical,FoodRules food,SafetyCollector.Rules safety,ComfortSettings comfort,
            ComfortRules.Definitions comfortRules,int cooldown,String tags,boolean available) {}
    private record Observation(Key key,SettlementStats stats,TownLedgerSnapshot.BellState bell,HousingSnapshot housing,
            FoodSnapshot food,SafetySnapshot safety,ComfortSnapshot comfort) {
        TownLedgerSnapshot page(int page) {
            return TownLedgerSnapshot.of(key.town(),stats,bell,page).withHousing(housing).withFood(food).withSafety(safety).withComfort(comfort);
        }
    }
    private record Session(Observation observation,long requestedAt,long lastAccess,boolean closed) {}
    private static final class State {
        long generation;
        final Map<UUID,Map<UUID,Session>> players=new HashMap<>();
        final Map<Key,Observation> sameTick=new HashMap<>();
        long tick=Long.MIN_VALUE;
    }
    public static void release(MinecraftServer server) { STATES.remove(server); }
    public static void release(ServerPlayer player) {
        var state=STATES.get(player.getServer());if(state!=null)state.players.remove(player.getUUID());
    }
    public static TownLedgerSnapshotPayload respond(ServerPlayer player,RequestTownLedgerPayload request) {
        return respond(player,request,HometownItems.TOWN_LEDGER.get(),HometownDataComponents.SETTLEMENT_ID.get());
    }
    static TownLedgerSnapshotPayload respond(ServerPlayer player,RequestTownLedgerPayload request,
            net.minecraft.world.item.Item ledgerItem,
            net.minecraft.core.component.DataComponentType<dev.conner.hometown.component.SettlementIdComponent> component) {
        var server=player.getServer();
        if(!server.isSameThread())throw new IllegalStateException("Ledger scanning requires the server thread");
        var stack=player.getItemInHand(request.hand());
        if(!player.isAlive()||!stack.is(ledgerItem)){release(player);return error(request,TownLedgerSnapshotPayload.Error.HOLD_LEDGER);}
        var link=stack.get(component);
        var town=link==null?null:HometownSavedData.get(server).getSettlement(link.settlementId()).orElse(null);
        if(town==null){release(player);return error(request,TownLedgerSnapshotPayload.Error.UNKNOWN);}
        var level=server.getLevel(town.dimension());
        long now=server.overworld().getGameTime();
        var key=key(town,level!=null);
        var state=STATES.computeIfAbsent(server,s->new State());
        if(state.tick!=now){state.sameTick.clear();state.tick=now;}
        var sessions=state.players.computeIfAbsent(player.getUUID(),id->new HashMap<>());
        sessions.entrySet().removeIf(e->now<e.getValue().lastAccess()||now-e.getValue().lastAccess()>SESSION_EXPIRY);
        var last=sessions.get(town.id());
        if(request.page()==-1){
            if(last!=null&&last.observation().safety().metadata().requestGeneration()==request.generation())
                sessions.put(town.id(),new Session(last.observation(),last.requestedAt(),now,true));
            return error(request,TownLedgerSnapshotPayload.Error.WAIT);
        }
        if(request.generation()>=0){
            if(last==null||last.closed()||!last.observation().key().equals(key)
                    ||last.observation().safety().metadata().requestGeneration()!=request.generation())
                return error(request,TownLedgerSnapshotPayload.Error.WAIT);
            sessions.put(town.id(),new Session(last.observation(),last.requestedAt(),now,false));
            return new TownLedgerSnapshotPayload(request.requestId(),last.observation().page(request.page()),TownLedgerSnapshotPayload.Error.NONE);
        }
        Observation observation=null;
        long requestedAt=now;
        if(last!=null&&last.observation().key().equals(key)&&now>=last.requestedAt()&&now-last.requestedAt()<key.cooldown()){
            observation=last.observation();requestedAt=last.requestedAt();
        }
        if(observation==null)observation=state.sameTick.get(key);
        // Refuse memory pressure instead of evicting cooldowns to permit repeated scans.
        if(last==null&&sessions.size()>=MAX_CACHED_TOWNS)return error(request,TownLedgerSnapshotPayload.Error.WAIT);
        if(observation==null){
            if(state.sameTick.size()>=MAX_CACHED_TOWNS)return error(request,TownLedgerSnapshotPayload.Error.WAIT);
            observation=collect(level,key,metadata(key,++state.generation,now));
            state.sameTick.put(key,observation);
        }
        sessions.put(town.id(),new Session(observation,requestedAt,now,false));
        return new TownLedgerSnapshotPayload(request.requestId(),observation.page(request.page()),TownLedgerSnapshotPayload.Error.NONE);
    }
    /** Explicit operator observation; never modifies Ledger candidate/session state. */
    public static SafetySnapshot debugSafety(MinecraftServer server,Settlement town) {
        if(!server.isSameThread())throw new IllegalStateException("Safety scanning requires the server thread");
        var level=server.getLevel(town.dimension());var key=key(town,level!=null);
        SettlementStats stats;
        try { stats=SettlementScanner.scan(level,town,key.vertical()); }
        catch(RuntimeException exception) { stats=SettlementStats.unavailable(); }
        HousingScanner.Observation housing;
        try { housing=HousingScanner.observe(level,town,stats,key.vertical()); }
        catch(RuntimeException exception) { housing=new HousingScanner.Observation(HousingSnapshot.unavailable(stats.population(),stats.beds()),Set.of()); }
        return SafetyCollector.collect(level,town,key.vertical(),housing,metadata(key,0,server.overworld().getGameTime()),key.safety());
    }
    public static ComfortSnapshot debugComfort(MinecraftServer server,Settlement town) {
        if(!server.isSameThread())throw new IllegalStateException("Comfort scanning requires the server thread");
        var level=server.getLevel(town.dimension());var key=key(town,level!=null);
        SettlementStats stats;
        try { stats=SettlementScanner.scan(level,town,key.vertical()); }
        catch(RuntimeException exception) { stats=SettlementStats.unavailable(); }
        HousingScanner.Observation housing;
        try { housing=HousingScanner.observe(level,town,stats,key.vertical()); }
        catch(RuntimeException exception) { housing=new HousingScanner.Observation(HousingSnapshot.unavailable(stats.population(),stats.beds()),Set.of()); }
        var cache=new dev.conner.hometown.observation.BlockObservationCache(level,
            SettlementQueries.bounds(town.bellPosition(),town.radius(),key.vertical()),key.safety().blockLimit());
        return ComfortCollector.collect(housing,metadata(key,0,server.overworld().getGameTime()),key.comfort(),key.comfortRules(),cache);
    }
    private static ObservationMetadata metadata(Key key,long generation,long time) {
        var t=key.town();
        return new ObservationMetadata(t.id(),t.dimension().location().toString(),generation,time,
            fingerprint(key.food()+"|"+key.safety()+"|"+key.comfort()+"|"+key.cooldown()),fingerprint(key.tags()+"|"+key.comfortRules().fingerprint()),
            fingerprint(t.dimension()+"|"+t.bellPosition()+"|"+t.radius()+"|"+key.vertical()));
    }
    private static Key key(Settlement town,boolean available) {
        var tags=new ArrayList<String>();
        var comfortTags=new ArrayList<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>>();
        for(var category:ComfortCategory.values())comfortTags.add(category.tag);
        comfortTags.add(ComfortCollector.EXCLUDED);
        for(var tag:comfortTags)BuiltInRegistries.BLOCK.getTag(tag).ifPresent(set->set.forEach(h->tags.add(tag.location()+"="+h.unwrapKey().orElseThrow().location())));
        for(var tag:List.of(SafetyCollector.THREATS,SafetyCollector.PROTECTORS,SafetyCollector.THREATS_EXCLUDED,SafetyCollector.PROTECTORS_EXCLUDED))
            BuiltInRegistries.ENTITY_TYPE.getTag(tag).ifPresent(set->set.forEach(h->tags.add(tag.location()+"="+h.unwrapKey().orElseThrow().location())));
        BuiltInRegistries.BLOCK.getTag(FoodScanner.FOOD_STORAGE).ifPresent(set->set.forEach(h->tags.add("storage="+h.unwrapKey().orElseThrow().location())));
        BuiltInRegistries.BLOCK.getTag(dev.conner.hometown.room.LoadedRoomWorld.ROOM_BOUNDARIES).ifPresent(set->set.forEach(h->tags.add("boundary="+h.unwrapKey().orElseThrow().location())));
        BuiltInRegistries.ITEM.getTag(FoodScanner.FOOD_EXCLUDED).ifPresent(set->set.forEach(h->tags.add("excluded="+h.unwrapKey().orElseThrow().location())));
        Collections.sort(tags);
        return new Key(town,SettlementValidator.Rules.current().verticalRadius(),FoodRules.current(),SafetyCollector.Rules.current(),
            ComfortSettings.current(),ComfortRules.current(),HometownServerConfig.REQUEST_COOLDOWN.get(),String.join("\n",tags),available);
    }
    private static long fingerprint(String text) {
        try { return java.nio.ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))).getLong(); }
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static Observation collect(ServerLevel level,Key key,ObservationMetadata metadata) {
        var town=key.town();
        var bell=level==null||!SettlementQueries.loaded(level,town.bellPosition())?TownLedgerSnapshot.BellState.UNAVAILABLE:
            level.getBlockState(town.bellPosition()).is(Blocks.BELL)?TownLedgerSnapshot.BellState.PRESENT:TownLedgerSnapshot.BellState.MISSING;
        SettlementStats stats;
        try { stats=SettlementScanner.scan(level,town,key.vertical()); }
        catch(RuntimeException exception){Hometown.LOGGER.warn("Unable to observe Hometown {}",town.id(),exception);stats=SettlementStats.unavailable();}
        HousingScanner.Observation housing;
        try { housing=HousingScanner.observe(level,town,stats,key.vertical()); }
        catch(RuntimeException exception){Hometown.LOGGER.warn("Unable to observe Housing {}",town.id(),exception);housing=new HousingScanner.Observation(HousingSnapshot.unavailable(stats.population(),stats.beds()),Set.of());}
        FoodSnapshot food;
        try { food=FoodScanner.scan(level,town,stats,key.vertical(),key.food()); }
        catch(RuntimeException exception){Hometown.LOGGER.warn("Unable to observe Food {}",town.id(),exception);food=FoodSnapshot.unavailable(stats.population(),FoodScanReason.INTERNAL_ERROR);}
        var blocks=new dev.conner.hometown.observation.BlockObservationCache(level,
            SettlementQueries.bounds(town.bellPosition(),town.radius(),key.vertical()),key.safety().blockLimit());
        var safety=SafetyCollector.collect(level,town,key.vertical(),housing,metadata,key.safety(),blocks);
        var comfort=ComfortCollector.collect(housing,metadata,key.comfort(),key.comfortRules(),blocks);
        return new Observation(key,stats,bell,housing.snapshot(),food,safety,comfort);
    }
    private static TownLedgerSnapshotPayload error(RequestTownLedgerPayload request,TownLedgerSnapshotPayload.Error error) {
        return new TownLedgerSnapshotPayload(request.requestId(),null,error);
    }
}
