package dev.conner.hometown.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.conner.hometown.item.TownLedgerItem;
import dev.conner.hometown.settlement.HometownSavedData;
import dev.conner.hometown.settlement.Settlement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class HometownDebugCommands {
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal("Unknown hometown UUID."));
    private HometownDebugCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hometown").requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("room").executes(RoomDebugCommand::execute))
                        .then(Commands.literal("comfort").executes(HometownDebugCommands::comfort)
                            .then(Commands.argument("uuid",UuidArgument.uuid()).executes(HometownDebugCommands::comfort)
                                .then(Commands.argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,4096)).executes(HometownDebugCommands::comfort))))
                        .then(Commands.literal("safety").executes(HometownDebugCommands::safety)
                            .then(Commands.argument("uuid", UuidArgument.uuid()).executes(HometownDebugCommands::safety)
                                .then(Commands.argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,65536)).executes(HometownDebugCommands::safety))))
                        .then(Commands.literal("food").executes(HometownDebugCommands::food)
                            .then(Commands.argument("uuid", UuidArgument.uuid()).executes(HometownDebugCommands::food))
                            .then(Commands.literal("variety").executes(HometownDebugCommands::foodVariety)
                                .then(Commands.argument("uuid",UuidArgument.uuid()).executes(HometownDebugCommands::foodVariety)))
                            .then(Commands.literal("growing").executes(HometownDebugCommands::foodGrowing)
                                .then(Commands.argument("uuid",UuidArgument.uuid()).executes(HometownDebugCommands::foodGrowing))))
                        .then(Commands.literal("housing").then(Commands.argument("uuid", UuidArgument.uuid()).executes(HometownDebugCommands::housing)))
                        .then(Commands.literal("list").executes(HometownDebugCommands::list))
                        .then(Commands.literal("inspect").then(Commands.argument("uuid", UuidArgument.uuid()).executes(context -> {
                            Settlement town = resolve(context);
                            context.getSource().sendSuccess(() -> Component.literal(town.toTag().toString()), false);
                            return 1;
                        })))
                        .then(Commands.literal("remove").then(Commands.argument("uuid", UuidArgument.uuid()).executes(context -> {
                            Settlement town = resolve(context);
                            data(context).removeSettlement(town.id());
                            context.getSource().sendSuccess(() -> Component.literal("Removed Hometown '" + town.name() + "' [" + town.id() + "]."), true);
                            return 1;
                        })))
                        .then(Commands.literal("ledger").then(Commands.argument("uuid", UuidArgument.uuid()).executes(context -> {
                            Settlement town = resolve(context);
                            var player = context.getSource().getPlayerOrException();
                            if (!TownLedgerItem.deliver(player, TownLedgerItem.create(town))) {
                                context.getSource().sendFailure(Component.literal("Could not deliver the replacement ledger."));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Delivered the ledger for " + town.name() + "."), true);
                            return 1;
                        })))));
    }

    private static int comfort(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Settlement town;
        if(context.getNodes().stream().anyMatch(n->n.getNode().getName().equals("uuid")))town=resolve(context);
        else {
            var player=context.getSource().getPlayerOrException();
            int vertical=dev.conner.hometown.settlement.SettlementValidator.Rules.current().verticalRadius();
            town=data(context).all().stream().filter(t->t.dimension().equals(player.level().dimension())
                && dev.conner.hometown.settlement.SettlementQueries.bounds(t.bellPosition(),t.radius(),vertical).contains(player.position()))
                .min(java.util.Comparator.comparingDouble(t->t.bellPosition().distSqr(player.blockPosition()))).orElseThrow(UNKNOWN::create);
        }
        int page=context.getNodes().stream().anyMatch(n->n.getNode().getName().equals("page"))
            ?com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"page"):0;
        var snapshot=dev.conner.hometown.settlement.TownLedgerService.debugComfort(context.getSource().getServer(),town);
        String output=dev.conner.hometown.comfort.ComfortDebugReport.format(snapshot,page);
        context.getSource().sendSuccess(()->Component.literal(output),false);
        return 1;
    }

    private static int safety(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Settlement town;
        if(context.getNodes().stream().anyMatch(n->n.getNode().getName().equals("uuid"))) town=resolve(context);
        else {
            var player=context.getSource().getPlayerOrException();
            int vertical=dev.conner.hometown.settlement.SettlementValidator.Rules.current().verticalRadius();
            town=data(context).all().stream().filter(t->t.dimension().equals(player.level().dimension())
                && dev.conner.hometown.settlement.SettlementQueries.bounds(t.bellPosition(),t.radius(),vertical).contains(player.position()))
                .min(java.util.Comparator.comparingDouble(t->t.bellPosition().distSqr(player.blockPosition())))
                .orElseThrow(UNKNOWN::create);
        }
        int page=context.getNodes().stream().anyMatch(n->n.getNode().getName().equals("page"))
            ?com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"page"):0;
        var snapshot=dev.conner.hometown.settlement.TownLedgerService.debugSafety(context.getSource().getServer(),town);
        String text=dev.conner.hometown.safety.SafetyDebugReport.format(snapshot,page);
        context.getSource().sendSuccess(()->Component.literal(text),false);
        return 1;
    }

    private static Settlement foodTown(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if(context.getNodes().stream().anyMatch(node->node.getNode().getName().equals("uuid")))return resolve(context);
        int vertical=dev.conner.hometown.settlement.SettlementValidator.Rules.current().verticalRadius();var player=context.getSource().getPlayerOrException();
        return data(context).all().stream().filter(t->t.dimension().equals(player.level().dimension())
                && dev.conner.hometown.settlement.SettlementQueries.bounds(t.bellPosition(),t.radius(),vertical).contains(player.position()))
                .min(java.util.Comparator.comparingDouble(t->t.bellPosition().distSqr(player.blockPosition())))
                .orElseThrow(()->new SimpleCommandExceptionType(Component.literal("Stand inside a Hometown, or provide its UUID.")).create());
    }
    private static int food(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int vertical=dev.conner.hometown.settlement.SettlementValidator.Rules.current().verticalRadius();Settlement town=foodTown(context);
        var level=context.getSource().getServer().getLevel(town.dimension());dev.conner.hometown.settlement.SettlementStats stats;
        try { stats=dev.conner.hometown.settlement.SettlementScanner.scan(level,town,vertical); }
        catch(RuntimeException exception) { stats=dev.conner.hometown.settlement.SettlementStats.unavailable(); }
        var f=dev.conner.hometown.food.FoodScanner.scan(level,town,stats,vertical,dev.conner.hometown.food.FoodRules.current());
        String output=dev.conner.hometown.food.FoodDebugReport.format(town.name(),f);context.getSource().sendSuccess(()->Component.literal(output),false);
        return f.scanStatus()==dev.conner.hometown.food.FoodScanStatus.UNAVAILABLE?0:1;
    }
    private static int foodVariety(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var data=dev.conner.hometown.food.FoodM3DebugService.variety(context.getSource().getServer(),foodTown(context));
        String output=dev.conner.hometown.food.FoodVarietyDebugReport.format(data);context.getSource().sendSuccess(()->Component.literal(output),false);return 1;
    }
    private static int foodGrowing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var snapshot=dev.conner.hometown.settlement.TownLedgerService.debugFoodGrowing(context.getSource().getServer(),foodTown(context));
        String output=dev.conner.hometown.food.FoodGrowingDebugReport.format(snapshot,dev.conner.hometown.food.CropRules.current());
        context.getSource().sendSuccess(()->Component.literal(output),false);return snapshot.scanStatus()==dev.conner.hometown.food.FoodGrowingSnapshot.Status.UNAVAILABLE?0:1;
    }

    private static int housing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var town = resolve(context);var level = context.getSource().getServer().getLevel(town.dimension());
        int vertical = dev.conner.hometown.settlement.SettlementValidator.Rules.current().verticalRadius();
        var stats = dev.conner.hometown.settlement.SettlementScanner.scan(level, town, vertical);var h = dev.conner.hometown.housing.HousingScanner.scan(level, town, stats, vertical);
        String message = "Housing Debug - " + town.name() + "\nState: " + h.state();
        if (!h.scanComplete()) message += "\nHousing data unavailable: town area/world data unavailable or scan limit reached.";
        else message += "\nPopulation: " + h.population() + "\nTotal Beds: " + h.totalBeds()
                + "\nEnclosed Beds: " + h.enclosedBeds() + "\nUnsealed Beds: " + h.unsealedBeds()
                + "\nRooms: " + h.roomCount() + "\nPrivate Rooms: " + h.privateRooms()
                + "\nShared Rooms: " + h.sharedRooms() + "\nCrowded Rooms: " + h.crowdedRooms() + "\nHigh Density Rooms: " + h.highDensityRooms()
                + "\nPrivate Beds: " + h.privateBeds() + "\nShared Beds: " + h.sharedBeds()
                + "\nCrowded Beds: " + h.crowdedBeds() + "\nHigh Density Beds: " + h.highDensityBeds()
                + "\nPrivacy: " + (h.privacyPercent().isPresent() ? h.privacyPercent().getAsInt() + "%" : "N/A")
                + "\nUnhoused Residents: " + h.unhousedResidents() + "\nSpare Capacity: " + h.spareHousingCapacity()
                + "\nCapacity: " + (h.capacityPercent().isPresent() ? h.capacityPercent().getAsInt() + "%" : "N/A");
        String output = message;context.getSource().sendSuccess(() -> Component.literal(output), false);return h.scanComplete() ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var towns = data(context).all();context.getSource().sendSuccess(() -> Component.literal("Known Hometowns: " + towns.size()), false);
        towns.forEach(town -> context.getSource().sendSuccess(() -> Component.literal(town.name() + " [" + town.id()
                + "] " + town.dimension().location() + " " + town.bellPosition().toShortString()), false));return towns.size();
    }
    private static HometownSavedData data(CommandContext<CommandSourceStack> context) { return HometownSavedData.get(context.getSource().getServer()); }
    private static Settlement resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return data(context).getSettlement(UuidArgument.getUuid(context, "uuid")).orElseThrow(UNKNOWN::create);
    }
}
