package net.rp.rpessentials.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.rp.rpessentials.RpEssentials;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how many packets a player can make the server handle. A normal client never comes close to the limit;
 * a modified one that floods the server has its extra packets ignored.
 */
public final class PacketGuard {

    /** Packets allowed at once, then per second. */
    private static final double BURST = 120;
    private static final double PER_SECOND = 20;
    private static final long WARN_EVERY_MS = 60_000L;

    private static final class Bucket {
        double left = BURST;
        long last = System.nanoTime();
        long warnedAt = 0;

        synchronized boolean take() {
            long now = System.nanoTime();
            left = Math.min(BURST, left + (now - last) / 1_000_000_000.0 * PER_SECOND);
            last = now;
            if (left < 1) return false;
            left -= 1;
            return true;
        }

        synchronized boolean shouldWarn() {
            long now = System.currentTimeMillis();
            if (now - warnedAt < WARN_EVERY_MS) return false;
            warnedAt = now;
            return true;
        }
    }

    private static final Map<UUID, Bucket> BUCKETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> LAST = new ConcurrentHashMap<>();

    private PacketGuard() {}

    /** The same handler, ignoring the packets of a player who sends too many. */
    public static <T extends CustomPacketPayload> IPayloadHandler<T> limit(IPayloadHandler<T> handler) {
        return (packet, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player && !allow(player)) return;
            handler.handle(packet, ctx);
        };
    }

    private static boolean allow(ServerPlayer player) {
        Bucket b = BUCKETS.computeIfAbsent(player.getUUID(), k -> new Bucket());
        if (b.take()) return true;
        if (b.shouldWarn()) {
            RpEssentials.LOGGER.warn("[Network] {} sends too many packets, the extra ones are ignored.",
                    player.getGameProfile().getName());
        }
        return false;
    }

    /** True at most once per {@code intervalMs} for this player and this action. */
    public static boolean every(ServerPlayer player, String action, long intervalMs) {
        long now = System.currentTimeMillis();
        Map<String, Long> m = LAST.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
        Long last = m.get(action);
        if (last != null && now - last < intervalMs) return false;
        m.put(action, now);
        return true;
    }

    public static void onLogout(UUID id) {
        BUCKETS.remove(id);
        LAST.remove(id);
    }
}
