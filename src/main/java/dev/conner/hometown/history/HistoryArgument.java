package dev.conner.hometown.history;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** A persisted typed History argument. Values use a canonical string representation plus an explicit type. */
public record HistoryArgument(Type type, String value) {
    public enum Type { STRING, UUID, LONG, INT, DOUBLE, BOOLEAN, BLOCK_POS, RESOURCE_LOCATION, ENUM }

    public HistoryArgument {
        Objects.requireNonNull(type); Objects.requireNonNull(value);
        if (value.length() > 512) throw new IllegalArgumentException("History argument too long");
        validate(type,value);
    }
    public static HistoryArgument string(String value){return new HistoryArgument(Type.STRING,value);}
    public static HistoryArgument uuid(UUID value){return new HistoryArgument(Type.UUID,value.toString());}
    public static HistoryArgument longValue(long value){return new HistoryArgument(Type.LONG,Long.toString(value));}
    public static HistoryArgument intValue(int value){return new HistoryArgument(Type.INT,Integer.toString(value));}
    public static HistoryArgument doubleValue(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite History value");return new HistoryArgument(Type.DOUBLE,Double.toString(value));}
    public static HistoryArgument bool(boolean value){return new HistoryArgument(Type.BOOLEAN,Boolean.toString(value));}
    public static HistoryArgument blockPos(BlockPos value){return new HistoryArgument(Type.BLOCK_POS,value.getX()+","+value.getY()+","+value.getZ());}
    public static HistoryArgument resource(String value){return new HistoryArgument(Type.RESOURCE_LOCATION,value);}
    public static HistoryArgument enumValue(Enum<?> value){return new HistoryArgument(Type.ENUM,value.name());}

    public int asInt(){require(Type.INT);return Integer.parseInt(value);}
    public long asLong(){require(Type.LONG);return Long.parseLong(value);}
    public double asDouble(){require(Type.DOUBLE);return Double.parseDouble(value);}
    public boolean asBoolean(){require(Type.BOOLEAN);return Boolean.parseBoolean(value);}
    public UUID asUuid(){require(Type.UUID);return UUID.fromString(value);}
    public BlockPos asBlockPos(){require(Type.BLOCK_POS);var p=value.split(",",-1);return new BlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));}
    private void require(Type expected){if(type!=expected)throw new IllegalStateException("History argument is "+type+", not "+expected);}

    CompoundTag toTag(){var tag=new CompoundTag();tag.putString("Type",type.name());tag.putString("Value",value);return tag;}
    static HistoryArgument fromTag(CompoundTag tag){return new HistoryArgument(Type.valueOf(tag.getString("Type")),tag.getString("Value"));}

    private static void validate(Type type,String value){
        try {
            switch(type){
                case UUID->UUID.fromString(value);
                case LONG->Long.parseLong(value);
                case INT->Integer.parseInt(value);
                case DOUBLE->{double v=Double.parseDouble(value);if(!Double.isFinite(v))throw new IllegalArgumentException("Non-finite History value");}
                case BOOLEAN->{if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Invalid History boolean");}
                case BLOCK_POS->{var p=value.split(",",-1);if(p.length!=3)throw new IllegalArgumentException("Invalid History position");for(var s:p)Integer.parseInt(s);}
                case RESOURCE_LOCATION->{if(value.isBlank()||value.length()>256)throw new IllegalArgumentException("Invalid History registry id");}
                case ENUM->{if(value.isBlank())throw new IllegalArgumentException("Invalid History enum");}
                case STRING->{}
            }
        } catch(RuntimeException exception){if(exception instanceof IllegalArgumentException iae)throw iae;throw new IllegalArgumentException("Invalid History argument",exception);}
    }
}
