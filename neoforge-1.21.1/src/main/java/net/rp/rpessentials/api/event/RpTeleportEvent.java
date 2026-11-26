package net.rp.rpessentials.api.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
//#if forge
//? import net.minecraftforge.eventbus.api.Cancelable;
//#else
import net.neoforged.bus.api.ICancellableEvent;
//#endif

/**
 * Posted on NeoForge.EVENT_BUS around the teleports of RpEssentials (/rp spawn, /rp afk,
 * and RpEssentialsAPI.teleport). Other mods can use it for animations, sounds or screen effects.
 * <ul>
 *   <li>{@link Warmup}: when the teleport is requested. Cancellable. The delay can be changed (0 = instant).</li>
 *   <li>{@link Tick}: every tick of the delay, with the ticks left (particles, sounds...).</li>
 *   <li>{@link Cancelled}: the player moved, was hurt, or the teleport was cancelled.</li>
 *   <li>{@link Pre}: right before the teleport. Cancellable.</li>
 *   <li>{@link Post}: right after the teleport.</li>
 * </ul>
 * {@link #getKind()} is "spawn", "afk", or the id given by the mod that called the API.
 */
public abstract class RpTeleportEvent extends Event {

    private final ServerPlayer player;
    private final String kind;
    private final ServerLevel level;
    private final Vec3 destination;

    protected RpTeleportEvent(ServerPlayer player, String kind, ServerLevel level, Vec3 destination) {
        this.player = player;
        this.kind = kind;
        this.level = level;
        this.destination = destination;
    }

    public ServerPlayer getPlayer() { return player; }

    public String getKind() { return kind; }

    public ServerLevel getLevel() { return level; }

    public Vec3 getDestination() { return destination; }

    //#if forge
    //? @Cancelable
//?
    //? public static class Warmup extends RpTeleportEvent {
    //#else
    public static class Warmup extends RpTeleportEvent implements ICancellableEvent {
    //#endif
        private int warmupTicks;
        private boolean cancelOnMove;
        private boolean cancelOnDamage;

        public Warmup(ServerPlayer player, String kind, ServerLevel level, Vec3 destination,
                      int warmupTicks, boolean cancelOnMove, boolean cancelOnDamage) {
            super(player, kind, level, destination);
            this.warmupTicks = warmupTicks;
            this.cancelOnMove = cancelOnMove;
            this.cancelOnDamage = cancelOnDamage;
        }

        public int getWarmupTicks() { return warmupTicks; }

        /** Changes the delay before the teleport, in ticks (20 = 1 second). 0 = instant. */
        public void setWarmupTicks(int ticks) { this.warmupTicks = Math.max(0, ticks); }

        public boolean cancelsOnMove() { return cancelOnMove; }

        public void setCancelOnMove(boolean v) { this.cancelOnMove = v; }

        public boolean cancelsOnDamage() { return cancelOnDamage; }

        public void setCancelOnDamage(boolean v) { this.cancelOnDamage = v; }
    }

    public static class Tick extends RpTeleportEvent {
        private final int ticksLeft;
        private final int totalTicks;

        public Tick(ServerPlayer player, String kind, ServerLevel level, Vec3 destination, int ticksLeft, int totalTicks) {
            super(player, kind, level, destination);
            this.ticksLeft = ticksLeft;
            this.totalTicks = totalTicks;
        }

        public int getTicksLeft() { return ticksLeft; }

        public int getTotalTicks() { return totalTicks; }

        /** 0 at the start of the delay, 1 at the teleport. */
        public float getProgress() { return totalTicks <= 0 ? 1f : 1f - (float) ticksLeft / totalTicks; }
    }

    public static class Cancelled extends RpTeleportEvent {
        public enum Reason { MOVED, DAMAGED, REPLACED, DISCONNECTED, OTHER }

        private final Reason reason;

        public Cancelled(ServerPlayer player, String kind, ServerLevel level, Vec3 destination, Reason reason) {
            super(player, kind, level, destination);
            this.reason = reason;
        }

        public Reason getReason() { return reason; }
    }

    //#if forge
    //? @Cancelable
//?
    //? public static class Pre extends RpTeleportEvent {
    //#else
    public static class Pre extends RpTeleportEvent implements ICancellableEvent {
    //#endif
        public Pre(ServerPlayer player, String kind, ServerLevel level, Vec3 destination) {
            super(player, kind, level, destination);
        }
    }

    public static class Post extends RpTeleportEvent {
        private final ServerLevel fromLevel;
        private final Vec3 from;

        public Post(ServerPlayer player, String kind, ServerLevel level, Vec3 destination, ServerLevel fromLevel, Vec3 from) {
            super(player, kind, level, destination);
            this.fromLevel = fromLevel;
            this.from = from;
        }

        public ServerLevel getFromLevel() { return fromLevel; }

        public Vec3 getFrom() { return from; }
    }
}
