package dev.conner.hometown.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.conner.hometown.history.HistoryDebugReport;
import dev.conner.hometown.prosperity.ProsperityDebugReport;
import dev.conner.hometown.settlement.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** M5 operator surfaces kept separate from legacy debug registration. */
public final class M5DebugCommands {
    private static final SimpleCommandExceptionType UNKNOWN=new SimpleCommandExceptionType(Component.literal("Stand inside a Hometown, or provide its UUID."));
    private M5DebugCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hometown").requires(source->source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("prosperity").executes(M5DebugCommands::prosperity)
                                .then(Commands.argument("uuid",UuidArgument.uuid()).executes(M5DebugCommands::prosperity)))
                        .then(Commands.literal("history").executes(M5DebugCommands::history)
                                .then(Commands.argument("uuid",UuidArgument.uuid()).executes(M5DebugCommands::history)
                                        .then(Commands.argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,4096)).executes(M5DebugCommands::history))))));
    }

    private static int prosperity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var town=currentTown(context);var snapshot=TownLedgerService.debugProsperity(context.getSource().getServer(),town);
        String output=ProsperityDebugReport.format(snapshot);context.getSource().sendSuccess(()->Component.literal(output),false);
        return snapshot.status()==dev.conner.hometown.prosperity.ProsperitySnapshot.Status.COMPLETE?1:0;
    }

    private static int history(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var town=currentTown(context);int page=context.getNodes().stream().anyMatch(n->n.getNode().getName().equals("page"))
                ?com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"page"):0;
        var state=HometownSavedData.get(context.getSource().getServer()).getHistory(town.id()).orElseGet(dev.conner.hometown.history.HistoryTownState::new);
        String output=HistoryDebugReport.format(state,page);context.getSource().sendSuccess(()->Component.literal(output),false);return 1;
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
