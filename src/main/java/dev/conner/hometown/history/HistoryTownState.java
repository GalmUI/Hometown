package dev.conner.hometown.history;

import dev.conner.hometown.prosperity.ProsperitySnapshot;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Durable per-town History state. Pending confirmation candidates deliberately live elsewhere in memory only. */
public final class HistoryTownState {
    public enum Domain { POPULATION, HOUSING_SHORTAGE, FOOD_SECURITY, PROSPERITY }

    public static final class DomainState {
        private boolean baselineInitialized;
        private String baseline="";
        private long comparisonRevision;
        private boolean fingerprintInitialized;
        private long fingerprint;
        public boolean baselineInitialized(){return baselineInitialized;}
        public String baseline(){return baseline;}
        public long comparisonRevision(){return comparisonRevision;}
        public boolean fingerprintInitialized(){return fingerprintInitialized;}
        public long fingerprint(){return fingerprint;}
        public void setBaseline(String value){baseline=Objects.requireNonNull(value);baselineInitialized=true;}
        public void clearBaseline(){baseline="";baselineInitialized=false;}
        public void setFingerprint(long value){fingerprint=value;fingerprintInitialized=true;comparisonRevision=Math.max(1,comparisonRevision+1);}
        CompoundTag toTag(){var tag=new CompoundTag();tag.putBoolean("BaselineInitialized",baselineInitialized);if(baselineInitialized)tag.putString("Baseline",baseline);tag.putLong("ComparisonRevision",comparisonRevision);tag.putBoolean("FingerprintInitialized",fingerprintInitialized);if(fingerprintInitialized)tag.putLong("Fingerprint",fingerprint);return tag;}
        static DomainState fromTag(CompoundTag tag){var state=new DomainState();state.baselineInitialized=tag.getBoolean("BaselineInitialized");if(state.baselineInitialized)state.baseline=tag.getString("Baseline");state.comparisonRevision=Math.max(0,tag.getLong("ComparisonRevision"));state.fingerprintInitialized=tag.getBoolean("FingerprintInitialized");if(state.fingerprintInitialized)state.fingerprint=tag.getLong("Fingerprint");return state;}
    }

    private final EnumMap<Domain,DomainState> domains=new EnumMap<>(Domain.class);
    private final ArrayList<HistoryEvent> events=new ArrayList<>();
    private long nextSequenceNumber=1;
    private int highestConfirmedProsperityMilestone;

    public HistoryTownState(){for(var domain:Domain.values())domains.put(domain,new DomainState());}
    public DomainState domain(Domain domain){return domains.get(domain);}
    public List<HistoryEvent> events(){return Collections.unmodifiableList(events);}
    public List<HistoryEvent> newestFirst(){return events.stream().sorted(Comparator.comparingLong(HistoryEvent::sequenceNumber).reversed()).toList();}
    public long nextSequenceNumber(){return nextSequenceNumber;}
    public int highestConfirmedProsperityMilestone(){return highestConfirmedProsperityMilestone;}
    public void setHighestConfirmedProsperityMilestone(int rank){if(rank<0||rank>3)throw new IllegalArgumentException("Invalid Prosperity milestone rank");highestConfirmedProsperityMilestone=rank;}

    public HistoryEvent append(UUID townId,HistoryEvent.Type type,long gameTime,long revision,Map<String,HistoryArgument> arguments){
        var event=new HistoryEvent(nextSequenceNumber++,townId,type,gameTime,gameTime,Math.floorDiv(gameTime,24000L),revision,type.translationKey,arguments);
        events.add(event);return event;
    }
    public void replace(HistoryEvent event){
        for(int i=0;i<events.size();i++)if(events.get(i).sequenceNumber()==event.sequenceNumber()){events.set(i,event);return;}
        throw new IllegalArgumentException("Unknown History sequence");
    }
    public Optional<HistoryEvent> highestRetained(HistoryEvent.Type type,long revision){return events.stream().filter(e->e.type()==type&&e.configurationRevision()==revision).max(Comparator.comparingLong(HistoryEvent::sequenceNumber));}
    public boolean enforceCap(int cap){
        if(cap<1)throw new IllegalArgumentException("Invalid History cap");boolean changed=false;
        while(events.size()>cap){
            var victim=events.stream().filter(e->e.type()!=HistoryEvent.Type.TOWN_FOUNDED).min(Comparator.comparingLong(HistoryEvent::sequenceNumber))
                    .orElseGet(()->events.stream().min(Comparator.comparingLong(HistoryEvent::sequenceNumber)).orElseThrow());
            events.remove(victim);changed=true;
        }
        return changed;
    }

    public CompoundTag toTag(){
        var tag=new CompoundTag();tag.putLong("NextSequenceNumber",nextSequenceNumber);tag.putInt("HighestConfirmedProsperityMilestone",highestConfirmedProsperityMilestone);
        var domainTag=new CompoundTag();domains.forEach((domain,state)->domainTag.put(domain.name(),state.toTag()));tag.put("Domains",domainTag);
        var list=new ListTag();events.stream().sorted(Comparator.comparingLong(HistoryEvent::sequenceNumber)).forEach(event->list.add(event.toTag()));tag.put("Events",list);return tag;
    }
    public static HistoryTownState fromTag(CompoundTag tag,UUID townId){
        var state=new HistoryTownState();state.nextSequenceNumber=Math.max(1,tag.getLong("NextSequenceNumber"));state.highestConfirmedProsperityMilestone=Math.clamp(tag.getInt("HighestConfirmedProsperityMilestone"),0,3);
        if(tag.contains("Domains",Tag.TAG_COMPOUND)){
            var domainTag=tag.getCompound("Domains");for(var domain:Domain.values())if(domainTag.contains(domain.name(),Tag.TAG_COMPOUND))state.domains.put(domain,DomainState.fromTag(domainTag.getCompound(domain.name())));
        }
        if(tag.contains("Events",Tag.TAG_LIST)){
            var list=tag.getList("Events",Tag.TAG_COMPOUND);var seen=new HashSet<Long>();
            for(int i=0;i<list.size();i++){var event=HistoryEvent.fromTag(list.getCompound(i));if(!event.settlementId().equals(townId)||!seen.add(event.sequenceNumber()))throw new IllegalArgumentException("Invalid History event identity/sequence");state.events.add(event);}
            state.events.sort(Comparator.comparingLong(HistoryEvent::sequenceNumber));
            long max=state.events.stream().mapToLong(HistoryEvent::sequenceNumber).max().orElse(0);state.nextSequenceNumber=Math.max(state.nextSequenceNumber,max+1);
        }
        return state;
    }

    public static int prosperityRank(ProsperitySnapshot.Band band){return switch(band){case STARTING->0;case DEVELOPING->1;case ESTABLISHED->2;case FLOURISHING->3;};}
}
