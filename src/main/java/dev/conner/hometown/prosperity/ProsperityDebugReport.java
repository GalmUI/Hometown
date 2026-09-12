package dev.conner.hometown.prosperity;

import dev.conner.hometown.food.FoodRules;

/** Stable operator explanation of one explicit Prosperity observation. */
public final class ProsperityDebugReport {
    private ProsperityDebugReport() {}
    public static String format(ProsperitySnapshot snapshot, FoodRules foodRules, boolean populationComplete, int population) {
        var out=new StringBuilder("Prosperity Debug\n");
        out.append("Status: ").append(snapshot.status()).append(" | enabled=").append(snapshot.enabled()).append('\n');
        out.append("Population: ").append(population).append(" | complete=").append(populationComplete).append('\n');
        out.append("Food stocked threshold: ").append(foodRules.stockedDays()).append(" days\n");
        for(var c:snapshot.components()) {
            out.append(c.type()).append(": status=").append(c.status())
                    .append(" | raw=").append(c.sourceRawValue().isPresent()?c.sourceRawValue().getAsDouble():"N/A")
                    .append(" | normalized=").append(c.normalizedValue().isPresent()?c.normalizedValue().getAsDouble():"N/A")
                    .append(" | weight=").append(c.configuredWeight())
                    .append(" | contribution=").append(c.weightedContribution().isPresent()?c.weightedContribution().getAsDouble():"N/A")
                    .append(" | reasons=").append(c.reasons()).append('\n');
        }
        out.append("Missing required inputs: ").append(snapshot.missingRequiredInputs()).append('\n');
        out.append("Total enabled weight: ").append(snapshot.totalEnabledWeight()).append('\n');
        out.append("Weighted total: ").append(snapshot.weightedTotal().isPresent()?snapshot.weightedTotal().getAsDouble():"N/A").append('\n');
        out.append("Development Index raw: ").append(snapshot.developmentIndex().isPresent()?snapshot.developmentIndex().getAsDouble():"N/A");
        if(snapshot.developmentIndex().isPresent())out.append(" | displayed=").append(snapshot.displayedIndex()).append('%');
        out.append(" | band=").append(snapshot.band().map(Enum::name).orElse("N/A")).append('\n');
        var m=snapshot.metadata();
        out.append("Generation: ").append(m.requestGeneration()).append(" | observedGameTime=").append(m.observedGameTime()).append('\n');
        out.append("Revisions: config=").append(m.configurationRevision()).append(" data=").append(m.dataRevision()).append(" bounds=").append(m.boundsRevision());
        return out.toString();
    }
}
