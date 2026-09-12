package dev.conner.hometown.history;

import dev.conner.hometown.food.FoodSnapshot;
import dev.conner.hometown.housing.HousingSnapshot;
import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.prosperity.ProsperitySnapshot;
import dev.conner.hometown.settlement.*;
import java.util.*;

/**
 * Confirms derived History only from fresh normal Ledger generations.
 * Durable baselines/events live in HometownSavedData; candidates and generation dedup live only here.
 */
public final class HistoryTracker {
    public record CandidateView(HistoryTownState.Domain domain,String value,long firstObservedGameTime,long firstGeneration,long earliestConfirmationTime) {}
    private record Candidate(String value,long firstObservedGameTime,long firstGeneration,Object latestPayload) {}
    private static final class TownMemory {
        long lastGeneration=Long.MIN_VALUE;
        final EnumMap<HistoryTownState.Domain,Candidate> candidates=new EnumMap<>(HistoryTownState.Domain.class);
    }
    private record HousingPayload(int population,int enclosedBeds) {}
    private record ProsperityPayload(String townName,ProsperitySnapshot.Band band,double index) {}
    private final Map<UUID,TownMemory> memory=new HashMap<>();

    public void clear(){memory.clear();}
    public void clearTown(UUID id){memory.remove(id);}

    /** Returns true only when this generation was eligible for History evaluation (whether or not it dirtied data). */
    public boolean advance(HometownSavedData data,Settlement town,ObservationMetadata metadata,SettlementStats stats,
            HousingSnapshot housing,FoodSnapshot food,ProsperitySnapshot prosperity,HistoryFingerprints fingerprints,
            HistorySettings settings) {
        Objects.requireNonNull(data);Objects.requireNonNull(town);Objects.requireNonNull(metadata);Objects.requireNonNull(stats);
        Objects.requireNonNull(housing);Objects.requireNonNull(food);Objects.requireNonNull(prosperity);Objects.requireNonNull(fingerprints);Objects.requireNonNull(settings);
        var state=data.history(town.id());var mem=memory.computeIfAbsent(town.id(),ignored->new TownMemory());
        long generation=metadata.requestGeneration(),time=metadata.observedGameTime();
        if(generation<=mem.lastGeneration)return false;
        mem.lastGeneration=generation;
        boolean dirty=state.enforceCap(settings.maxEventsPerTown());
        if(!settings.enabled()){
            mem.candidates.clear();
            for(var domain:HistoryTownState.Domain.values())if(state.domain(domain).baselineInitialized()){state.domain(domain).clearBaseline();dirty=true;}
            if(state.highestConfirmedProsperityMilestone()!=0){state.setHighestConfirmedProsperityMilestone(0);dirty=true;}
            if(dirty)data.setDirty();return true;
        }

        boolean populationEligible=stats.availability()==SettlementStats.Availability.COMPLETE;
        dirty|=processPopulation(data,state,mem,town,time,generation,fingerprints.population(),settings,populationEligible,stats.population());

        boolean housingEligible=populationEligible&&housing.scanComplete();
        dirty|=processHousing(data,state,mem,town,time,generation,fingerprints.housingShortage(),settings,housingEligible,
                new HousingPayload(stats.population(),housing.enclosedBeds()));

        var foodState=food.state().orElse(null);
        boolean foodEligible=populationEligible&&stats.population()>0&&food.scanComplete()&&foodState!=null&&foodState!=FoodSnapshot.FoodSecurityState.NO_RESIDENTS;
        dirty|=processFood(data,state,mem,town,time,generation,fingerprints.foodSecurity(),settings,foodEligible,foodState);

        boolean prosperityEligible=populationEligible&&stats.population()>0&&prosperity.status()==ProsperitySnapshot.Status.COMPLETE
                &&prosperity.band().isPresent()&&prosperity.developmentIndex().isPresent();
        ProsperityPayload prosperityPayload=prosperityEligible?new ProsperityPayload(town.name(),prosperity.band().orElseThrow(),prosperity.developmentIndex().orElseThrow()):null;
        dirty|=processProsperity(data,state,mem,town,time,generation,fingerprints.prosperity(),settings,prosperityEligible,prosperityPayload);
        if(state.enforceCap(settings.maxEventsPerTown()))dirty=true;
        if(dirty)data.setDirty();
        return true;
    }

    public List<CandidateView> candidates(UUID townId,HistorySettings settings){
        var mem=memory.get(townId);if(mem==null)return List.of();var out=new ArrayList<CandidateView>();
        mem.candidates.forEach((domain,c)->out.add(new CandidateView(domain,c.value(),c.firstObservedGameTime(),c.firstGeneration(),c.firstObservedGameTime()+settings.confirmationTicks())));
        out.sort(Comparator.comparing(v->v.domain().ordinal()));return List.copyOf(out);
    }

