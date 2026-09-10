package dev.conner.hometown.settlement;

import dev.conner.hometown.config.HometownServerConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class SettlementValidator {
    private SettlementValidator() {}

    public record Rules(int radius, int verticalRadius, int villagers, int beds, boolean preventOverlap, boolean consumeBook) {
        public static Rules current() {
            return new Rules(HometownServerConfig.SETTLEMENT_RADIUS.get(), HometownServerConfig.VERTICAL_SCAN_RADIUS.get(),
                    HometownServerConfig.MINIMUM_VILLAGERS.get(), HometownServerConfig.MINIMUM_BEDS.get(),
                    HometownServerConfig.PREVENT_OVERLAP.get(), HometownServerConfig.CONSUME_BOOK.get());
        }
    }

    public static Optional<Component> validate(ServerPlayer player, BlockPos bell, HometownSavedData data, Rules rules) {
        ServerLevel level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator()) return error("player");
        if (player.distanceToSqr(Vec3.atCenterOf(bell)) > 64.0) return error("distance");
        if (!loaded(level, bell) || !level.getBlockState(bell).is(Blocks.BELL)) return error("bell_missing");
        if (findBookSlot(player) == -1) return error("book");
        Optional<Settlement> existing = data.findByBell(level.dimension(), bell);
        if (existing.isPresent()) return Optional.of(Component.translatable("hometown.error.existing", existing.get().name()));
        if (rules.preventOverlap() && data.getSettlementsInDimension(level.dimension()).stream()
                .anyMatch(s -> s.overlaps(level.dimension(), bell, rules.radius()))) return error("overlap");

        int radius = rules.radius();
        int vertical = rules.verticalRadius();
        if (rules.villagers() > 0) {
            int villagers = SettlementQueries.residents(level, SettlementQueries.bounds(bell, radius, vertical)).size();
            if (villagers < rules.villagers()) return Optional.of(Component.translatable(
                    "hometown.error.residents", rules.villagers(), villagers));
        }
        if (rules.beds() > 0) {
            int beds = SettlementQueries.countBeds(level, bell, radius, vertical, rules.beds());
            if (beds < rules.beds()) return Optional.of(Component.translatable("hometown.error.beds", rules.beds(), beds));
        }
        return Optional.empty();
    }

    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return SettlementQueries.loaded(level, pos);
    }

    /** Inventory includes the off hand. Prefer main hand when it still holds the founding book. */
    public static int findBookSlot(ServerPlayer player) {
        int selected = player.getInventory().selected;
        if (player.getInventory().getItem(selected).is(Items.BOOK)) return selected;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.BOOK)) return i;
        }
        return -1;
    }

    private static Optional<Component> error(String key) {
        return Optional.of(Component.translatable("hometown.error." + key));
    }
}
