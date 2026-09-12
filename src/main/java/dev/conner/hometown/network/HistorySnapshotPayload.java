package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.history.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One bounded page from the immutable History view captured by a Ledger generation. */
public record HistorySnapshotPayload(int requestId,UUID settlementId,long generation,int page,int totalPages,
        boolean available,List<HistoryEvent> events) implements CustomPacketPayload {
    public static final int PAGE_SIZE=8;
    public static final Type<HistorySnapshotPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID,"ledger_history"));
    public static final StreamCodec<FriendlyByteBuf,HistorySnapshotPayload> STREAM_CODEC=StreamCodec.of(HistorySnapshotPayload::write,HistorySnapshotPayload::read);
    public HistorySnapshotPayload {
        Objects.requireNonNull(settlementId);events=List.copyOf(events);
        if(page<0||totalPages<1||page>=totalPages||events.size()>PAGE_SIZE)throw new IllegalArgumentException("Invalid History page payload");
        if(!available&&!events.isEmpty())throw new IllegalArgumentException("Unavailable History cannot carry events");
    }
    public static HistorySnapshotPayload page(int requestId,UUID settlementId,long generation,int requestedPage,List<HistoryEvent> all){
        int pages=Math.max(1,(all.size()+PAGE_SIZE-1)/PAGE_SIZE),page=Math.clamp(requestedPage,0,pages-1),start=page*PAGE_SIZE,end=Math.min(start+PAGE_SIZE,all.size());
        return new HistorySnapshotPayload(requestId,settlementId,generation,page,pages,true,all.subList(start,end));
    }
    public static HistorySnapshotPayload unavailable(int requestId,UUID settlementId,long generation,int page){return new HistorySnapshotPayload(requestId,settlementId,generation,0,1,false,List.of());}

    private static void write(FriendlyByteBuf b,HistorySnapshotPayload p){
        b.writeInt(p.requestId());b.writeUUID(p.settlementId());b.writeLong(p.generation());b.writeVarInt(p.page());b.writeVarInt(p.totalPages());b.writeBoolean(p.available());b.writeVarInt(p.events().size());
        p.events().forEach(event->writeEvent(b,event));
    }
    private static HistorySnapshotPayload read(FriendlyByteBuf b){
        int requestId=b.readInt();UUID town=b.readUUID();long generation=b.readLong();int page=b.readVarInt(),pages=b.readVarInt();boolean available=b.readBoolean();int count=b.readVarInt();
        if(count<0||count>PAGE_SIZE)throw new IllegalArgumentException("Oversized History page");var events=new ArrayList<HistoryEvent>(count);for(int i=0;i<count;i++)events.add(readEvent(b));
        return new HistorySnapshotPayload(requestId,town,generation,page,pages,available,events);
    }
    private static void writeEvent(FriendlyByteBuf b,HistoryEvent e){
        b.writeLong(e.sequenceNumber());b.writeUUID(e.settlementId());b.writeEnum(e.type());b.writeLong(e.createdGameTime());b.writeLong(e.observedGameTime());b.writeLong(e.observedDay());b.writeLong(e.configurationRevision());b.writeUtf(e.translationKey(),256);
        b.writeVarInt(e.arguments().size());e.arguments().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{b.writeUtf(entry.getKey(),64);b.writeEnum(entry.getValue().type());b.writeUtf(entry.getValue().value(),512);});
    }
    private static HistoryEvent readEvent(FriendlyByteBuf b){
        long sequence=b.readLong();UUID town=b.readUUID();var type=b.readEnum(HistoryEvent.Type.class);long created=b.readLong(),observed=b.readLong(),day=b.readLong(),revision=b.readLong();String key=b.readUtf(256);int count=b.readVarInt();
        if(count<0||count>32)throw new IllegalArgumentException("Oversized History arguments");var args=new LinkedHashMap<String,HistoryArgument>();
        for(int i=0;i<count;i++){String name=b.readUtf(64);var value=new HistoryArgument(b.readEnum(HistoryArgument.Type.class),b.readUtf(512));if(args.put(name,value)!=null)throw new IllegalArgumentException("Duplicate History argument");}
        return new HistoryEvent(sequence,town,type,created,observed,day,revision,key,args);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
