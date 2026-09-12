package dev.conner.hometown.food;

import dev.conner.hometown.observation.ObservationMetadata;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;

/** Server-derived Food Variety observation. Partial evidence is explicitly non-authoritative. */
public record FoodVarietySnapshot(ObservationMetadata metadata, boolean enabled, int population, boolean populationComplete,
        Map<FoodGroup,Long> perGroupNutrition, Set<FoodGroup> enabledGroups, OptionalLong requiredNutritionPerGroup,
        Map<FoodGroup,Qualification> qualifications, int uniqueFoodCount, long unclassifiedNutrition,
        OptionalDouble observedCoverage, OptionalDouble authoritativeCoverage, VarietyState varietyState,
        int collisionCount, FoodScanStatus sourceStatus) {
    public enum Qualification { TRUE, FALSE, UNKNOWN, DISABLED }
    public enum VarietyState { NONE, LIMITED, VARIED, DIVERSE, NO_RESIDENTS, DISABLED, INCOMPLETE }

    public FoodVarietySnapshot {
        Objects.requireNonNull(metadata); Objects.requireNonNull(requiredNutritionPerGroup);
        Objects.requireNonNull(observedCoverage); Objects.requireNonNull(authoritativeCoverage);
        Objects.requireNonNull(varietyState); Objects.requireNonNull(sourceStatus);
        var nutrition = new EnumMap<FoodGroup,Long>(FoodGroup.class);
        var qualification = new EnumMap<FoodGroup,Qualification>(FoodGroup.class);
        for (var group : FoodGroup.values()) {
            nutrition.put(group, perGroupNutrition.getOrDefault(group, 0L));
            qualification.put(group, qualifications.getOrDefault(group, Qualification.UNKNOWN));
        }
        perGroupNutrition = Map.copyOf(nutrition);
        qualifications = Map.copyOf(qualification);
        enabledGroups = enabledGroups.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(enabledGroups));
        if (population < 0 || uniqueFoodCount < 0 || unclassifiedNutrition < 0 || collisionCount < 0
                || perGroupNutrition.values().stream().anyMatch(value -> value < 0)
                || (requiredNutritionPerGroup.isPresent() && requiredNutritionPerGroup.getAsLong() < 1)
                || invalidPercent(observedCoverage) || invalidPercent(authoritativeCoverage))
            throw new IllegalArgumentException("Invalid Food Variety snapshot");
        boolean authoritative = authoritativeCoverage.isPresent();
        if (authoritative && (sourceStatus != FoodScanStatus.COMPLETE || !populationComplete || population == 0 || !enabled || enabledGroups.isEmpty()))
            throw new IllegalArgumentException("Unauthoritative Food Variety coverage");
        if ((varietyState == VarietyState.NONE || varietyState == VarietyState.LIMITED || varietyState == VarietyState.VARIED || varietyState == VarietyState.DIVERSE) != authoritative)
            throw new IllegalArgumentException("Food Variety band authority mismatch");
    }

    private static boolean invalidPercent(OptionalDouble value) {
        return value.isPresent() && (!Double.isFinite(value.getAsDouble()) || value.getAsDouble() < 0 || value.getAsDouble() > 100);
    }

    public int qualifyingEnabledGroups() {
        int count = 0;
        for (var group : enabledGroups) if (qualifications.get(group) == Qualification.TRUE) count++;
        return count;
    }

    public static FoodVarietySnapshot unavailable(ObservationMetadata metadata) {
        var nutrition = new EnumMap<FoodGroup,Long>(FoodGroup.class);
        var qualification = new EnumMap<FoodGroup,Qualification>(FoodGroup.class);
        for (var group : FoodGroup.values()) { nutrition.put(group,0L); qualification.put(group,Qualification.UNKNOWN); }
        return new FoodVarietySnapshot(metadata,true,0,false,nutrition,EnumSet.allOf(FoodGroup.class),OptionalLong.empty(),qualification,
                0,0,OptionalDouble.empty(),OptionalDouble.empty(),VarietyState.INCOMPLETE,0,FoodScanStatus.UNAVAILABLE);
    }

    public void write(FriendlyByteBuf buffer) {
        metadata.write(buffer); buffer.writeBoolean(enabled); buffer.writeVarInt(population); buffer.writeBoolean(populationComplete);
        for (var group : FoodGroup.values()) buffer.writeVarLong(perGroupNutrition.get(group));
        buffer.writeVarInt(enabledGroups.size()); for (var group : FoodGroup.values()) if (enabledGroups.contains(group)) buffer.writeEnum(group);
        buffer.writeBoolean(requiredNutritionPerGroup.isPresent()); if (requiredNutritionPerGroup.isPresent()) buffer.writeVarLong(requiredNutritionPerGroup.getAsLong());
        for (var group : FoodGroup.values()) buffer.writeEnum(qualifications.get(group));
        buffer.writeVarInt(uniqueFoodCount); buffer.writeVarLong(unclassifiedNutrition);
        writePercent(buffer,observedCoverage); writePercent(buffer,authoritativeCoverage);
        buffer.writeEnum(varietyState); buffer.writeVarInt(collisionCount); buffer.writeEnum(sourceStatus);
    }

    public static FoodVarietySnapshot read(FriendlyByteBuf buffer) {
        var metadata = ObservationMetadata.read(buffer); boolean enabled = buffer.readBoolean(); int population = buffer.readVarInt(); boolean populationComplete = buffer.readBoolean();
        var nutrition = new EnumMap<FoodGroup,Long>(FoodGroup.class); for (var group : FoodGroup.values()) nutrition.put(group,buffer.readVarLong());
        int enabledCount = buffer.readVarInt(); if (enabledCount < 0 || enabledCount > FoodGroup.values().length) throw new IllegalArgumentException("Invalid Food Variety group count");
        var enabledGroups = EnumSet.noneOf(FoodGroup.class); for (int i=0;i<enabledCount;i++) if(!enabledGroups.add(buffer.readEnum(FoodGroup.class))) throw new IllegalArgumentException("Duplicate Food Variety group");
        var required = buffer.readBoolean()?OptionalLong.of(buffer.readVarLong()):OptionalLong.empty();
        var qualification = new EnumMap<FoodGroup,Qualification>(FoodGroup.class); for (var group : FoodGroup.values()) qualification.put(group,buffer.readEnum(Qualification.class));
        int unique = buffer.readVarInt(); long unclassified = buffer.readVarLong();
        var observed = readPercent(buffer); var authoritative = readPercent(buffer);
        return new FoodVarietySnapshot(metadata,enabled,population,populationComplete,nutrition,enabledGroups,required,qualification,unique,unclassified,
                observed,authoritative,buffer.readEnum(VarietyState.class),buffer.readVarInt(),buffer.readEnum(FoodScanStatus.class));
    }

    private static void writePercent(FriendlyByteBuf buffer,OptionalDouble value) { buffer.writeBoolean(value.isPresent()); if(value.isPresent())buffer.writeDouble(value.getAsDouble()); }
    private static OptionalDouble readPercent(FriendlyByteBuf buffer) { if(!buffer.readBoolean())return OptionalDouble.empty(); double value=buffer.readDouble(); if(!Double.isFinite(value)||value<0||value>100)throw new IllegalArgumentException("Invalid Food Variety percent"); return OptionalDouble.of(value); }
}
