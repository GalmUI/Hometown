package dev.conner.hometown.food;

import dev.conner.hometown.observation.ObservationMetadata;
import dev.conner.hometown.settlement.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Explicit operator collection. Each command performs one bounded observation and never touches Ledger session state. */
public final class FoodM3DebugService {
    private FoodM3DebugService() {}
    private static ObservationMetadata metadata(MinecraftServer server,Settlement town) {
        return new ObservationMetadata(town.id(),town.dimension().location().toString(),0,server.overworld().getGameTime(),0,0,0);
    }
    public static FoodVarietyDebugReport.Data variety(MinecraftServer server,Settlement town) {
        int vertical=SettlementValidator.Rules.current().verticalRadius();var level=server.getLevel(town.dimension());SettlementStats stats;
        try{stats=SettlementScanner.scan(level,town,vertical);}catch(RuntimeException error){stats=SettlementStats.unavailable();}
        FoodObservation observation;
        try{observation=FoodScanner.observe(level,town,stats,vertical,FoodRules.current());}
        catch(RuntimeException error){observation=new FoodObservation(FoodSnapshot.unavailable(stats.population(),FoodScanReason.INTERNAL_ERROR),List.of());}
        var settings=FoodVarietySettings.current();var snapshot=FoodVarietyEvaluator.evaluate(metadata(server,town),observation.reserves(),observation.stackFacts(),settings);
        return FoodVarietyDebugReport.data(snapshot,settings,observation.stackFacts());
    }
}
