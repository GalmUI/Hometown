package dev.conner.hometown.food;

public final class FoodGrowingDebugReport {
    private FoodGrowingDebugReport() {}
    public static String format(FoodGrowingSnapshot snapshot,CropRules.Definitions definitions) {
        var out=new StringBuilder("Food Growing Debug\n");
        out.append("Status: ").append(snapshot.scanStatus()).append(" | enabled=").append(snapshot.enabled()).append('\n');
        out.append("Scope chunks: ").append(snapshot.loadedChunks()).append('/').append(snapshot.chunksConsidered()).append('\n');
        out.append("Work: palette=").append(snapshot.paletteInspections()).append(" candidateSections=").append(snapshot.candidateSections())
                .append(" positions=").append(snapshot.blockInspections()).append(" shared=").append(snapshot.sharedBlockInspections()).append('/').append(snapshot.sharedBlockLimit()).append('\n');
        out.append("Crops: ").append(snapshot.growingCropBlocks()).append(" | known mature=").append(snapshot.knownMatureCropBlocks())
                .append(" | maturity unassessed=").append(snapshot.maturityUnassessedCropBlocks()).append(" | families=").append(snapshot.cropFamilyCount()).append('\n');
        out.append("Reasons: ").append(snapshot.reasonCounts()).append('\n');
        out.append("Definitions:\n");
        definitions.rules().values().stream().sorted(java.util.Comparator.comparing(rule->rule.block().toString())).forEach(rule->
                out.append("  ").append(rule.block()).append(" => ").append(rule.family()).append(" anchor=").append(rule.anchorProperties())
                        .append(" mature=").append(rule.matureProperties().map(Object::toString).orElse("N/A")).append('\n'));
        out.append("Families:\n");
        snapshot.families().forEach(family->out.append("  ").append(family.familyId()).append(" growing=").append(family.growing())
                .append(" mature=").append(family.mature().isPresent()?family.mature().getAsInt():"N/A").append(" maturity=").append(family.maturityStatus()).append('\n'));
        return out.toString().stripTrailing();
    }
}
