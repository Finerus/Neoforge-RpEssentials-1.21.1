package net.rp.rpessentials.gameplay;

import com.google.gson.Gson;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.api.event.ServerScheduleEvent;
import net.rp.rpessentials.compat.DiscordWebhook;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ScheduleConfig;
import net.rp.rpessentials.core.JsonStore;
import net.rp.rpessentials.lang.Msg;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.moderation.DeathRPManager;
import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.util.PlayerRefs;
import net.rp.rpessentials.util.TimeHelper;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Opening hours of the server: who may be there, announcements, forced state, death and HRP hours.
 * The hours themselves (slots, midnight, exceptions) are in {@link ScheduleClock}.
 */
public class RpEssentialsScheduleManager {

    private static final DateTimeFormatter FMT = ScheduleClock.FMT;
    private static volatile ScheduleClock clock = new ScheduleClock();
    private static String lastSummary = "";
    private static String lastHrpSlotKey = "";
    private static String lastDeathHoursSlotKey = "";
    /** Warnings already sent: "event time|kind|minutes". */
    private static final Set<String> sentWarnings = new HashSet<>();

    // =========================================================================
    // SAVED STATE (schedule-state.json)
    // =========================================================================
    /** What was last announced and the details of the forced state: survives a restart. */
    private static final class State {
        /** True = players were told it is open, false = closed, null = nothing announced yet. */
        Boolean announcedOpen;
        String force = "NONE";
        /** End of the forced state (epoch ms), 0 = until someone changes it. */
        long forceUntil;
        String forceReason = "";

        State copy() {
            State s = new State();
            s.announcedOpen = announcedOpen;
            s.force = force;
            s.forceUntil = forceUntil;
            s.forceReason = forceReason;
            return s;
        }
    }

    private static final JsonStore<State> STORE = new JsonStore<>("schedule-state.json", State.class, "Schedule", new Gson());
    private static State state = null;
    private static boolean firstTick = true;

    private static State state() {
        if (state == null) {
            STORE.reset();
            State s = STORE.load();
            state = s != null ? s : new State();
        }
        return state;
    }

    private static void saveState() { STORE.save(state().copy()); }

    /** Server stopped: the next world has its own state. */
    public static void onServerStopped() {
        state = null;
        firstTick = true;
        sentWarnings.clear();
        ScheduleGate.clear();
    }

    private static void setAnnounced(Boolean open) {
        if (java.util.Objects.equals(state().announcedOpen, open)) return;
        state().announcedOpen = open;
        saveState();
    }

