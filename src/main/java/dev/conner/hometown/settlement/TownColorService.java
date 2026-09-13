package dev.conner.hometown.settlement;

import dev.conner.hometown.component.HometownDataComponents;
import dev.conner.hometown.item.HometownItems;
import dev.conner.hometown.network.OpenTownColorsPayload;
import dev.conner.hometown.network.RequestTownColorsPayload;
import dev.conner.hometown.network.SubmitTownColorsPayload;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;

/** Server-authoritative one-time town color configuration for existing/migrated towns. */
public final class TownColorService {
    private TownColorService() {}

    public static Optional<OpenTownColorsPayload> begin(ServerPlayer player, RequestTownColorsPayload request) {
        var server = player.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Town color requests require the server thread");
        if (!player.isAlive()) return Optional.empty();

        var stack = player.getItemInHand(request.hand());
        if (!stack.is(HometownItems.TOWN_LEDGER.get())) {
            player.sendSystemMessage(Component.literal("Hold the Town Ledger whose colors you want to configure."));
            return Optional.empty();
        }
        var link = stack.get(HometownDataComponents.SETTLEMENT_ID.get());
        if (link == null) {
            player.sendSystemMessage(Component.literal("This Town Ledger is not linked to a known Hometown."));
            return Optional.empty();
        }
        var data = HometownSavedData.get(server);
        var town = data.getSettlement(link.settlementId()).orElse(null);
        if (town == null) {
            player.sendSystemMessage(Component.literal("This Town Ledger no longer points to a known Hometown."));
            return Optional.empty();
        }
        if (data.civicState(town.id()).colorsConfigured()) {
            player.sendSystemMessage(Component.literal("Town colors are already configured."));
            return Optional.empty();
        }
        return Optional.of(new OpenTownColorsPayload(town.id(), town.name(), request.hand()));
    }

    public static boolean submit(ServerPlayer player, SubmitTownColorsPayload request) {
        var server = player.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Town color mutations require the server thread");
        if (!player.isAlive()) return false;

        var stack = player.getItemInHand(request.hand());
        if (!stack.is(HometownItems.TOWN_LEDGER.get())) return fail(player, "Hold the linked Town Ledger while saving town colors.");
        var link = stack.get(HometownDataComponents.SETTLEMENT_ID.get());
        if (link == null || !link.settlementId().equals(request.settlementId())) {
            return fail(player, "The held Town Ledger does not match this color-selection screen.");
        }

        var data = HometownSavedData.get(server);
        var town = data.getSettlement(request.settlementId()).orElse(null);
        if (town == null) return fail(player, "That Hometown no longer exists.");
        if (request.primary() == request.secondary()) return fail(player, "Primary and secondary colors must be different.");
        if (data.civicState(town.id()).colorsConfigured()) return fail(player, "Town colors are already configured.");

        try {
            if (!data.configureColors(town.id(), request.primary(), request.secondary())) {
                return fail(player, "Town colors were not changed.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return fail(player, ex.getMessage());
        }

        player.sendSystemMessage(Component.literal(town.name() + " colors set to "
                + displayName(request.primary()) + " and " + displayName(request.secondary()) + "."));
        return true;
    }

    public static String displayName(DyeColor color) {
        String raw = color.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (upper && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                upper = false;
            } else {
                result.append(c);
            }
            if (c == ' ') upper = true;
        }
        return result.toString();
    }

    private static boolean fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message == null || message.isBlank() ? "Town colors could not be configured." : message));
        return false;
    }
}
