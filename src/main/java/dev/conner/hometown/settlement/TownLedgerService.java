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
import dev.conner.hometown.commerce.*;
import dev.conner.hometown.prosperity.*;
import dev.conner.hometown.history.*;
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
    private record Key(Settlement town,int vertical,FoodRules food,FoodVarietySettings variety,boolean growing,CropRules.Definitions crops,
            SafetyCollector.Rules safety,ComfortSettings comfort,ComfortRules.Definitions comfortRules,boolean commerce,
            ProsperitySettings prosperity,HistorySettings historySettings,HistoryFingerprints historyFingerprints,
            int cooldown,String tags,boolean available) {}
    private record Observation(Key key,SettlementStats stats,TownLedgerSnapshot.BellState bell,HousingSnapshot housing,
            FoodSnapshot food,FoodVarietySnapshot variety,FoodGrowingSnapshot growing,SafetySnapshot safety,ComfortSnapshot comfort,
            CommerceSnapshot commerce,ProsperitySnapshot prosperity,List<HistoryEvent> history,long elapsedNanos) {
        Observation { history=List.copyOf(history);if(elapsedNanos<0)throw new IllegalArgumentException("Negative observation time"); }
        TownLedgerSnapshot page(int page) {
            return TownLedgerSnapshot.of(key.town(),stats,bell,page).withHousing(housing).withFood(food).withSafety(safety).withComfort(comfort);
        }
        Observation withHistory(List<HistoryEvent> events){return new Observation(key,stats,bell,housing,food,variety,growing,safety,comfort,commerce,prosperity,events,elapsedNanos);}
        Observation withElapsed(long nanos){return new Observation(key,stats,bell,housing,food,variety,growing,safety,comfort,commerce,prosperity,history,nanos);}
        LedgerPerformanceProfile performance(){
            var town=key.town();var fd=food.diagnostics();
            return new LedgerPerformanceProfile(town.id(),town.name(),town.dimension().location().toString(),town.bellPosition(),town.radius(),
                    safety.metadata().requestGeneration(),safety.metadata().observedGameTime(),elapsedNanos,stats.population(),housing.enclosedBeds(),
                    food.foodContainers(),fd.storageScanned(),comfort.roomsAttempted(),comfort.assessedRooms(),safety.loadedChunks(),safety.requiredChunks(),
                    safety.entitiesInspected(),safety.entityLimit(),growing.sharedBlockInspections(),growing.sharedBlockLimit(),
                    growing.candidateSections(),growing.paletteInspections(),growing.blockInspections());
        }
    }
    private record Session(Observation observation,long requestedAt,long lastAccess,boolean closed) {}
    private static final class State {
        long generation;
        final Map<UUID,Map<UUID,Session>> players=new HashMap<>();
        final Map<Key,Observation> sameTick=new HashMap<>();
        final HistoryTracker historyTracker=new HistoryTracker();
        long tick=Long.MIN_VALUE;
    }
    public static void release(MinecraftServer server) { STATES.remove(server); }
    public static void release(ServerPlayer player) { var state=STATES.get(player.getServer());if(state!=null)state.players.remove(player.getUUID()); }
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
        var saved=HometownSavedData.get(server);
        var town=link==null?null:saved.getSettlement(link.settlementId()).orElse(null);
        if(town==null){release(player);return error(request,TownLedgerSnapshotPayload.Error.UNKNOWN);}
        var level=server.getLevel(town.dimension());long now=server.overworld().getGameTime();var key=key(town,level!=null);
        var state=STATES.computeIfAbsent(server,s->new State());if(state.tick!=now){state.sameTick.clear();state.tick=now;}
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
                    ||last.observation().safety().metadata().requestGeneration()!=request.generation())return error(request,TownLedgerSnapshotPayload.Error.WAIT);
            sessions.put(town.id(),new Session(last.observation(),last.requestedAt(),now,false));
            return new TownLedgerSnapshotPayload(request.requestId(),last.observation().page(request.page()),TownLedgerSnapshotPayload.Error.NONE);
        }
        Observation observation=null;long requestedAt=now;
        if(last!=null&&last.observation().key().equals(key)&&now>=last.requestedAt()&&now-last.requestedAt()<key.cooldown()){
            observation=last.observation();requestedAt=last.requestedAt();
        }
        if(observation==null)observation=state.sameTick.get(key);
        if(last==null&&sessions.size()>=MAX_CACHED_TOWNS)return error(request,TownLedgerSnapshotPayload.Error.WAIT);
        if(observation==null){
            if(state.sameTick.size()>=MAX_CACHED_TOWNS)return error(request,TownLedgerSnapshotPayload.Error.WAIT);
            var metadata=metadata(key,++state.generation,now);long startedNanos=System.nanoTime();
            observation=collect(level,key,metadata);
            state.historyTracker.advance(saved,town,metadata,observation.stats(),observation.housing(),observation.food(),observation.prosperity(),
                    key.historyFingerprints(),key.historySettings());
            observation=observation.withHistory(saved.history(town.id()).newestFirst());
            observation=observation.withElapsed(Math.max(0L,System.nanoTime()-startedNanos));
            state.sameTick.put(key,observation);
        }
        sessions.put(town.id(),new Session(observation,requestedAt,now,false));
        return new TownLedgerSnapshotPayload(request.requestId(),observation.page(request.page()),TownLedgerSnapshotPayload.Error.NONE);
    }
    /** Returns only the already-cached Food companion for the exact Ledger generation. Never scans. */
    public static Optional<FoodM3SnapshotPayload> foodM3(ServerPlayer player,UUID settlementId,long generation,int requestId) {
        var observation=cached(player,settlementId,generation).orElse(null);if(observation==null)return Optional.empty();
        return Optional.of(new FoodM3SnapshotPayload(requestId,observation.variety(),observation.growing()));
    }
    /** Returns only the already-cached Commerce companion for the exact Ledger generation. Never scans. */
    public static Optional<CommerceSnapshotPayload> commerce(ServerPlayer player,UUID settlementId,long generation,int requestId) {
        return cached(player,settlementId,generation).map(observation->new CommerceSnapshotPayload(requestId,observation.commerce()));
    }
    /** Returns only the already-cached Prosperity companion for the exact Ledger generation. Never scans. */
    public static Optional<ProsperitySnapshotPayload> prosperity(ServerPlayer player,UUID settlementId,long generation,int requestId) {
        return cached(player,settlementId,generation).map(observation->new ProsperitySnapshotPayload(requestId,observation.prosperity()));
    }
    /** Immutable newest-first History captured by the originating Ledger generation. */
    public static Optional<List<HistoryEvent>> history(ServerPlayer player,UUID settlementId,long generation){
        return cached(player,settlementId,generation).map(Observation::history);
    }
    /** Last normal Ledger observation for this player's town session. Read-only and never scans. */
    public static Optional<LedgerPerformanceProfile> lastPerformanceProfile(ServerPlayer player,UUID settlementId){
        var state=STATES.get(player.getServer());if(state==null)return Optional.empty();
        var sessions=state.players.get(player.getUUID());if(sessions==null)return Optional.empty();
        var session=sessions.get(settlementId);if(session==null)return Optional.empty();
        long now=player.getServer().overworld().getGameTime();
        if(now<session.lastAccess()||now-session.lastAccess()>SESSION_EXPIRY)return Optional.empty();
        return Optional.of(session.observation().performance());
    }
    private static Optional<Observation> cached(ServerPlayer player,UUID settlementId,long generation){
        var state=STATES.get(player.getServer());if(state==null)return Optional.empty();
        var sessions=state.players.get(player.getUUID());if(sessions==null)return Optional.empty();
        var session=sessions.get(settlementId);if(session==null||session.closed())return Optional.empty();
        var observation=session.observation();return observation.safety().metadata().requestGeneration()==generation?Optional.of(observation):Optional.empty();
    }
    /** Explicit operator observation; never modifies Ledger candidate/session/History state. */
    public static SafetySnapshot debugSafety(MinecraftServer server,Settlement town) {
        if(!server.isSameThread())throw new IllegalStateException("Safety scanning requires the server thread");
        var level=server.getLevel(town.dimension());var key=key(town,level!=null);SettlementStats stats;
        try { stats=SettlementScanner.scan(level,town,key.vertical()); }catch(RuntimeException exception) { stats=SettlementStats.unavailable(); }
        HousingScanner.Observation housing;
        try { housing=HousingScanner.observe(level,town,stats,key.vertical()); }catch(RuntimeException exception) { housing=new HousingScanner.Observation(HousingSnapshot.unavailable(stats.population(),stats.beds()),Set.of()); }
        return SafetyCollector.collect(level,town,key.vertical(),housing,metadata(key,0,server.overworld().getGameTime()),key.safety());
    }
    public static ComfortSnapshot debugComfort(MinecraftServer server,Settlement town) {
        if(!server.isSameThread())throw new IllegalStateException("Comfort scanning requires the server thread");
        var level=server.getLevel(town.dimension());var key=key(town,level!=null);SettlementStats stats;
        try { stats=SettlementScanner.scan(level,town,key.vertical()); }catch(RuntimeException exception) { stats=SettlementStats.unavailable(); }
        HousingScanner.Observation housing;
        try { housing=HousingScanner.observe(level,town,stats,key.vertical()); }catch(RuntimeException exception) { housing=new HousingScanner.Observation(HousingSnapshot.unavailable(stats.population(),stats.beds()),Set.of()); }
        var cache=new dev.conner.hometown.observation.BlockObservationCache(level,SettlementQueries.bounds(town.bellPosition(),town.radius(),key.vertical()),key.safety().blockLimit());
        return ComfortCollector.collect(housing,metadata(key,0,server.overworld().getGameTime()),key.comfort(),key.comfortRules(),cache);
    }
    public static FoodVarietySnapshot debugFoodVariety(MinecraftServer server,Settlement town) {
        if(!server.isSameThread())throw new IllegalStateException("Food scanning requires the server thread");
        var level=server.getLevel(town.dimension());var key=key(town,level!=null);SettlementStats stats;
        try { stats=SettlementScanner.scan(level,town,key.vertical()); }catch(RuntimeException exception){stats=SettlementStats.unavailable();}
        FoodObservation food;
        try { food=FoodScanner.observe(level,town,stats,key.vertical(),key.food()); }catch(RuntimeException exception){food=new FoodObservation(FoodSnapshot.unavailable(stats.population(),FoodScanReason.INTERNAL_ERROR),List.of());}
        return FoodVarietyEvaluator.evaluate(metadata(key,0,server.overworld().getGameTime()),food.reserves(),food.stackFacts(),key.variety());
    }
    public static FoodGrowingSnapshot debugFoodGrowing(MinecraftServer server,Settlement town) {
        if(!server.isSameThread())throw new IllegalStateException("Growing scan requires the server thread");
        var level=server.getLevel(town.dimension());var key=key(town,level!=null);var meta=metadata(key,0,server.overworld().getGameTime());
        var cache=new dev.conner.hometown.observation.BlockObservationCache(level,SettlementQueries.bounds(town.bellPosition(),town.radius(),key.vertical()),key.safety().blockLimit());
        return FoodGrowingCollector.collect(level,town,key.vertical(),meta,key.growing(),key.crops(),cache);
    }
    public static CommerceSnapshot debugCommerce(MinecraftServer server,Settlement town) {
        if(!server.isSameThread())throw new IllegalStateException("Commerce scanning requires the server thread");
        var level=server.getLevel(town.dimension());var key=key(town,level!=null);SettlementObservation residents;
        try { residents=SettlementScanner.observe(level,town,key.vertical()); }
        catch(RuntimeException exception){residents=new SettlementObservation(SettlementStats.unavailable(),List.of(),0,0);}
        return CommerceEvaluator.evaluate(metadata(key,0,server.overworld().getGameTime()),residents,key.commerce());
    }
    public static ProsperitySnapshot debugProsperity(MinecraftServer server,Settlement town) {
        if(!server.isSameThread())throw new IllegalStateException("Prosperity observation requires the server thread");
        var level=server.getLevel(town.dimension());var key=key(town,level!=null);
        return collect(level,key,metadata(key,0,server.overworld().getGameTime())).prosperity();
    }
    public static List<HistoryEvent> debugHistory(MinecraftServer server,Settlement town){
        if(!server.isSameThread())throw new IllegalStateException("History debug requires the server thread");
        return HometownSavedData.get(server).getHistory(town.id()).map(HistoryTownState::newestFirst).orElse(List.of());
    }
    private static ObservationMetadata metadata(Key key,long generation,long time) {
        var t=key.town();
        return new ObservationMetadata(t.id(),t.dimension().location().toString(),generation,time,
            fingerprint(key.food()+"|"+key.variety()+"|growing="+key.growing()+"|"+key.safety()+"|"+key.comfort()+"|commerce="+key.commerce()+"|prosperity="+key.prosperity()+"|history="+key.historySettings()+"|"+key.cooldown()),
            fingerprint(key.tags()+"|"+key.comfortRules().fingerprint()+"|"+key.crops().fingerprint()),
            fingerprint(t.dimension()+"|"+t.bellPosition()+"|"+t.radius()+"|"+key.vertical()));
    }
    private static Key key(Settlement town,boolean available) {
        var allTags=new ArrayList<String>();var comfortFacts=new ArrayList<String>();var boundaryFacts=new ArrayList<String>();var foodFacts=new ArrayList<String>();
        var comfortTags=new ArrayList<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>>();
        for(var category:ComfortCategory.values())comfortTags.add(category.tag);comfortTags.add(ComfortCollector.EXCLUDED);
        for(var tag:comfortTags)BuiltInRegistries.BLOCK.getTag(tag).ifPresent(set->set.forEach(h->{String fact=tag.location()+"="+h.unwrapKey().orElseThrow().location();allTags.add(fact);comfortFacts.add(fact);}));
        for(var tag:List.of(SafetyCollector.THREATS,SafetyCollector.PROTECTORS,SafetyCollector.THREATS_EXCLUDED,SafetyCollector.PROTECTORS_EXCLUDED))
            BuiltInRegistries.ENTITY_TYPE.getTag(tag).ifPresent(set->set.forEach(h->allTags.add(tag.location()+"="+h.unwrapKey().orElseThrow().location())));
        BuiltInRegistries.BLOCK.getTag(FoodScanner.FOOD_STORAGE).ifPresent(set->set.forEach(h->{String fact="storage="+h.unwrapKey().orElseThrow().location();allTags.add(fact);foodFacts.add(fact);}));
        BuiltInRegistries.BLOCK.getTag(dev.conner.hometown.room.LoadedRoomWorld.ROOM_BOUNDARIES).ifPresent(set->set.forEach(h->{String fact="boundary="+h.unwrapKey().orElseThrow().location();allTags.add(fact);boundaryFacts.add(fact);}));
        BuiltInRegistries.ITEM.getTag(FoodScanner.FOOD_EXCLUDED).ifPresent(set->set.forEach(h->{String fact="excluded="+h.unwrapKey().orElseThrow().location();allTags.add(fact);foodFacts.add(fact);}));
        for(var group:FoodGroup.values())BuiltInRegistries.ITEM.getTag(group.tag).ifPresent(set->set.forEach(h->allTags.add(group.id+"="+h.unwrapKey().orElseThrow().location())));
        Collections.sort(allTags);Collections.sort(comfortFacts);Collections.sort(boundaryFacts);Collections.sort(foodFacts);
        int vertical=SettlementValidator.Rules.current().verticalRadius();var food=FoodRules.current();var safety=SafetyCollector.Rules.current();
        var comfort=ComfortSettings.current();var comfortRules=ComfortRules.current();boolean commerce=HometownServerConfig.COMMERCE_ENABLED.get();var prosperity=ProsperitySettings.current();
        String geometry=town.dimension()+"|"+town.bellPosition()+"|"+town.radius()+"|"+vertical;
        long populationFp=fingerprint("history-population-v1|"+geometry+"|vanilla-villager-alive-loaded");
        long housingFp=fingerprint("history-housing-v1|population="+populationFp+"|boundaries="+String.join(";",boundaryFacts));
        long foodFp=fingerprint("history-food-v1|population="+populationFp+"|rules="+food+"|facts="+String.join(";",foodFacts));
        long prosperityFp=fingerprint("history-prosperity-v1|population="+populationFp+"|housing="+housingFp+"|food="+foodFp
                +"|safetyEnabled="+safety.enabled()+"|minimumLight="+safety.minimumBlockLight()+"|comfort="+comfort
                +"|comfortRules="+comfortRules.fingerprint()+"|comfortFacts="+String.join(";",comfortFacts)
                +"|commerceEnabled="+commerce+"|prosperity="+prosperity);
        var fingerprints=new HistoryFingerprints(populationFp,housingFp,foodFp,prosperityFp);
        return new Key(town,vertical,food,FoodVarietySettings.current(),HometownServerConfig.FOOD_GROWING_ENABLED.get(),CropRules.current(),safety,comfort,comfortRules,
            commerce,prosperity,HistorySettings.current(),fingerprints,HometownServerConfig.REQUEST_COOLDOWN.get(),String.join("\n",allTags),available);
    }
    private static long fingerprint(String text) {
        try { return java.nio.ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))).getLong(); }
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static Observation collect(ServerLevel level,Key key,ObservationMetadata metadata) {
        var town=key.town();
        var bell=level==null||!SettlementQueries.loaded(level,town.bellPosition())?TownLedgerSnapshot.BellState.UNAVAILABLE:
            level.getBlockState(town.bellPosition()).is(Blocks.BELL)?TownLedgerSnapshot.BellState.PRESENT:TownLedgerSnapshot.BellState.MISSING;
        SettlementObservation residents;
        try { residents=SettlementScanner.observe(level,town,key.vertical()); }
        catch(RuntimeException exception){Hometown.LOGGER.warn("Unable to observe Hometown {}",town.id(),exception);residents=new SettlementObservation(SettlementStats.unavailable(),List.of(),0,0);}
        var stats=residents.stats();
        HousingScanner.Observation housing;
        try { housing=HousingScanner.observe(level,town,stats,key.vertical()); }catch(RuntimeException exception){Hometown.LOGGER.warn("Unable to observe Housing {}",town.id(),exception);housing=new HousingScanner.Observation(HousingSnapshot.unavailable(stats.population(),stats.beds()),Set.of());}
        FoodObservation food;
        try { food=FoodScanner.observe(level,town,stats,key.vertical(),key.food()); }
        catch(RuntimeException exception){Hometown.LOGGER.warn("Unable to observe Food {}",town.id(),exception);food=new FoodObservation(FoodSnapshot.unavailable(stats.population(),FoodScanReason.INTERNAL_ERROR),List.of());}
        var variety=FoodVarietyEvaluator.evaluate(metadata,food.reserves(),food.stackFacts(),key.variety());
        var blocks=new dev.conner.hometown.observation.BlockObservationCache(level,SettlementQueries.bounds(town.bellPosition(),town.radius(),key.vertical()),key.safety().blockLimit());
        var safety=SafetyCollector.collect(level,town,key.vertical(),housing,metadata,key.safety(),blocks);
        var comfort=ComfortCollector.collect(housing,metadata,key.comfort(),key.comfortRules(),blocks);
        var growing=FoodGrowingCollector.collect(level,town,key.vertical(),metadata,key.growing(),key.crops(),blocks);
        var commerce=CommerceEvaluator.evaluate(metadata,residents,key.commerce());
        var prosperity=ProsperityEvaluator.evaluate(metadata,stats,housing.snapshot(),food.reserves(),key.food(),safety,comfort,commerce,key.prosperity());
        return new Observation(key,stats,bell,housing.snapshot(),food.reserves(),variety,growing,safety,comfort,commerce,prosperity,List.of(),0L);
    }
    private static TownLedgerSnapshotPayload error(RequestTownLedgerPayload request,TownLedgerSnapshotPayload.Error error) {
        return new TownLedgerSnapshotPayload(request.requestId(),null,error);
    }
}
