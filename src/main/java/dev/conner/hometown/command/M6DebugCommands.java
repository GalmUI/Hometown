package dev.conner.hometown.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.conner.hometown.settlement.*;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** M6 integration diagnostics. Performance output reads a cached normal Ledger observation and never scans. */
public final class M6DebugCommands {
    private static final SimpleCommandExceptionType UNKNOWN=new SimpleCommandExceptionType(Component.literal("Stand inside a Hometown, or provide its UUID."));
    private static final SimpleCommandExceptionType NO_PROFILE=new SimpleCommandExceptionType(Component.literal("No recent normal Ledger observation is cached. Open this town's Ledger, close it, then run the command within 60 seconds."));
    private M6DebugCommands() {}

    public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("hometown").requires(source->source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("performance").executes(M6DebugCommands::performance)
                                .then(Commands.argument("uuid",UuidArgument.uuid()).executes(M6DebugCommands::performance)))));
    }

    private static int performance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player=context.getSource().getPlayerOrException();var town=currentTown(context);
        var profile=TownLedgerService.lastPerformanceProfile(player,town.id()).orElseThrow(NO_PROFILE::create);
        long now=context.getSource().getServer().overworld().getGameTime();
        context.getSource().sendSuccess(()->Component.literal(format(profile,now)),false);return 1;
    }

    static String format(LedgerPerformanceProfile p,long now){
        long age=now>=p.observedGameTime()?now-p.observedGameTime():0;
        var b=p.bellPosition();
        return "Hometown Ledger performance (cached normal observation; this command ran no scan)
"
                +"Town: "+p.townName()+" ["+p.settlementId()+"]
"
                +"Dimension/Bell/Radius: "+p.dimension()+" "+b.getX()+","+b.getY()+","+b.getZ()+" r="+p.radius()+"
"
                +"Generation: "+p.generation()+" | observedGameTime="+p.observedGameTime()+" | ageTicks="+age+"
"
                +"Fresh observation elapsed: "+String.format(Locale.ROOT,"%.3f",p.elapsedMillis())+" ms
"
                +"Population/Beds: "+p.population()+" / "+p.enclosedBeds()+"
"
                +"Loaded town chunks: "+p.loadedChunks()+" / "+p.requiredChunks()+"
"
                +"Food containers/scanned storage: "+p.foodContainers()+" / "+p.foodStorageScanned()+"
"
                +"Comfort rooms: assessed="+p.assessedRooms()+" attempted="+p.roomsAttempted()+"
"
                +"Entity work: "+p.entitiesInspected()+" / "+p.entityLimit()+"
"
                +"Shared new-block work: "+p.sharedBlockInspections()+" / "+p.sharedBlockLimit()+"
"
                +"Growing work: sections="+p.growingCandidateSections()+" palette="+p.growingPaletteInspections()+" positions="+p.growingBlockInspections();
    }

    private static Settlement currentTown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var data=HometownSavedData.get(context.getSource().getServer());
        if(context.getNodes().stream().anyMatch(node->node.getNode().getName().equals("uuid")))
            return data.getSettlement(UuidArgument.getUuid(context,"uuid")).orElseThrow(UNKNOWN::create);
        int vertical=SettlementValidator.Rules.current().verticalRadius();var player=context.getSource().getPlayerOrException();
        return data.all().stream().filter(t->t.dimension().equals(player.level().dimension())
                        &&SettlementQueries.bounds(t.bellPosition(),t.radius(),vertical).contains(player.position()))
                .min(java.util.Comparator.comparingDouble(t->t.bellPosition().distSqr(player.blockPosition()))).orElseThrow(UNKNOWN::create);
    }
}