    // =========================================================================
    // RELOAD
    // =========================================================================
    public static void reload() {
        configWarnings.clear();
        TimeHelper.reload();

        if (!Cfg.ready(ScheduleConfig.SPEC)) {
            RpEssentials.LOGGER.debug("[Schedule] Config not loaded yet, skipping reload.");
            return;
        }
        try {
            ScheduleClock c = new ScheduleClock();
            c.setDay(DayOfWeek.MONDAY,    parseDay(ScheduleConfig.MONDAY_ENABLED,    ScheduleConfig.MONDAY_OPEN,    ScheduleConfig.MONDAY_CLOSE,    ScheduleConfig.MONDAY_EXTRA));
            c.setDay(DayOfWeek.TUESDAY,   parseDay(ScheduleConfig.TUESDAY_ENABLED,   ScheduleConfig.TUESDAY_OPEN,   ScheduleConfig.TUESDAY_CLOSE,   ScheduleConfig.TUESDAY_EXTRA));
            c.setDay(DayOfWeek.WEDNESDAY, parseDay(ScheduleConfig.WEDNESDAY_ENABLED, ScheduleConfig.WEDNESDAY_OPEN, ScheduleConfig.WEDNESDAY_CLOSE, ScheduleConfig.WEDNESDAY_EXTRA));
            c.setDay(DayOfWeek.THURSDAY,  parseDay(ScheduleConfig.THURSDAY_ENABLED,  ScheduleConfig.THURSDAY_OPEN,  ScheduleConfig.THURSDAY_CLOSE,  ScheduleConfig.THURSDAY_EXTRA));
            c.setDay(DayOfWeek.FRIDAY,    parseDay(ScheduleConfig.FRIDAY_ENABLED,    ScheduleConfig.FRIDAY_OPEN,    ScheduleConfig.FRIDAY_CLOSE,    ScheduleConfig.FRIDAY_EXTRA));
            c.setDay(DayOfWeek.SATURDAY,  parseDay(ScheduleConfig.SATURDAY_ENABLED,  ScheduleConfig.SATURDAY_OPEN,  ScheduleConfig.SATURDAY_CLOSE,  ScheduleConfig.SATURDAY_EXTRA));
            c.setDay(DayOfWeek.SUNDAY,    parseDay(ScheduleConfig.SUNDAY_ENABLED,    ScheduleConfig.SUNDAY_OPEN,    ScheduleConfig.SUNDAY_CLOSE,    ScheduleConfig.SUNDAY_EXTRA));
            parseExceptions(c);
            clock = c;

            StringBuilder sb = new StringBuilder();
            for (DayOfWeek day : DayOfWeek.values()) sb.append(day).append(' ').append(slotsText(c.day(day), "CLOSED")).append("; ");
            String summary = sb.toString();
            if (!summary.equals(lastSummary)) {
                lastSummary = summary;
                RpEssentials.LOGGER.info("[Schedule] {}", summary);
            }

            MinecraftServer srv = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (srv != null && !configWarnings.isEmpty()) {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    if (!RpPerms.has(p, RpPerm.NOTIFY)) continue;
                    for (String w : getConfigWarnings(p)) p.sendSystemMessage(ColorHelper.parseSection(
                            ServerLang.get(p, MessagesConfig.SCHED_SYS_CONFIG_WARNING, "warning", w)));
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[Schedule] Error parsing schedule", e);
        }
    }

    private static List<ScheduleClock.Slot> parseDay(
            net.neoforged.neoforge.common.ModConfigSpec.BooleanValue enabled,
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> open,
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> close,
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<? extends String>> extra) {
        List<ScheduleClock.Slot> out = new ArrayList<>();
        if (!enabled.get()) return out;
        LocalTime o = ScheduleClock.parseTime(open.get(), false);
        LocalTime c = ScheduleClock.parseTime(close.get(), true);
        if (o == null) warnConfig(MessagesConfig.SCHED_WARN_INVALID_OPEN, "day", String.join(".", open.getPath()), "value", open.get());
        if (c == null) warnConfig(MessagesConfig.SCHED_WARN_INVALID_CLOSE, "day", String.join(".", close.getPath()), "value", close.get());
        if (o != null && c != null) out.add(new ScheduleClock.Slot(o, c));
        for (String line : extra.get()) {
            ScheduleClock.Slot s = ScheduleClock.parseSlot(line);
            if (s != null) out.add(s);
            else warnConfig(MessagesConfig.SCHED_WARN_INVALID_SLOT, "day", String.join(".", extra.getPath()), "value", line);
        }
        return out;
    }

    private static void parseExceptions(ScheduleClock c) {
        for (String raw : ScheduleConfig.SCHEDULE_EXCEPTIONS.get()) {
            String line = raw.trim();
            int eq = line.indexOf('=');
            try {
                if (eq < 0) throw new IllegalArgumentException();
                LocalDate date = LocalDate.parse(line.substring(0, eq).trim());
                String v = line.substring(eq + 1).trim();
                List<ScheduleClock.Slot> slots;
                if (v.equalsIgnoreCase("CLOSED")) slots = List.of();
                else if (v.equalsIgnoreCase("OPEN")) slots = List.of(new ScheduleClock.Slot(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT));
                else slots = ScheduleClock.parseSlots(v);
                if (slots == null) throw new IllegalArgumentException();
                c.setException(date, slots);
            } catch (Exception e) {
                warnConfig(MessagesConfig.SCHED_WARN_INVALID_EXCEPTION, "value", line);
            }
        }
    }

    private static String slotsText(List<ScheduleClock.Slot> slots, String closed) {
        if (slots.isEmpty()) return closed;
        List<String> out = new ArrayList<>();
        for (ScheduleClock.Slot s : slots) out.add(s.text());
        return String.join(", ", out);
    }

    // =========================================================================
    // QUERIES
    // =========================================================================
    /** An opening of a day, as shown to the players. */
    public record Hours(String open, String close) {}

    private static List<Hours> hours(List<ScheduleClock.Slot> slots) {
        List<Hours> out = new ArrayList<>();
        for (ScheduleClock.Slot s : slots) out.add(new Hours(s.open().format(FMT), s.closeText()));
        return out;
    }

    /** Openings of a weekday, empty = closed. */
    public static List<Hours> getHours(DayOfWeek day) { return hours(clock.day(day)); }

    /** Upcoming special dates (today included), in date order; no opening = closed. */
    public static Map<LocalDate, List<Hours>> getUpcomingExceptions() {
        Map<LocalDate, List<Hours>> out = new java.util.LinkedHashMap<>();
        clock.exceptionsFrom(today()).forEach((date, slots) -> out.put(date, hours(slots)));
        return out;
    }

    public static boolean isScheduleExempt(ServerPlayer player) {
        if (RpPerms.has(player, RpPerm.BYPASS_SCHEDULE)) return true;
        if (player.getServer() != null && player.getServer().isSingleplayerOwner(player.getGameProfile())) return true;
        return PlayerRefs.SCHEDULE.contains(player.getGameProfile());
    }

    private static boolean scheduleOn() {
        return Cfg.ready(ScheduleConfig.SPEC) && ScheduleConfig.ENABLE_SCHEDULE.get();
    }

    /** True when the schedule alone (no forced state) has the server open at this time. */
    private static boolean scheduledOpen(LocalDateTime t) {
        return !scheduleOn() || clock.active(t) != null;
    }

    /** True when the server is open now: forced state, else schedule. */
    public static boolean isServerOpen() {
        if (!Cfg.ready(ScheduleConfig.SPEC)) return true;
        return switch (getForceState()) {
            case FORCE_OPEN -> true;
            case FORCE_CLOSED -> false;
            case NONE -> scheduledOpen(now());
        };
    }

    /**
     * Check made once the player is in the world: the reference, for what {@link ScheduleGate} could not know before.
     * Null = may stay.
     */
    public static Component canPlayerJoin(ServerPlayer player) {
        if (isServerOpen() || isScheduleExempt(player)) return null;
        if (player.getServer() != null && ScheduleGate.pendingRoleAllows(player.getServer(), player.getGameProfile())) return null;
        return closedMessage();
    }

    /** When the server will be open again, null = not planned. */
    private static LocalDateTime reopening() {
        LocalDateTime from = now();
        if (getForceState() == ServerScheduleEvent.ForceState.FORCE_CLOSED) {
            from = forceEnd();
            if (from == null) return null;
            if (scheduledOpen(from)) return from;
        }
        ScheduleClock.Session next = clock.next(from);
        return next != null ? next.start() : null;
    }

    /** When the server will close, null = not planned. */
    private static LocalDateTime closing() {
        LocalDateTime from = now();
        if (getForceState() == ServerScheduleEvent.ForceState.FORCE_OPEN) {
            from = forceEnd();
            if (from == null || !scheduleOn()) return null;
            ScheduleClock.Session s = clock.active(from);
            return s != null ? s.end() : from;
        }
        if (!scheduleOn()) return null;
        ScheduleClock.Session s = clock.active(from);
        return s != null ? s.end() : null;
    }

    /** "2h 15min" until then, in the viewer's language (null = server language). */
    private static String in(ServerPlayer v, LocalDateTime t) {
        return MessagesConfig.formatDuration(v, (int) Math.max(1, (seconds(t) + 59) / 60));
    }

    /** Seconds until then (clock changes of the time zone included). */
    private static long seconds(LocalDateTime t) {
        return Duration.between(Instant.now(), t.atZone(zone()).toInstant()).getSeconds();
    }

    /** What a refused or kicked player reads. */
    public static Component closedMessage() {
        LocalDateTime open = reopening();
        if (getForceState() == ServerScheduleEvent.ForceState.FORCE_CLOSED) {
            StringBuilder sb = new StringBuilder(ScheduleConfig.FORCE_CLOSED_MESSAGE.get());
            String reason = forceReason();
            if (!reason.isEmpty()) sb.append('\n').append(ServerLang.get(null, MessagesConfig.SCHED_FORCE_REASON, "reason", reason));
            if (open != null) sb.append('\n').append(ServerLang.get(null, MessagesConfig.SCHED_REOPENS_IN, "in", in(null, open)));
            return ColorHelper.parseColors(sb.toString());
        }
        if (open == null) return ColorHelper.parseColors(ScheduleConfig.MSG_SERVER_CLOSED_NO_OPENING.get());
        ScheduleClock.Session s = clock.active(open);
        return ColorHelper.parseColors(ScheduleConfig.MSG_SERVER_CLOSED.get()
                .replace("{open}", open.format(FMT))
                .replace("{close}", s != null ? s.end().format(FMT) : "?")
                .replace("{day}", getDayName(null, open.getDayOfWeek()))
                .replace("{in}", in(null, open)));
    }

    /** State shown in the multiplayer list, null when there is nothing to show (no schedule, option off). */
    public static ServerListState serverListState() {
        if (!Cfg.get(ScheduleConfig.SHOW_IN_SERVER_LIST, true)) return null;
        boolean forced = getForceState() != ServerScheduleEvent.ForceState.NONE;
        if (!scheduleOn() && !forced) return null;
        boolean open = isServerOpen();
        LocalDateTime next = open ? closing() : reopening();
        return new ServerListState(open, forced, ServerLang.fromMod(), next == null ? 0L : next.atZone(zone()).toEpochSecond());
    }

    /** Lines above the player names in the multiplayer list, in the server language; the first one carries the state. */
    public static List<com.mojang.authlib.GameProfile> serverListLines() {
        ServerListState s = serverListState();
        if (s == null) return List.of();
        List<com.mojang.authlib.GameProfile> out = new ArrayList<>(2);
        out.add(new com.mojang.authlib.GameProfile(s.toUuid(), (s.open() ? "§a" : "§c") + getTimeUntilNextEvent(null)));
        String reason = forceReason();
        if (!reason.isEmpty()) {
            String text = ServerLang.get(null, MessagesConfig.SCHED_FORCE_REASON);
            int at = text.indexOf("{reason}");
            java.util.UUID id = at < 0 || !s.fromMod() ? net.minecraft.Util.NIL_UUID
                    : ServerListState.reasonUuid(at, text.length() - at - "{reason}".length());
            out.add(new com.mojang.authlib.GameProfile(id, text.replace("{reason}", reason)));
        }
        return out;
    }

    /** Staff notice of a refused player (notifyRefusedJoin). */
    public static void notifyRefused(MinecraftServer server, String name) {
        if (!Cfg.get(ScheduleConfig.NOTIFY_REFUSED_JOIN, false)) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpPerms.has(p, RpPerm.NOTIFY)) p.sendSystemMessage(Msg.c(p, MessagesConfig.LOGIN_REFUSED_NOTICE, "player", name));
        }
    }

    public static String getTimeUntilNextEvent() {
        return getTimeUntilNextEvent(null);
    }

    /** Short status ("Open: closing in 2h05"...) in the viewer's language. */
    public static String getTimeUntilNextEvent(ServerPlayer v) {
        if (!Cfg.ready(ScheduleConfig.SPEC)) return ServerLang.get(v, MessagesConfig.SCHED_NEXT_NOT_INIT);
        boolean forced = getForceState() != ServerScheduleEvent.ForceState.NONE;
        if (!scheduleOn() && !forced) return ServerLang.get(v, MessagesConfig.SCHED_NEXT_DISABLED);
        if (isServerOpen()) {
            LocalDateTime close = closing();
            if (close == null) return ServerLang.get(v, MessagesConfig.SCHED_NEXT_NO_CLOSING);
            long min = Math.max(0, seconds(close) / 60);
            return ServerLang.get(v, MessagesConfig.SCHED_NEXT_OPEN, "h", String.valueOf(min / 60), "min", String.format("%02d", min % 60));
        }
        LocalDateTime open = reopening();
        if (open == null) return ServerLang.get(v, MessagesConfig.SCHED_NEXT_NONE);
        return ServerLang.get(v, MessagesConfig.SCHED_NEXT_CLOSED,
                "day", open.toLocalDate().equals(today()) ? ServerLang.get(v, MessagesConfig.SCHED_TODAY) : getDayName(v, open.getDayOfWeek()),
                "time", open.format(FMT));
    }

    /** Kicks whoever may not stay, when the server is closed (config or role changed). */
    public static void enforceOnline(MinecraftServer server) {
        if (server == null || isServerOpen()) return;
        kickNotExempt(server);
    }

    public static void enforceOnlineDelayed(MinecraftServer server) {
        if (server == null) return;
        CompletableFuture.runAsync(() -> server.execute(() -> enforceOnline(server)),
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }

    public static String getDayName(java.time.DayOfWeek day) {
        return getDayName(null, day);
    }

    /** Day name in the viewer's language (null = server language). */
    public static String getDayName(ServerPlayer v, java.time.DayOfWeek day) {
        return ServerLang.get(v, switch (day) {
            case MONDAY    -> MessagesConfig.SCHEDULE_DAY_MONDAY;
            case TUESDAY   -> MessagesConfig.SCHEDULE_DAY_TUESDAY;
            case WEDNESDAY -> MessagesConfig.SCHEDULE_DAY_WEDNESDAY;
            case THURSDAY  -> MessagesConfig.SCHEDULE_DAY_THURSDAY;
            case FRIDAY    -> MessagesConfig.SCHEDULE_DAY_FRIDAY;
            case SATURDAY  -> MessagesConfig.SCHEDULE_DAY_SATURDAY;
            case SUNDAY    -> MessagesConfig.SCHEDULE_DAY_SUNDAY;
        });
    }

    // =========================================================================
    // DEATH HOURS / HRP HOURS
    // =========================================================================
    public static boolean isDeathHour() {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (!ScheduleConfig.DEATH_HOURS_ENABLED.get()) return false;
            LocalTime now = nowTime();
            for (String slot : ScheduleConfig.DEATH_HOURS_SLOTS.get())
                if (isInSlot(now, slot)) return true;
        }
        return false;
    }

    public static boolean isHrpTolerated() {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (!ScheduleConfig.ENABLE_HRP_HOURS.get()) return false;
            LocalTime now = nowTime();
            for (String slot : ScheduleConfig.HRP_TOLERATED_SLOTS.get())
                if (isInSlot(now, slot)) return true;
        }
        return false;
    }

    public static boolean isHrpAllowed() {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (!ScheduleConfig.ENABLE_HRP_HOURS.get()) return false;
            LocalTime now = nowTime();
            for (String slot : ScheduleConfig.HRP_ALLOWED_SLOTS.get())
                if (isInSlot(now, slot)) return true;
        }
        return false;
    }

    // =========================================================================
    // FORCED STATE (/schedule forceopen, forceclose, resetforcestate and RpEssentialsAPI)
    // =========================================================================

    /** Forced state of the schedule config; NONE when the config is not loaded or the value is unknown. */
    public static ServerScheduleEvent.ForceState getForceState() {
        if (!Cfg.ready(ScheduleConfig.SPEC)) return ServerScheduleEvent.ForceState.NONE;
        return switch (ScheduleConfig.FORCE_STATE.get()) {
            case "FORCE_OPEN" -> ServerScheduleEvent.ForceState.FORCE_OPEN;
            case "FORCE_CLOSED" -> ServerScheduleEvent.ForceState.FORCE_CLOSED;
            default -> ServerScheduleEvent.ForceState.NONE;
        };
    }

    /** Details of the forced state, only while it is the one they were given for (it can be changed in the config). */
    private static boolean hasForceDetails() {
        return getForceState() != ServerScheduleEvent.ForceState.NONE && getForceState().name().equals(state().force);
    }

    /** Why the state is forced, "" when not said. */
    public static String forceReason() {
        return hasForceDetails() && state().forceReason != null ? state().forceReason : "";
    }

    /** End of the forced state, null = until someone changes it. */
    private static LocalDateTime forceEnd() {
        return hasForceDetails() && state().forceUntil > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(state().forceUntil), zone()) : null;
    }

    /** " (reason: ..., 2h left)" of the forced state, "" when there is nothing to say. */
    public static String forceDetails(ServerPlayer v) {
        List<String> parts = new ArrayList<>();
        if (!forceReason().isEmpty()) parts.add(ServerLang.get(v, MessagesConfig.SCHED_FORCE_REASON, "reason", forceReason()));
        LocalDateTime end = forceEnd();
        if (end != null) parts.add(ServerLang.get(v, MessagesConfig.SCHED_FORCE_LEFT, "in", in(v, end)));
        return parts.isEmpty() ? "" : " §8(" + String.join("§8, ", parts) + "§8)";
    }

    /** Posts ForceStateChanging and saves the new state. False when another mod cancelled it. */
    private static boolean setForceState(MinecraftServer server, ServerScheduleEvent.ForceState next, String actor,
                                         ServerScheduleEvent.Cause cause, int minutes, String reason) {
        var ev = new ServerScheduleEvent.ForceStateChanging(server, cause, getForceState(), next, actor);
        //#if forge
        //? if (MinecraftForge.EVENT_BUS.post(ev)) return false;
        //#else
        if (NeoForge.EVENT_BUS.post(ev).isCanceled()) return false;
        //#endif
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            ScheduleConfig.FORCE_STATE.set(next.name());
            ScheduleConfig.SPEC.save();
        }
        State s = state();
        s.force = next.name();
        s.forceUntil = minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : 0L;
        s.forceReason = reason == null ? "" : reason.strip();
        sentWarnings.clear();
        saveState();
        return true;
    }

    private static void notifyStaff(MinecraftServer server, net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> key,
                                    boolean details, String... args) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!RpPerms.has(p, RpPerm.NOTIFY)) continue;
            p.sendSystemMessage(ColorHelper.parseSection(ServerLang.get(p, key, args) + (details ? forceDetails(p) : "")));
        }
    }

    /** Back to the normal schedule. False when cancelled by another mod. */
    public static boolean resetForceState(MinecraftServer server, String actor, ServerScheduleEvent.Cause cause) {
        if (!setForceState(server, ServerScheduleEvent.ForceState.NONE, actor, cause, 0, "")) return false;
        notifyStaff(server, MessagesConfig.STAFF_SCHED_FORCE_RESET, false, "staff", actor);
        // Opens or closes at once if the schedule says something else.
        applyState(server);
        return true;
    }

    /** Opens the server now, whatever the schedule says. False when cancelled by another mod. */
    public static boolean forceOpen(MinecraftServer server, String actor, ServerScheduleEvent.Cause cause) {
        return forceOpen(server, actor, cause, 0, "");
    }

    /** Same, for some minutes (0 = until someone changes it) and with a reason. */
    public static boolean forceOpen(MinecraftServer server, String actor, ServerScheduleEvent.Cause cause, int minutes, String reason) {
        if (!setForceState(server, ServerScheduleEvent.ForceState.FORCE_OPEN, actor, cause, minutes, reason)) return false;
        setAnnounced(true);
        announceOpened(server);
        DiscordWebhook.onServerOpened(null, null, reason);
        notifyStaff(server, MessagesConfig.STAFF_SCHED_FORCE_OPEN, true, "staff", actor);
        NeoForge.EVENT_BUS.post(new ServerScheduleEvent.Opened(server, cause, null, null));
        return true;
    }

    /** Closes the server now and kicks the players who are not exempt. False when cancelled by another mod. */
    public static boolean forceClose(MinecraftServer server, String actor, ServerScheduleEvent.Cause cause) {
        return forceClose(server, actor, cause, 0, "");
    }

    /** Same, for some minutes (0 = until someone changes it) and with a reason, shown to the refused players. */
    public static boolean forceClose(MinecraftServer server, String actor, ServerScheduleEvent.Cause cause, int minutes, String reason) {
        if (!setForceState(server, ServerScheduleEvent.ForceState.FORCE_CLOSED, actor, cause, minutes, reason)) return false;
        setAnnounced(false);
        DiscordWebhook.onServerClosed(reason);
        int kicked = kickNotExempt(server, true);
        notifyStaff(server, MessagesConfig.STAFF_SCHED_FORCE_CLOSE, true, "staff", actor, "count", String.valueOf(kicked));
        NeoForge.EVENT_BUS.post(new ServerScheduleEvent.Closed(server, cause, kicked));
        return true;
    }

    // =========================================================================
    // OPENING, CLOSING
    // =========================================================================
    /** Told to everyone online: the staff always, and the players who were allowed to stay. */
    private static void announceOpened(MinecraftServer server) {
        LocalDateTime now = now(), close = closing();
        server.getPlayerList().broadcastSystemMessage(ColorHelper.parseColors(ScheduleConfig.MSG_SERVER_OPENED.get()
                .replace("{open}", now.format(FMT))
                .replace("{close}", close != null ? close.format(FMT) : "—")
                .replace("{day}", getDayName(null, now.getDayOfWeek()))), false);
    }

    private static int kickNotExempt(MinecraftServer server) { return kickNotExempt(server, false); }

    /** Kicks the players who are not exempt (kickNonStaff, or {@code always}). Returns how many. */
    private static int kickNotExempt(MinecraftServer server, boolean always) {
        if (!always && !Cfg.get(ScheduleConfig.KICK_NON_STAFF, true)) return 0;
        List<ServerPlayer> toKick = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!isScheduleExempt(p)) toKick.add(p);
        }
        if (toKick.isEmpty()) return 0;
        Component message = closedMessage();
        for (ServerPlayer p : toKick) p.connection.disconnect(message);
        RpEssentials.LOGGER.info("[Schedule] Server closed, kicked {} player(s).", toKick.size());
        return toKick.size();
    }

    /** Announces the state when it is not the one last announced (also after a restart). */
    private static void applyState(MinecraftServer server) {
        boolean open = isServerOpen();
        boolean first = firstTick;
        firstTick = false;
        if (!scheduleOn() && getForceState() == ServerScheduleEvent.ForceState.NONE) {
            // No schedule: a server that was closed is open again, nothing else to say.
            if (Boolean.FALSE.equals(state().announcedOpen)) opened(server);
            setAnnounced(null);
            return;
        }
        Boolean told = state().announcedOpen;
        if (told != null && told == open) {
            if (first) {
                RpEssentials.LOGGER.info("[Schedule] Server restarted, still {} to players.", open ? "open" : "closed");
                DiscordWebhook.onServerRestarted(open);
            }
            return;
        }
        if (open) {
            opened(server);
        } else {
            setAnnounced(false);
            notifyStaff(server, MessagesConfig.SCHED_SYS_STAFF_CLOSED, false);
            int kicked = kickNotExempt(server);
            DiscordWebhook.onServerClosed(forceReason());
            NeoForge.EVENT_BUS.post(new ServerScheduleEvent.Closed(server, ServerScheduleEvent.Cause.SCHEDULE, kicked));
        }
    }

    private static void opened(MinecraftServer server) {
        setAnnounced(true);
        LocalDateTime close = closing();
        String from = now().format(FMT), to = close != null ? close.format(FMT) : null;
        announceOpened(server);
        DiscordWebhook.onServerOpened(from, to, "");
        NeoForge.EVENT_BUS.post(new ServerScheduleEvent.Opened(server, ServerScheduleEvent.Cause.SCHEDULE, from, to));
    }

    /**
     * Every 20 seconds: end of a forced state, opening and closing, warnings, then the death hours and HRP hours
     * notifications.
     */
    public static void tick(MinecraftServer server) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            LocalDateTime end = forceEnd();
            if (end != null && seconds(end) <= 0) {
                notifyStaff(server, MessagesConfig.SCHED_FORCE_ENDED, false);
                if (setForceState(server, ServerScheduleEvent.ForceState.NONE, "System", ServerScheduleEvent.Cause.SCHEDULE, 0, "")) {
                    RpEssentials.LOGGER.info("[Schedule] End of the forced state.");
                } else {
                    // Refused by another mod: kept as a forced state without end, not asked again every 20 seconds.
                    state().forceUntil = 0L;
                    saveState();
                }
            }
            applyState(server);
            warnings(server);
        }
        LocalTime time = nowTime();
        tickDeathHoursNotifications(server, time);
        tickHrpNotifications(server, time);
    }

    // =========================================================================
    // WARNINGS
    // =========================================================================
    private static void warnings(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() == 0) return;
        ServerScheduleEvent.ForceState force = getForceState();
        // What will really happen.
        LocalDateTime close = isServerOpen() ? closing() : null;
        if (close != null) {
            final LocalDateTime at = close;
            warn("close", at, minutes -> server.getPlayerList().broadcastSystemMessage(ColorHelper.parseColors(
                    (minutes == 1 ? ScheduleConfig.MSG_CLOSING_IMMINENT : ScheduleConfig.MSG_WARNING).get()
                            .replace("{minutes}", String.valueOf(minutes)).replace("{close}", at.format(FMT))), false));
        }
        if (!scheduleOn() || force == ServerScheduleEvent.ForceState.NONE) return;
        // What would have happened without the forced state.
        LocalDateTime end = forceEnd();
        if (force == ServerScheduleEvent.ForceState.FORCE_OPEN) {
            ScheduleClock.Session s = clock.active(now());
            if (s != null && !s.end().equals(close)) supposed(server, "supposedClose", s.end(), MessagesConfig.SCHED_SUPPOSED_CLOSE);
        } else {
            ScheduleClock.Session s = clock.next(now());
            if (s != null && (end == null || end.isAfter(s.start()))) supposed(server, "supposedOpen", s.start(), MessagesConfig.SCHED_SUPPOSED_OPEN);
        }
    }

    private static void supposed(MinecraftServer server, String kind, LocalDateTime at,
                                 net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> key) {
        warn(kind, at, minutes -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Msg.c(p, key, "minutes", String.valueOf(minutes), "time", at.format(FMT)));
            }
        });
    }

    private static void warn(String kind, LocalDateTime at, java.util.function.IntConsumer send) {
        long remaining = seconds(at) / 60;
        if (remaining < 0) return;
        for (int minutes : ScheduleConfig.WARNING_TIMES.get()) {
            // A 2 minutes window: the check runs every 20 seconds, and the server can lag.
            if (remaining > minutes || remaining <= minutes - 2) continue;
            if (sentWarnings.size() > 200) sentWarnings.clear();
            if (sentWarnings.add(at + "|" + kind + "|" + minutes)) send.accept(minutes);
        }
    }

    // =========================================================================
    // DEATH HOURS / HRP HOURS NOTIFICATIONS
    // =========================================================================
    public static void tickDeathHoursNotifications(MinecraftServer server, LocalTime now) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (!ScheduleConfig.DEATH_HOURS_ENABLED.get()) return;
        } else { return; }

        boolean active = isDeathHour();

        if (!active) { lastDeathHoursSlotKey = ""; return; }

        String currentSlot = null;
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            for (String slot : ScheduleConfig.DEATH_HOURS_SLOTS.get()) {
                if (isInSlot(now, slot)) { currentSlot = slot; break; }
            }
        }

        if (currentSlot == null || currentSlot.equals(lastDeathHoursSlotKey)) return;
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        lastDeathHoursSlotKey = currentSlot;

        if (Cfg.ready(ScheduleConfig.SPEC)) {
            String slots  = String.join(", ", ScheduleConfig.DEATH_HOURS_SLOTS.get());
            String mode   = ServerLang.get(null, MessagesConfig.SCHEDULE_DEATH_HOURS_NOTIFY_MODE);
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                DeathRPManager.sendMessageToPlayer(p, ServerLang.get(p, MessagesConfig.SCHEDULE_DEATH_HOURS_NOTIFY, "slots", slots), mode);
        }
    }

    public static void tickHrpNotifications(MinecraftServer server, LocalTime now) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (!ScheduleConfig.ENABLE_HRP_HOURS.get()) return;
        } else { return; }

        boolean allowed   = isHrpAllowed();
        boolean tolerated = isHrpTolerated();
        if (!allowed && !tolerated) { lastHrpSlotKey = ""; return; }

        String currentSlot = null;
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            List<? extends String> slots = allowed
                    ? ScheduleConfig.HRP_ALLOWED_SLOTS.get()
                    : ScheduleConfig.HRP_TOLERATED_SLOTS.get();
            for (String slot : slots) {
                if (isInSlot(now, slot)) { currentSlot = slot; break; }
            }
        }

        if (currentSlot == null || currentSlot.equals(lastHrpSlotKey)) return;
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        lastHrpSlotKey = currentSlot;

        if (Cfg.ready(ScheduleConfig.SPEC)) {
            String rawMsg = allowed
                    ? ScheduleConfig.HRP_ALLOWED_MESSAGE.get()
                    : ScheduleConfig.HRP_TOLERATED_MESSAGE.get();
            String[] parts = currentSlot.split("-", 2);
            rawMsg = rawMsg
                    .replace("{start}", parts[0].trim())
                    .replace("{end}",   parts.length > 1 ? parts[1].trim() : "?");
            String mode = net.rp.rpessentials.config.ConfigRules.value(ScheduleConfig.HRP_MESSAGE_MODE);
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                DeathRPManager.sendMessageToPlayer(p, rawMsg, mode);
        }
    }

    /** Plage "HH:MM-HH:MM", supporte cross-minuit. */
    public static boolean isInSlot(LocalTime now, String slot) {
        ScheduleClock.Slot s = ScheduleClock.parseSlot(slot);
        if (s == null) {
            RpEssentials.LOGGER.warn("[Schedule] Invalid slot format: '{}'", slot);
            return false;
        }
        if (s.close().isAfter(s.open())) return !now.isBefore(s.open()) && now.isBefore(s.close());
        return !now.isBefore(s.open()) || now.isBefore(s.close());
    }

    // =========================================================================
    // CONFIG PROBLEMS, TIME
    // =========================================================================
    private record ConfigWarning(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> key, String[] args) {}
    private static final List<ConfigWarning> configWarnings = new ArrayList<>();

    /** Schedule config problems, in the viewer's language (null = server language). */
    public static List<String> getConfigWarnings(ServerPlayer viewer) {
        List<String> out = new ArrayList<>();
        for (ConfigWarning w : configWarnings) out.add(ServerLang.get(viewer, w.key(), w.args()));
        return out;
    }

    private static void warnConfig(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> key, String... args) {
        ConfigWarning w = new ConfigWarning(key, args);
        RpEssentials.LOGGER.error("[Schedule] {}", ServerLang.get(null, key, args));
        configWarnings.add(w);
    }

    public static java.time.ZoneId zone() { return TimeHelper.zone(); }

    public static LocalDateTime now() { return TimeHelper.now(); }

    public static LocalTime nowTime() { return TimeHelper.nowTime(); }

    public static LocalDate today() { return TimeHelper.today(); }
}
