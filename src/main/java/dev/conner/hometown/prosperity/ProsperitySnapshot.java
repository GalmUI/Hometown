package dev.conner.hometown.prosperity;

import dev.conner.hometown.observation.ObservationMetadata;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;

/** Pure, disposable Development Index result. Current Prosperity is never persisted as truth. */
public record ProsperitySnapshot(ObservationMetadata metadata, boolean enabled, Status status,
        List<Component> components, int totalEnabledWeight, OptionalDouble weightedTotal,
        OptionalDouble developmentIndex, Optional<Band> band, List<ComponentType> missingRequiredInputs) {
    public enum Status { COMPLETE, NO_RESIDENTS, INCOMPLETE, DISABLED }
    public enum Band { STARTING, DEVELOPING, ESTABLISHED, FLOURISHING }
    public enum ComponentType { HOUSING_SUPPLY, FOOD_RESERVES, RESIDENTIAL_LIGHTING, RESIDENTIAL_COMFORT, EMPLOYMENT }
    public enum ComponentStatus { COMPLETE, EXCLUDED, PARTIAL, UNAVAILABLE, NOT_APPLICABLE, MODULE_DISABLED, STALE }
    public enum Reason { SOURCE_PARTIAL, SOURCE_UNAVAILABLE, NOT_APPLICABLE, MODULE_DISABLED, REVISION_MISMATCH, INVALID_SOURCE_VALUE }

    public record Component(ComponentType type, ComponentStatus status, OptionalDouble sourceRawValue,
            OptionalDouble normalizedValue, int configuredWeight, OptionalDouble weightedContribution,
            Set<Reason> reasons) {
        public Component {
            Objects.requireNonNull(type); Objects.requireNonNull(status); Objects.requireNonNull(sourceRawValue);
            Objects.requireNonNull(normalizedValue); Objects.requireNonNull(weightedContribution); reasons = Set.copyOf(reasons);
            if (configuredWeight < 0 || configuredWeight > 100 || invalid(sourceRawValue) || invalid(normalizedValue)
                    || invalid(weightedContribution)) throw new IllegalArgumentException("Invalid Prosperity component");
            if (normalizedValue.isPresent() && (normalizedValue.getAsDouble() < 0 || normalizedValue.getAsDouble() > 100))
                throw new IllegalArgumentException("Invalid normalized Prosperity value");
            if (status == ComponentStatus.EXCLUDED) {
                if (configuredWeight != 0 || normalizedValue.isPresent() || sourceRawValue.isPresent()
                        || weightedContribution.isEmpty() || weightedContribution.getAsDouble() != 0)
                    throw new IllegalArgumentException("Invalid excluded Prosperity component");
            } else if (configuredWeight == 0) throw new IllegalArgumentException("Zero-weight component must be excluded");
            if (status == ComponentStatus.COMPLETE) {
                if (sourceRawValue.isEmpty() || normalizedValue.isEmpty() || weightedContribution.isEmpty() || !reasons.isEmpty())
                    throw new IllegalArgumentException("Incomplete complete Prosperity component");
            } else if (status != ComponentStatus.EXCLUDED && weightedContribution.isPresent())
                throw new IllegalArgumentException("Missing component cannot contribute");
        }
        private static boolean invalid(OptionalDouble value) { return value.isPresent() && !Double.isFinite(value.getAsDouble()); }
    }

    public ProsperitySnapshot {
        Objects.requireNonNull(metadata); Objects.requireNonNull(status); Objects.requireNonNull(weightedTotal);
        Objects.requireNonNull(developmentIndex); Objects.requireNonNull(band);
        components = List.copyOf(components); missingRequiredInputs = List.copyOf(missingRequiredInputs);
        if (components.size() != ComponentType.values().length) throw new IllegalArgumentException("Prosperity requires exactly five components");
        for (int i = 0; i < ComponentType.values().length; i++)
            if (components.get(i).type() != ComponentType.values()[i]) throw new IllegalArgumentException("Prosperity component order mismatch");
        if (totalEnabledWeight <= 0 || totalEnabledWeight > 500) throw new IllegalArgumentException("Invalid Prosperity total weight");
        if (weightedTotal.isPresent() && !Double.isFinite(weightedTotal.getAsDouble())) throw new IllegalArgumentException("Invalid Prosperity weighted total");
        if (developmentIndex.isPresent() && (!Double.isFinite(developmentIndex.getAsDouble()) || developmentIndex.getAsDouble() < 0 || developmentIndex.getAsDouble() > 100))
            throw new IllegalArgumentException("Invalid Development Index");
        boolean authoritative = status == Status.COMPLETE;
        if (authoritative != developmentIndex.isPresent() || authoritative != weightedTotal.isPresent() || authoritative != band.isPresent())
            throw new IllegalArgumentException("Prosperity authority mismatch");
        if (authoritative && !missingRequiredInputs.isEmpty()) throw new IllegalArgumentException("Complete Prosperity has missing inputs");
        if (!authoritative && status != Status.DISABLED && status != Status.NO_RESIDENTS && missingRequiredInputs.isEmpty())
            throw new IllegalArgumentException("Incomplete Prosperity must name missing inputs");
    }

    public Optional<Component> component(ComponentType type) { return components.stream().filter(c -> c.type() == type).findFirst(); }
    public int displayedIndex() { return developmentIndex.isEmpty() ? -1 : (int)Math.floor(developmentIndex.getAsDouble() + 0.5d); }

    public void write(FriendlyByteBuf b) {
        metadata.write(b); b.writeBoolean(enabled); b.writeEnum(status); b.writeVarInt(totalEnabledWeight);
        optional(b, weightedTotal); optional(b, developmentIndex); b.writeBoolean(band.isPresent()); band.ifPresent(b::writeEnum);
        b.writeVarInt(components.size());
        for (var c : components) {
            b.writeEnum(c.type()); b.writeEnum(c.status()); optional(b,c.sourceRawValue()); optional(b,c.normalizedValue());
            b.writeVarInt(c.configuredWeight()); optional(b,c.weightedContribution()); b.writeVarInt(c.reasons().size());
            c.reasons().stream().sorted().forEach(b::writeEnum);
        }
        b.writeVarInt(missingRequiredInputs.size()); missingRequiredInputs.forEach(b::writeEnum);
    }

    public static ProsperitySnapshot read(FriendlyByteBuf b) {
        var metadata=ObservationMetadata.read(b); boolean enabled=b.readBoolean(); var status=b.readEnum(Status.class); int weight=b.readVarInt();
        var total=optional(b); var index=optional(b); Optional<Band> band=b.readBoolean()?Optional.of(b.readEnum(Band.class)):Optional.empty();
        int count=size(b,ComponentType.values().length); var components=new ArrayList<Component>();
        for(int i=0;i<count;i++) {
            var type=b.readEnum(ComponentType.class);var componentStatus=b.readEnum(ComponentStatus.class);var raw=optional(b);var normalized=optional(b);
            int configured=b.readVarInt();var contribution=optional(b);int reasonCount=size(b,Reason.values().length);var reasons=EnumSet.noneOf(Reason.class);
            for(int j=0;j<reasonCount;j++)if(!reasons.add(b.readEnum(Reason.class)))throw new IllegalArgumentException("Duplicate Prosperity reason");
            components.add(new Component(type,componentStatus,raw,normalized,configured,contribution,reasons));
        }
        int missingCount=size(b,ComponentType.values().length);var missing=new ArrayList<ComponentType>();
        for(int i=0;i<missingCount;i++){var type=b.readEnum(ComponentType.class);if(missing.contains(type))throw new IllegalArgumentException("Duplicate missing Prosperity input");missing.add(type);}
        return new ProsperitySnapshot(metadata,enabled,status,components,weight,total,index,band,missing);
    }
    private static void optional(FriendlyByteBuf b,OptionalDouble value){b.writeBoolean(value.isPresent());if(value.isPresent())b.writeDouble(value.getAsDouble());}
    private static OptionalDouble optional(FriendlyByteBuf b){if(!b.readBoolean())return OptionalDouble.empty();double value=b.readDouble();if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid Prosperity number");return OptionalDouble.of(value);}
    private static int size(FriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("Oversized Prosperity data");return n;}
}
