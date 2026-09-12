package dev.conner.hometown.prosperity;

import dev.conner.hometown.config.HometownServerConfig;
import java.util.EnumMap;
import java.util.Map;

/** Effective Prosperity configuration. Validation is centralized here so scoring never sees an invalid rule set. */
public record ProsperitySettings(boolean enabled, Map<ProsperitySnapshot.ComponentType,Integer> weights,
        double developing, double established, double flourishing) {
    public ProsperitySettings {
        var normalized = new EnumMap<ProsperitySnapshot.ComponentType,Integer>(ProsperitySnapshot.ComponentType.class);
        int total = 0;
        for (var type : ProsperitySnapshot.ComponentType.values()) {
            Integer value = weights.get(type);
            if (value == null || value < 0 || value > 100) throw new IllegalArgumentException("Invalid Prosperity weight for " + type);
            normalized.put(type, value);
            total += value;
        }
        if (total == 0) throw new IllegalArgumentException("Prosperity weights may not all be zero");
        if (!Double.isFinite(developing) || !Double.isFinite(established) || !Double.isFinite(flourishing)
                || !(0 < developing && developing < established && established < flourishing && flourishing <= 100))
            throw new IllegalArgumentException("Invalid Prosperity band thresholds");
        weights = Map.copyOf(normalized);
    }

    public int weight(ProsperitySnapshot.ComponentType type) { return weights.get(type); }
    public int totalEnabledWeight() { return weights.values().stream().mapToInt(Integer::intValue).sum(); }

    public ProsperitySnapshot.Band band(double index) {
        if (!Double.isFinite(index) || index < 0 || index > 100) throw new IllegalArgumentException("Invalid Development Index");
        return index < developing ? ProsperitySnapshot.Band.STARTING
                : index < established ? ProsperitySnapshot.Band.DEVELOPING
                : index < flourishing ? ProsperitySnapshot.Band.ESTABLISHED
                : ProsperitySnapshot.Band.FLOURISHING;
    }

    public static ProsperitySettings defaults() {
        var weights = new EnumMap<ProsperitySnapshot.ComponentType,Integer>(ProsperitySnapshot.ComponentType.class);
        for (var type : ProsperitySnapshot.ComponentType.values()) weights.put(type, 20);
        return new ProsperitySettings(true, weights, 25, 50, 75);
    }

    public static ProsperitySettings current() {
        var weights = new EnumMap<ProsperitySnapshot.ComponentType,Integer>(ProsperitySnapshot.ComponentType.class);
        weights.put(ProsperitySnapshot.ComponentType.HOUSING_SUPPLY, HometownServerConfig.PROSPERITY_HOUSING_WEIGHT.get());
        weights.put(ProsperitySnapshot.ComponentType.FOOD_RESERVES, HometownServerConfig.PROSPERITY_FOOD_WEIGHT.get());
        weights.put(ProsperitySnapshot.ComponentType.RESIDENTIAL_LIGHTING, HometownServerConfig.PROSPERITY_LIGHTING_WEIGHT.get());
        weights.put(ProsperitySnapshot.ComponentType.RESIDENTIAL_COMFORT, HometownServerConfig.PROSPERITY_COMFORT_WEIGHT.get());
        weights.put(ProsperitySnapshot.ComponentType.EMPLOYMENT, HometownServerConfig.PROSPERITY_EMPLOYMENT_WEIGHT.get());
        return new ProsperitySettings(HometownServerConfig.PROSPERITY_ENABLED.get(), weights,
                HometownServerConfig.PROSPERITY_DEVELOPING.get(), HometownServerConfig.PROSPERITY_ESTABLISHED.get(),
                HometownServerConfig.PROSPERITY_FLOURISHING.get());
    }
}
