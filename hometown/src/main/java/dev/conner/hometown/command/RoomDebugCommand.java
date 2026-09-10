package dev.conner.hometown.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.conner.hometown.room.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Explicit operator diagnostic only; no tick hooks, persistent state, or Ledger coupling. */
final class RoomDebugCommand {
    private RoomDebugCommand() {}
    static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();
        var world = new LoadedRoomWorld(source.getLevel());
        BlockPos center = player.blockPosition(), nearest = null;
        double distance = Double.MAX_VALUE;
        // Documented nearest-bed alternative; fixed radius, loaded chunks only, no ray trace loads.
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -6, -6), center.offset(6, 6, 6))) {
            BlockPos head = world.intactBedAt(pos);
            if (head == null) continue;
            double candidate = head.distSqr(center);
            if (candidate < distance) { nearest = head; distance = candidate; }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("No intact bed found within 6 blocks on each axis in loaded chunks."));
            return 0;
        }
        var detector = new RoomDetector(world);
        var result = detector.detect(nearest);
        String message = (result.enclosed() ? "Room detected" : "Room not detected")
                + "\nBed: " + nearest.toShortString() + "\nEnclosed: " + (result.enclosed() ? "Yes" : "No")
                + "\n" + (result.enclosed() ? "Interior Volume: " : "Explored Volume (partial): ") + result.interiorVolume()
                + "\n" + (result.enclosed() ? "Beds in Room: " : "Beds observed (partial): ") + result.bedCount()
                + "\nRepresentative: " + result.representativePosition().toShortString()
                + (result.enclosed() ? "" : "\nFailure: " + result.failureReason() + " - " + result.failureReason().description())
                + "\nInspected cells: " + detector.inspectedCells();
        source.sendSuccess(() -> Component.literal(message), false);
        return result.enclosed() ? 1 : 0;
    }
}