    private boolean processPopulation(HometownSavedData data,HistoryTownState state,TownMemory mem,Settlement town,long time,long generation,
            long fingerprint,HistorySettings settings,boolean eligible,int value){
        var domain=HistoryTownState.Domain.POPULATION;var durable=state.domain(domain);String encoded=Integer.toString(value);
        if(prepareRevision(state,mem,domain,durable,fingerprint,eligible,encoded,null)){return true;}
        if(!eligible){mem.candidates.remove(domain);return false;}
        if(!durable.baselineInitialized()){durable.setBaseline(encoded);return true;}
        if(durable.baseline().equals(encoded)){mem.candidates.remove(domain);return false;}
        var candidate=mem.candidates.get(domain);
        if(restart(candidate,encoded,time)){mem.candidates.put(domain,new Candidate(encoded,time,generation,value));return false;}
        if(generation==candidate.firstGeneration()||time-candidate.firstObservedGameTime()<settings.confirmationTicks()){
            mem.candidates.put(domain,new Candidate(encoded,candidate.firstObservedGameTime(),candidate.firstGeneration(),value));return false;
        }
        int previous=Integer.parseInt(durable.baseline());durable.setBaseline(encoded);mem.candidates.remove(domain);
        populationEvent(state,town.id(),previous,value,time,durable.comparisonRevision(),settings);return true;
    }

    private boolean processHousing(HometownSavedData data,HistoryTownState state,TownMemory mem,Settlement town,long time,long generation,
            long fingerprint,HistorySettings settings,boolean eligible,HousingPayload payload){
        var domain=HistoryTownState.Domain.HOUSING_SHORTAGE;var durable=state.domain(domain);String encoded=eligible?Boolean.toString(payload.population()>payload.enclosedBeds()):"";
        if(prepareRevision(state,mem,domain,durable,fingerprint,eligible,encoded,null))return true;
        if(!eligible){mem.candidates.remove(domain);return false;}
        if(!durable.baselineInitialized()){durable.setBaseline(encoded);return true;}
        if(durable.baseline().equals(encoded)){mem.candidates.remove(domain);return false;}
        var candidate=mem.candidates.get(domain);
        if(restart(candidate,encoded,time)){mem.candidates.put(domain,new Candidate(encoded,time,generation,payload));return false;}
        if(generation==candidate.firstGeneration()||time-candidate.firstObservedGameTime()<settings.confirmationTicks()){
            mem.candidates.put(domain,new Candidate(encoded,candidate.firstObservedGameTime(),candidate.firstGeneration(),payload));return false;
        }
        boolean shortage=Boolean.parseBoolean(encoded);durable.setBaseline(encoded);mem.candidates.remove(domain);
        var type=shortage?HistoryEvent.Type.HOUSING_SHORTAGE_STARTED:HistoryEvent.Type.HOUSING_SHORTAGE_RESOLVED;
        var args=new LinkedHashMap<String,HistoryArgument>();args.put("observedPopulation",HistoryArgument.intValue(payload.population()));args.put("observedEnclosedBeds",HistoryArgument.intValue(payload.enclosedBeds()));
        state.append(town.id(),type,time,durable.comparisonRevision(),args);return true;
    }

    private boolean processFood(HometownSavedData data,HistoryTownState state,TownMemory mem,Settlement town,long time,long generation,
            long fingerprint,HistorySettings settings,boolean eligible,FoodSnapshot.FoodSecurityState value){
        var domain=HistoryTownState.Domain.FOOD_SECURITY;var durable=state.domain(domain);String encoded=eligible?value.name():"";
        if(prepareRevision(state,mem,domain,durable,fingerprint,eligible,encoded,null))return true;
        if(!eligible){mem.candidates.remove(domain);return false;}
        if(!durable.baselineInitialized()){durable.setBaseline(encoded);return true;}
        if(durable.baseline().equals(encoded)){mem.candidates.remove(domain);return false;}
        var candidate=mem.candidates.get(domain);
        if(restart(candidate,encoded,time)){mem.candidates.put(domain,new Candidate(encoded,time,generation,value));return false;}
        if(generation==candidate.firstGeneration()||time-candidate.firstObservedGameTime()<settings.confirmationTicks()){
            mem.candidates.put(domain,new Candidate(encoded,candidate.firstObservedGameTime(),candidate.firstGeneration(),value));return false;
        }
        String previous=durable.baseline();durable.setBaseline(encoded);mem.candidates.remove(domain);
        foodEvent(state,town.id(),previous,encoded,time,durable.comparisonRevision(),settings);return true;
    }

