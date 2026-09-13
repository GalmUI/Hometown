package dev.conner.hometown.history;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Durable structural observation. Render from translationKey + typed arguments; never persist rendered English as sole meaning. */
public record HistoryEvent(long sequenceNumber, UUID settlementId, Type type, long createdGameTime,
        long observedGameTime, long observedDay, long configurationRevision, String translationKey,
        Map<String,HistoryArgument> arguments) {
    public enum Type {
        TOWN_FOUNDED("history.hometown.town_founded"),
        TOWN_HALL_ESTABLISHED("history.hometown.town_hall_established"),
        POPULATION_CHANGED("history.hometown.population_changed"),
        HOUSING_SHORTAGE_STARTED("history.hometown.housing_shortage_started"),
        HOUSING_SHORTAGE_RESOLVED("history.hometown.housing_shortage_resolved"),
        FOOD_SECURITY_CHANGED("history.hometown.food_security_changed"),
        PROSPERITY_DEVELOPING_REACHED("history.hometown.prosperity_developing_reached"),
        PROSPERITY_ESTABLISHED_REACHED("history.hometown.prosperity_established_reached"),
        PROSPERITY_FLOURISHING_REACHED("history.hometown.prosperity_flourishing_reached");
        public final String translationKey;
        Type(String key){translationKey=key;}
    }

    public HistoryEvent {
        if(sequenceNumber<0||createdGameTime<0||observedGameTime<0||observedDay<0)throw new IllegalArgumentException("Invalid History timing/sequence");
        Objects.requireNonNull(settlementId);Objects.requireNonNull(type);Objects.requireNonNull(translationKey);Objects.requireNonNull(arguments);
        if(translationKey.isBlank()||translationKey.length()>256)throw new IllegalArgumentException("Invalid History translation key");
        var normalized=new LinkedHashMap<String,HistoryArgument>();
        arguments.forEach((key,value)->{if(key==null||key.isBlank()||key.length()>64||value==null)throw new IllegalArgumentException("Invalid History argument");normalized.put(key,value);});
        arguments=Collections.unmodifiableMap(normalized);
    }

    public HistoryEvent withObserved(long gameTime,long revision,Map<String,HistoryArgument> newArguments){
        return new HistoryEvent(sequenceNumber,settlementId,type,createdGameTime,gameTime,Math.floorDiv(gameTime,24000L),revision,translationKey,newArguments);
    }

    public CompoundTag toTag(){
        var tag=new CompoundTag();tag.putLong("Sequence",sequenceNumber);tag.putUUID("SettlementId",settlementId);tag.putString("Type",type.name());
        tag.putLong("CreatedGameTime",createdGameTime);tag.putLong("ObservedGameTime",observedGameTime);tag.putLong("ObservedDay",observedDay);
        tag.putLong("ConfigurationRevision",configurationRevision);tag.putString("TranslationKey",translationKey);
        var args=new ListTag();arguments.forEach((name,value)->{var a=new CompoundTag();a.putString("Name",name);a.put("Argument",value.toTag());args.add(a);});
        tag.put("Arguments",args);return tag;
    }

    public static HistoryEvent fromTag(CompoundTag tag){
        var args=new LinkedHashMap<String,HistoryArgument>();
        if(tag.contains("Arguments",Tag.TAG_LIST)){
            var list=tag.getList("Arguments",Tag.TAG_COMPOUND);
            for(int i=0;i<list.size();i++){var a=list.getCompound(i);String name=a.getString("Name");if(args.put(name,HistoryArgument.fromTag(a.getCompound("Argument")))!=null)throw new IllegalArgumentException("Duplicate History argument");}
        }
        return new HistoryEvent(tag.getLong("Sequence"),tag.getUUID("SettlementId"),Type.valueOf(tag.getString("Type")),
                tag.getLong("CreatedGameTime"),tag.getLong("ObservedGameTime"),tag.getLong("ObservedDay"),tag.getLong("ConfigurationRevision"),
                tag.getString("TranslationKey"),args);
    }
}
