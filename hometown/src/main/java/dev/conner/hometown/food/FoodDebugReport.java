package dev.conner.hometown.food;

import java.util.Locale;
import java.util.TreeSet;

/** Operator-only presentation shared with tests; no extra scanning. */
public final class FoodDebugReport {
    private FoodDebugReport() {}
    public static String format(String town, FoodSnapshot f) {
        var d=f.diagnostics();
        String days=f.reserveDays().isPresent()?String.format(Locale.ROOT,"%.2f",f.scanComplete()?f.reserveDays().getAsDouble():Math.floor(f.reserveDays().getAsDouble()*100)/100):"N/A";
        return "Food Debug - " + town + "\nScan Status: " + f.scanStatus() + "\nReason: " + d.primaryReason()
                + "\nAll reasons: " + (d.reasons().isEmpty()?"NONE":new TreeSet<>(d.reasons()))
                + "\nSettlement chunks considered: " + d.settlementChunks() + "\nLoaded chunks: " + d.loadedChunks()
                + "\nUnavailable chunks: " + d.unavailableChunks() + "\nBlock entities inspected: " + d.blockEntitiesInspected()
                + " / " + d.limits().blockEntities() + "\nFood storage containers found: " + d.storageFound()
                + " (limit " + d.limits().storageContainers() + ")\nFood storage containers successfully scanned: " + d.storageScanned()
                + "\nDuplicate inventories skipped: " + d.duplicateInventoriesSkipped() + "\nUnavailable storage: " + d.storageUnavailable()
                + "\nUngenerated loot containers skipped: " + d.lootContainersSkipped() + "\nInventory slots inspected: " + d.slotsInspected()
                + " / " + d.limits().inventorySlots() + "\nScan limit reached: " + (d.scanLimitHit()?"Yes":"No")
                + "\nPopulation: " + f.population() + (d.populationComplete()?"":" (known residents only; population incomplete)")
                + "\nFood containers with known food: " + f.foodContainers() + "\nFood stacks: " + f.foodStacks()
                + "\nFood types: " + f.uniqueFoodTypes() + "\nKnown Total Nutrition: " + f.totalNutrition()
                + "\nDaily Requirement: " + f.dailyNutritionRequirement() + "\n" + (f.scanComplete()?"Reserve Days: ":"Known Reserve Days: ") + days
                + (!f.scanComplete() && f.reserveDays().isPresent()?" (lower bound with complete population)":"")
                + "\nFood Security: " + f.state().map(Enum::name).orElse("Not determined from incomplete data");
    }
}
