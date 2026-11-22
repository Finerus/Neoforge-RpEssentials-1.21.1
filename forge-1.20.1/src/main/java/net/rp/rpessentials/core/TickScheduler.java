package net.rp.rpessentials.core;

import net.minecraft.server.MinecraftServer;
import net.rp.rpessentials.RpEssentials;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Periodic server tasks, declared in one line each:
 * {@code every(40, 0, "tab blur", TabBlur::tick)} runs every 40 ticks (2 seconds).
 * <p>
 * The offset spreads tasks that share a period over different ticks. A task that throws is logged
 * (at most once per minute per task) and does not stop the other tasks nor crash the server.
 */
public final class TickScheduler {

    private record Task(String name, IntSupplier period, int offset, Consumer<MinecraftServer> action) {}

    private final List<Task> tasks = new ArrayList<>();
    private final java.util.Map<String, Long> lastError = new java.util.HashMap<>();
    private long tick = 0;

    /** Runs {@code action} every {@code ticks} ticks (20 ticks = 1 second), shifted by {@code offset}. */
    public TickScheduler every(int ticks, int offset, String name, Consumer<MinecraftServer> action) {
        return every(() -> ticks, offset, name, action);
    }

    /** Same, with a period read each tick (for example from the config). Values below 1 count as 1. */
    public TickScheduler every(IntSupplier ticks, int offset, String name, Consumer<MinecraftServer> action) {
        tasks.add(new Task(name, ticks, offset, action));
        return this;
    }

    /** Called once per server tick. */
    public void tick(MinecraftServer server) {
        long t = tick++;
        for (Task task : tasks) {
            int period = Math.max(1, task.period().getAsInt());
            if ((t + task.offset()) % period != 0) continue;
            try {
                task.action().accept(server);
            } catch (Exception e) {
                long now = System.currentTimeMillis();
                Long last = lastError.get(task.name());
                if (last == null || now - last > 60_000L) {
                    lastError.put(task.name(), now);
                    RpEssentials.LOGGER.error("[RpEssentials] Periodic task '{}' failed", task.name(), e);
                }
            }
        }
    }
}
