package dev.conner.hometown.settlement;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.item.TownLedgerItem;
import dev.conner.hometown.network.OpenTownNamingPayload;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;

/** All founding validation, registration and item transactions run on the server thread here. */
public final class SettlementManager {
    private static final Map<MinecraftServer, SettlementManager> MANAGERS = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SESSION_TICKS = 20 * 120;
    private final MinecraftServer server;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> lastAttempt = new HashMap<>();

    private record Pending(ResourceKey<Level> dimension, BlockPos bell, long nonce, long expires) {}
    private SettlementManager(MinecraftServer server) { this.server = server; }
    public static SettlementManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, SettlementManager::new);
    }
    public static void release(MinecraftServer server) { MANAGERS.remove(server); }
    public void cancel(UUID player) { pending.remove(player); lastAttempt.remove(player); }

    private void requireServerThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Hometown mutations require the server thread");
    }

    public Optional<OpenTownNamingPayload> beginFounding(ServerPlayer player, BlockPos bell) {
        requireServerThread();
        pending.remove(player.getUUID());
        if (!player.isShiftKeyDown() || !player.getMainHandItem().is(Items.BOOK)) return Optional.empty();
        long now = server.overworld().getGameTime();
        Long previous = lastAttempt.put(player.getUUID(), now);
        if (previous != null && now - previous < 10) return Optional.empty();
        var error = SettlementValidator.validate(player, bell, HometownSavedData.get(server), SettlementValidator.Rules.current());
        if (error.isPresent()) {
            player.sendSystemMessage(error.get());
            return Optional.empty();
        }
        Pending session = new Pending(player.serverLevel().dimension(), bell.immutable(), RANDOM.nextLong(), now + SESSION_TICKS);
        pending.put(player.getUUID(), session);
        return Optional.of(new OpenTownNamingPayload(session.bell(), session.nonce()));
    }

    public Optional<Settlement> createSettlement(ServerPlayer player, BlockPos bell, long nonce, String requestedName) {
        requireServerThread();
        // A submission consumes its session, including failures. Re-open the bell to try again.
        Pending session = pending.remove(player.getUUID());
        if (session == null || session.nonce() != nonce || !session.bell().equals(bell)
                || !session.dimension().equals(player.serverLevel().dimension())
                || server.overworld().getGameTime() > session.expires()) return fail(player, "session");
        String name;
        try { name = TownNames.validate(requestedName); }
        catch (IllegalArgumentException ex) {
            player.sendSystemMessage(Component.translatable(ex.getMessage()));
            return Optional.empty();
        }
        HometownSavedData data = HometownSavedData.get(server);
        if (data.findByName(name).isPresent()) return fail(player, "name_used");
        var rules = SettlementValidator.Rules.current();
        var error = SettlementValidator.validate(player, bell, data, rules);
        if (error.isPresent()) {
            player.sendSystemMessage(error.get());
            return Optional.empty();
        }
        // No task is queued between revalidation and commit: simultaneous players are serialized.
        int bookSlot = SettlementValidator.findBookSlot(player);
        Settlement settlement = new Settlement(UUID.randomUUID(), name, player.serverLevel().dimension(), bell,
                rules.radius(), player.getUUID(), player.getGameProfile().getName(), server.overworld().getGameTime());
        var ledger = TownLedgerItem.create(settlement);
        data.addSettlement(settlement);
        if (!TownLedgerItem.deliver(player, ledger)) {
            data.removeSettlement(settlement.id());
            return fail(player, "delivery");
        }
        // Registration and successful delivery precede charging the player.
        if (rules.consumeBook() && !player.getAbilities().instabuild) player.getInventory().getItem(bookSlot).shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        ((BellBlock)Blocks.BELL).attemptToRing(player, player.serverLevel(), bell, null);
        player.sendSystemMessage(Component.translatable("hometown.founded", name));
        Hometown.LOGGER.info("Created Hometown '{}' [{}] at {} {} {} {}", name, settlement.id(),
                settlement.dimension().location(), bell.getX(), bell.getY(), bell.getZ());
        return Optional.of(settlement);
    }

    private Optional<Settlement> fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("hometown.error." + key));
        return Optional.empty();
    }
}
