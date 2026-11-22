package net.rp.rpessentials.api.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Opening and closing of the RP server (schedule of rpessentials-schedule.toml or forced by staff),
 * posted on {@code NeoForge.EVENT_BUS}.
 * <ul>
 *   <li>{@link ForceStateChanging}: before /schedule forceopen, forceclose, resetforcestate or the API
 *       change the forced state. Cancellable: nothing changes, nobody is kicked.</li>
 *   <li>{@link Opened}: the server just opened (scheduled opening or forced open).</li>
 *   <li>{@link Closed}: the server just closed; players who are not exempt have already been kicked.</li>
 * </ul>
 */
public abstract class ServerScheduleEvent extends Event {

    /** Why the state changed. */
    public enum Cause {
        /** Opening hours of the schedule config. */
        SCHEDULE,
        /** /schedule forceopen, forceclose or resetforcestate. */
        COMMAND,
        /** Another mod, through RpEssentialsAPI. */
        API
    }

    /** Forced state of the schedule config ({@code forceState}). */
    public enum ForceState { NONE, FORCE_OPEN, FORCE_CLOSED }

    private final MinecraftServer server;
    private final Cause cause;

    protected ServerScheduleEvent(MinecraftServer server, Cause cause) {
        this.server = server;
        this.cause = cause;
    }

    public MinecraftServer getServer() { return server; }

    public Cause getCause() { return cause; }

    /** Before the forced state changes. Cancel it to keep the current state. */
    public static class ForceStateChanging extends ServerScheduleEvent implements ICancellableEvent {
        private final ForceState oldState;
        private final ForceState newState;
        private final String actor;

        public ForceStateChanging(MinecraftServer server, Cause cause, ForceState oldState, ForceState newState, String actor) {
            super(server, cause);
            this.oldState = oldState;
            this.newState = newState;
            this.actor = actor;
        }

        public ForceState getOldState() { return oldState; }

        public ForceState getNewState() { return newState; }

        /** Name of who asked for it (staff name, "Server", or the name given to the API). */
        public String getActor() { return actor; }
    }

    /** The server just opened. */
    public static class Opened extends ServerScheduleEvent {
        private final String openTime;
        private final String closeTime;

        public Opened(MinecraftServer server, Cause cause, String openTime, String closeTime) {
            super(server, cause);
            this.openTime = openTime;
            this.closeTime = closeTime;
        }

        /** Scheduled opening time (HH:mm), null when forced. */
        public String getOpenTime() { return openTime; }

        /** Scheduled closing time (HH:mm), null when forced. */
        public String getCloseTime() { return closeTime; }

        public boolean isForced() { return getCause() != Cause.SCHEDULE; }
    }

    /** The server just closed. */
    public static class Closed extends ServerScheduleEvent {
        private final int kickedCount;

        public Closed(MinecraftServer server, Cause cause, int kickedCount) {
            super(server, cause);
            this.kickedCount = kickedCount;
        }

        /** Players kicked because they were not exempt. */
        public int getKickedCount() { return kickedCount; }

        public boolean isForced() { return getCause() != Cause.SCHEDULE; }
    }
}