    private boolean processProsperity(HometownSavedData data,HistoryTownState state,TownMemory mem,Settlement town,long time,long generation,
            long fingerprint,HistorySettings settings,boolean eligible,ProsperityPayload payload){
        var domain=HistoryTownState.Domain.PROSPERITY;var durable=state.domain(domain);String encoded=eligible?payload.band().name():"";
        if(prepareRevision(state,mem,domain,durable,fingerprint,eligible,encoded,payload)){
            if(eligible)state.setHighestConfirmedProsperityMilestone(HistoryTownState.prosperityRank(payload.band()));
            return true;
        }
        if(!eligible){mem.candidates.remove(domain);return false;}
        if(!durable.baselineInitialized()){durable.setBaseline(encoded);state.setHighestConfirmedProsperityMilestone(HistoryTownState.prosperityRank(payload.band()));return true;}
        if(durable.baseline().equals(encoded)){mem.candidates.remove(domain);return false;}
        var candidate=mem.candidates.get(domain);
        if(restart(candidate,encoded,time)){mem.candidates.put(domain,new Candidate(encoded,time,generation,payload));return false;}
        if(generation==candidate.firstGeneration()||time-candidate.firstObservedGameTime()<settings.confirmationTicks()){
            mem.candidates.put(domain,new Candidate(encoded,candidate.firstObservedGameTime(),candidate.firstGeneration(),payload));return false;
        }
        durable.setBaseline(encoded);mem.candidates.remove(domain);int rank=HistoryTownState.prosperityRank(payload.band());
        if(rank>state.highestConfirmedProsperityMilestone()){
            if(rank>0){var type=switch(payload.band()){case DEVELOPING->HistoryEvent.Type.PROSPERITY_DEVELOPING_REACHED;case ESTABLISHED->HistoryEvent.Type.PROSPERITY_ESTABLISHED_REACHED;case FLOURISHING->HistoryEvent.Type.PROSPERITY_FLOURISHING_REACHED;case STARTING->throw new IllegalStateException();};
                var args=new LinkedHashMap<String,HistoryArgument>();args.put("townName",HistoryArgument.string(payload.townName()));args.put("developmentIndexAtObservation",HistoryArgument.doubleValue(payload.index()));
                state.append(town.id(),type,time,durable.comparisonRevision(),args);}
            state.setHighestConfirmedProsperityMilestone(rank);
        }
        return true;
    }

    /** Returns true when durable revision/baseline state changed and this observation is consumed as a silent baseline. */
    private static boolean prepareRevision(HistoryTownState state,TownMemory mem,HistoryTownState.Domain domain,HistoryTownState.DomainState durable,
            long fingerprint,boolean eligible,String encoded,Object payload){
        boolean changed=!durable.fingerprintInitialized()||durable.fingerprint()!=fingerprint;
        if(!changed)return false;
        durable.setFingerprint(fingerprint);durable.clearBaseline();mem.candidates.remove(domain);
        if(domain==HistoryTownState.Domain.PROSPERITY)state.setHighestConfirmedProsperityMilestone(0);
        if(eligible){durable.setBaseline(encoded);if(domain==HistoryTownState.Domain.PROSPERITY&&payload instanceof ProsperityPayload p)state.setHighestConfirmedProsperityMilestone(HistoryTownState.prosperityRank(p.band()));}
        return true;
    }
    private static boolean restart(Candidate candidate,String encoded,long time){return candidate==null||!candidate.value().equals(encoded)||time<candidate.firstObservedGameTime();}

    private static void populationEvent(HistoryTownState state,UUID townId,int previous,int current,long time,long revision,HistorySettings settings){
        var args=new LinkedHashMap<String,HistoryArgument>();args.put("previousPopulation",HistoryArgument.intValue(previous));args.put("newPopulation",HistoryArgument.intValue(current));
        if(settings.coalesceTicks()>0){var prior=state.highestRetained(HistoryEvent.Type.POPULATION_CHANGED,revision).orElse(null);if(prior!=null){long elapsed=time-prior.createdGameTime();var priorCurrent=prior.arguments().get("newPopulation");if(elapsed>=0&&elapsed<=settings.coalesceTicks()&&priorCurrent!=null&&priorCurrent.type()==HistoryArgument.Type.INT&&priorCurrent.asInt()==previous){var merged=new LinkedHashMap<>(prior.arguments());merged.put("newPopulation",HistoryArgument.intValue(current));state.replace(prior.withObserved(time,revision,merged));return;}}}
        state.append(townId,HistoryEvent.Type.POPULATION_CHANGED,time,revision,args);
    }
    private static void foodEvent(HistoryTownState state,UUID townId,String previous,String current,long time,long revision,HistorySettings settings){
        var args=new LinkedHashMap<String,HistoryArgument>();args.put("previousSecurityState",new HistoryArgument(HistoryArgument.Type.ENUM,previous));args.put("newSecurityState",new HistoryArgument(HistoryArgument.Type.ENUM,current));
        if(settings.coalesceTicks()>0){var prior=state.highestRetained(HistoryEvent.Type.FOOD_SECURITY_CHANGED,revision).orElse(null);if(prior!=null){long elapsed=time-prior.createdGameTime();var priorCurrent=prior.arguments().get("newSecurityState");if(elapsed>=0&&elapsed<=settings.coalesceTicks()&&priorCurrent!=null&&priorCurrent.value().equals(previous)){var merged=new LinkedHashMap<>(prior.arguments());merged.put("newSecurityState",new HistoryArgument(HistoryArgument.Type.ENUM,current));state.replace(prior.withObserved(time,revision,merged));return;}}}
        state.append(townId,HistoryEvent.Type.FOOD_SECURITY_CHANGED,time,revision,args);
    }
}
