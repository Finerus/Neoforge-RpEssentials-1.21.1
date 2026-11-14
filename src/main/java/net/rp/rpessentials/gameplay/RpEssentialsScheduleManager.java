package net.rp.rpessentials.gameplay;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.config.ScheduleConfig;
import net.rp.rpessentials.moderation.DeathRPManager;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.util.TimeHelper;

public class RpEssentialsScheduleManager {

    private static String lastSummary = "";

    // =========================================================================
    // STRUCTURE
    // =========================================================================
    public record DaySchedule(LocalTime open, LocalTime close) {

        /**
         * Returns true if {@code now} falls within this schedule's session.
         * -
         * Two cases:
         *  - Normal    (open < close): 20:00 → 23:59  →  open ≤ now < close
         *  - Cross-midnight (close < open): 22:00 → 02:00  →  now ≥ open  OR  now < close
         */
        public boolean isOpen(LocalTime now) {
            if (!close.isBefore(open)) {
                // Normal same-day session
                return !now.isBefore(open) && now.isBefore(close);
            } else {
                // Cross-midnight session: open side OR close side
                return !now.isBefore(open) || now.isBefore(close);
            }
        }

        /** True when this schedule's session extends past midnight. */
        public boolean crossesMidnight() {
            return close.isBefore(open);
        }

        /**
         * Minutes remaining until this session closes, given the current time.
         * Works for both normal and cross-midnight sessions.
         * Returns a negative value if the session is not open.
         */
        public long minutesUntilClose(LocalTime now) {
            if (!isOpen(now)) return -1;
            if (!crossesMidnight() || !now.isBefore(close)) {
                // Same-day session, or we are on the "before-close" side of a cross-midnight session
                return Duration.between(now, close).toMinutes();
            } else {
                // We are on the "after-open" side of a cross-midnight session (e.g. now=23:30, close=02:00)
                // Time until midnight + time from midnight to close
                long toMidnight = Duration.between(now, LocalTime.MIDNIGHT).toMinutes();
                long fromMidnight = Duration.between(LocalTime.MIDNIGHT, close).toMinutes();
                // toMidnight is 0 or negative at midnight: use modulo 1440
                return Math.floorMod(toMidnight, 1440) + fromMidnight;
            }
        }
    }

    // =========================================================================
    // STATE
    // =========================================================================
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<DayOfWeek, DaySchedule> schedules = new EnumMap<>(DayOfWeek.class);
    /** Dated exceptions. A null value means closed that day. */
    private static final Map<LocalDate, DaySchedule> exceptions = new java.util.TreeMap<>();

    /** Schedule of a given date: its exception if there is one, else the weekly schedule. */
    public static DaySchedule scheduleFor(LocalDate date) {
        if (exceptions.containsKey(date)) return exceptions.get(date);
        return schedules.get(date.getDayOfWeek());
    }

    /** Upcoming exceptions (today included), in date order. A null value means closed. */
    public static Map<LocalDate, DaySchedule> getUpcomingExceptions() {
        Map<LocalDate, DaySchedule> out = new java.util.LinkedHashMap<>();
        LocalDate today = today();
        for (Map.Entry<LocalDate, DaySchedule> e : exceptions.entrySet()) {
            if (!e.getKey().isBefore(today)) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private static void parseExceptions() {
        exceptions.clear();
        for (String raw : ScheduleConfig.SCHEDULE_EXCEPTIONS.get()) {
            String line = raw.trim();
            int eq = line.indexOf('=');
            try {
                if (eq < 0) throw new IllegalArgumentException();
                LocalDate date = LocalDate.parse(line.substring(0, eq).trim());
                String v = line.substring(eq + 1).trim();
                if (v.equalsIgnoreCase("CLOSED")) {
                    exceptions.put(date, null);
                    continue;
                }
                if (v.equalsIgnoreCase("OPEN")) {
                    exceptions.put(date, new DaySchedule(LocalTime.MIDNIGHT, LocalTime.of(23, 59)));
                    continue;
                }
                String[] t = v.split("-");
                if (t.length != 2 || !isValidTimeFormat(t[0].trim()) || !isValidTimeFormat(t[1].trim()))
                    throw new IllegalArgumentException();
                exceptions.put(date, new DaySchedule(LocalTime.parse(t[0].trim(), FMT), LocalTime.parse(t[1].trim(), FMT)));
            } catch (Exception e) {
                warnConfig(MessagesConfig.SCHED_WARN_INVALID_EXCEPTION, "value", line);
            }
        }
    }

    private static final Set<Integer> sentWarnings    = new HashSet<>();
    private static boolean hasClosedToday             = false;
    private static boolean hasOpenedToday             = false;
    private static DayOfWeek lastResetDay             = null;
    private static String lastHrpSlotKey              = "";
    private static String lastDeathHoursSlotKey       = "";
    private static boolean closeNoticeSent = false;

    // =========================================================================
    // RELOAD
    // =========================================================================
    public static void reload() {
        schedules.clear();
        sentWarnings.clear();
        configWarnings.clear();
        TimeHelper.reload();

        if (!Cfg.ready(ScheduleConfig.SPEC)) {
            RpEssentials.LOGGER.debug("[Schedule] Config not loaded yet, skipping reload.");
            return;
        }
        try {
            schedules.put(DayOfWeek.MONDAY,   parseDay(ScheduleConfig.MONDAY_ENABLED,    ScheduleConfig.MONDAY_OPEN,    ScheduleConfig.MONDAY_CLOSE));
            schedules.put(DayOfWeek.TUESDAY,   parseDay(ScheduleConfig.TUESDAY_ENABLED,   ScheduleConfig.TUESDAY_OPEN,   ScheduleConfig.TUESDAY_CLOSE));
            schedules.put(DayOfWeek.WEDNESDAY, parseDay(ScheduleConfig.WEDNESDAY_ENABLED, ScheduleConfig.WEDNESDAY_OPEN, ScheduleConfig.WEDNESDAY_CLOSE));
            schedules.put(DayOfWeek.THURSDAY,  parseDay(ScheduleConfig.THURSDAY_ENABLED,  ScheduleConfig.THURSDAY_OPEN,  ScheduleConfig.THURSDAY_CLOSE));
            schedules.put(DayOfWeek.FRIDAY,    parseDay(ScheduleConfig.FRIDAY_ENABLED,    ScheduleConfig.FRIDAY_OPEN,    ScheduleConfig.FRIDAY_CLOSE));
            schedules.put(DayOfWeek.SATURDAY,  parseDay(ScheduleConfig.SATURDAY_ENABLED,  ScheduleConfig.SATURDAY_OPEN,  ScheduleConfig.SATURDAY_CLOSE));
            schedules.put(DayOfWeek.SUNDAY,    parseDay(ScheduleConfig.SUNDAY_ENABLED,    ScheduleConfig.SUNDAY_OPEN,    ScheduleConfig.SUNDAY_CLOSE));
            parseExceptions();

            StringBuilder sb = new StringBuilder();
            for (DayOfWeek day : DayOfWeek.values()) {
                DaySchedule s = schedules.get(day);
                sb.append(day).append(s == null ? " CLOSED" : " " + s.open().format(FMT) + "-" + s.close().format(FMT)
                        + (s.crossesMidnight() ? "*" : "")).append("; ");
            }
            String summary = sb.toString();
            if (!summary.equals(lastSummary)) {
                lastSummary = summary;
                RpEssentials.LOGGER.info("[Schedule] {}", summary);
            }

            net.minecraft.server.MinecraftServer srv = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    if (!RpEssentialsPermissions.isStaff(p)) continue;
                    for (String w : getConfigWarnings(p)) p.sendSystemMessage(Component.literal(
                            ServerLang.get(p, MessagesConfig.SCHED_SYS_CONFIG_WARNING, "warning", w)));
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[Schedule] Error parsing schedule: {}", e.getMessage());
        }
    }

    private static DaySchedule parseDay(
            net.neoforged.neoforge.common.ModConfigSpec.BooleanValue enabled,
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> open,
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> close) {
        if (!enabled.get()) return null;
        String openStr = open.get();
        String closeStr = close.get();
        if (!isValidTimeFormat(openStr)) {
            warnConfig(MessagesConfig.SCHED_WARN_INVALID_OPEN, "day", String.join(".", open.getPath()), "value", openStr);
            return null;
        }
        if (!isValidTimeFormat(closeStr)) {
            warnConfig(MessagesConfig.SCHED_WARN_INVALID_CLOSE, "day", String.join(".", close.getPath()), "value", closeStr);
            return null;
        }
        return new DaySchedule(LocalTime.parse(openStr, FMT), LocalTime.parse(closeStr, FMT));
    }

    private static boolean isValidTimeFormat(String time) {
        if (time == null) return false;
        return time.matches("^([01]\\d|2[0-3]):[0-5]\\d$");
    }

    // =========================================================================
    // API PUBLIQUE
    // =========================================================================
    /**
     * Returns the active schedule for the current moment.
     *
     * Priority:
     *  1. Today's schedule, if its session is currently open.
     *  2. Yesterday's schedule, if it crosses midnight and is still open now.
     *  3. null — no session is active.
     */
    public static DaySchedule getActiveSchedule() {
        LocalTime now   = nowTime();
        LocalDate todayDate = today();

        // Check today first
        DaySchedule todayS = scheduleFor(todayDate);
        // An overnight session (22:00 -> 02:00) only belongs to today from its opening time;
        // before midnight-close it is yesterday's session.
        if (todayS != null && todayS.isOpen(now)
                && (!todayS.crossesMidnight() || !now.isBefore(todayS.open()))) return todayS;

        // Check yesterday (cross-midnight session that started the day before)
        DaySchedule yesterdayS = scheduleFor(todayDate.minusDays(1));
        if (yesterdayS != null && yesterdayS.crossesMidnight() && yesterdayS.isOpen(now))
            return yesterdayS;

        return null;
    }

    public static DaySchedule getTodaySchedule() {
        return scheduleFor(today());
    }

    public static Map<DayOfWeek, DaySchedule> getSchedules() {
        return Collections.unmodifiableMap(schedules);
    }

    public static boolean isScheduleExempt(ServerPlayer player) {
        if (RpEssentialsPermissions.isStaff(player)) return true;
        if (player.getServer() != null && player.getServer().isSingleplayerOwner(player.getGameProfile())) return true;
        if (RpEssentialsRoleManager.has(player, RpEssentialsRoleManager.Permission.SCHEDULE_WHITELIST)) return true;
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            return ScheduleConfig.SCHEDULE_WHITELIST.get().contains(player.getGameProfile().getName());
        } else {
            return false;
        }
    }

    public static Component canPlayerJoin(ServerPlayer player) {
        if (isScheduleExempt(player)) return null;

        if (Cfg.ready(ScheduleConfig.SPEC)) {
            String state = ScheduleConfig.FORCE_STATE.get();
            if ("FORCE_OPEN".equals(state))   return null;
            if ("FORCE_CLOSED".equals(state))
                return ColorHelper.parseColors(ScheduleConfig.FORCE_CLOSED_MESSAGE.get());
            if (!ScheduleConfig.ENABLE_SCHEDULE.get()) return null;
        } else {
            return null;
        }
        if (isServerOpen()) return null;

        DaySchedule next = getNextOpenSchedule();
        String open  = next != null ? next.open().format(FMT)  : "?";
        String close = next != null ? next.close().format(FMT) : "?";
        String day   = getNextOpenDayName(player);
        String msg;
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            msg = ScheduleConfig.MSG_SERVER_CLOSED.get()
                    .replace("{open}",  open)
                    .replace("{close}", close)
                    .replace("{day}",   day);
        } else {
            msg = ScheduleConfig.MSG_SERVER_CLOSED.getDefault().replace("{open}", open).replace("{close}", close).replace("{day}", day);
        }
        return ColorHelper.parseColors(msg);
    }

    /**
     * Returns true when the server is currently open.
     * Handles cross-midnight sessions transparently via {@link #getActiveSchedule()}.
     */
    public static boolean isServerOpen() {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            String state = ScheduleConfig.FORCE_STATE.get();
            if ("FORCE_OPEN".equals(state))   return true;
            if ("FORCE_CLOSED".equals(state)) return false;
            if (!ScheduleConfig.ENABLE_SCHEDULE.get()) return true;
        } else {
            return true;
        }
        if (schedules.isEmpty()) return true;
        return getActiveSchedule() != null;
    }

    public static String getTimeUntilNextEvent() {
        return getTimeUntilNextEvent(null);
    }

    /** Short status ("Open: closing in 2h05"...) in the viewer's language. */
    public static String getTimeUntilNextEvent(ServerPlayer v) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get())
                return ServerLang.get(v, MessagesConfig.SCHED_NEXT_DISABLED);
        } else {
            return ServerLang.get(v, MessagesConfig.SCHED_NEXT_NOT_INIT);
        }
        if (schedules.isEmpty()) return ServerLang.get(v, MessagesConfig.SCHED_NEXT_NOT_INIT);

        DaySchedule active = getActiveSchedule();
        LocalTime now = nowTime();

        if (active != null) {
            long min = active.minutesUntilClose(now);
            if (min < 0) min = 0;
            return ServerLang.get(v, MessagesConfig.SCHED_NEXT_OPEN,
                    "h", String.valueOf(min / 60), "min", String.format("%02d", min % 60));
        }

        // Find next opening
        NextOpening n = findNextOpening();
        if (n == null) return ServerLang.get(v, MessagesConfig.SCHED_NEXT_NONE);
        return ServerLang.get(v, MessagesConfig.SCHED_NEXT_CLOSED,
                "day", n.today() ? ServerLang.get(v, MessagesConfig.SCHED_TODAY) : getDayName(v, n.day()),
                "time", n.schedule().open().format(FMT));
    }

    public static void enforceOnline(MinecraftServer server) {
        if (server == null) return;
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get() && "NONE".equals(ScheduleConfig.FORCE_STATE.get())) return;
        } else { return; }
        if (isServerOpen()) return;
        closeServer(server, false);
    }

    public static void enforceOnlineDelayed(MinecraftServer server) {
        if (server == null) return;
        CompletableFuture.runAsync(() -> server.execute(() -> enforceOnline(server)),
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }

    // =========================================================================
    // ACCESSEURS D'ÉTAT
    // =========================================================================
    public static boolean hasOpenedToday() { return hasOpenedToday; }
    public static boolean hasClosedToday() { return hasClosedToday; }

    public static void markOpenedToday() { hasOpenedToday = true; hasClosedToday = false; closeNoticeSent = false; }

    public static void resetDailyFlags() {
        hasClosedToday = false;
        hasOpenedToday = false;
        closeNoticeSent = false;
        sentWarnings.clear();
    }

    public static void markClosedToday()   { hasClosedToday = true; }

    // =========================================================================
    // TICK MIDNIGHT — reset flags (every day)
    // =========================================================================
    public static void tickMidnightSweep(MinecraftServer server) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get()) return;
        } else {
            return;
        }
        DayOfWeek today = today().getDayOfWeek();
        if (today.equals(lastResetDay)) return;

        LocalTime now = nowTime();

        lastResetDay          = today;
        sentWarnings.clear();
        lastHrpSlotKey        = "";
        lastDeathHoursSlotKey = "";

        // Only reset open/close flags if yesterday's schedule does NOT cross midnight
        // (if it does, the session is still ongoing and we must not prematurely reset).
        DaySchedule yesterdayS = scheduleFor(today().minusDays(1));
        boolean midnightSessionOngoing = yesterdayS != null
                && yesterdayS.crossesMidnight()
                && yesterdayS.isOpen(now);

        // Both flags are reset every day. While yesterday's overnight session is still running the
        // active schedule is yesterday's, so today's opening is only announced when it really starts,
        // and the close at the end of the overnight session still kicks players.
        hasClosedToday = false;
        hasOpenedToday = false;
        RpEssentials.LOGGER.info(midnightSessionOngoing
                ? "[Schedule] Daily flags reset for {} (overnight session still running)"
                : "[Schedule] Daily flags reset for {}", today);
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
    // TICK MÉTHODES — appelées depuis RpEssentials.onServerTick
    // =========================================================================
    /**
     * Sends closing warnings.
     * Uses {@code minutesUntilClose()} so it works for both normal and cross-midnight sessions.
     */
    public static void checkWarnings(MinecraftServer server, LocalTime now, DaySchedule s) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            long remaining = s.minutesUntilClose(now);
            if (remaining < 0) return;

            for (int minutes : ScheduleConfig.WARNING_TIMES.get()) {
                // Fire the warning when we enter the target minute
                // (remaining is checked against a 2-minute window to tolerate tick granularity)
                if (remaining <= minutes && remaining > minutes - 2 && !sentWarnings.contains(minutes)) {
                    sentWarnings.add(minutes);
                    String msg = (minutes == 1)
                            ? ScheduleConfig.MSG_CLOSING_IMMINENT.get()
                            .replace("{minutes}", String.valueOf(minutes))
                            .replace("{close}",   s.close().format(FMT))
                            : ScheduleConfig.MSG_WARNING.get()
                            .replace("{minutes}", String.valueOf(minutes))
                            .replace("{close}",   s.close().format(FMT));
                    server.getPlayerList().broadcastSystemMessage(ColorHelper.parseColors(msg), false);
                }
            }
        }
    }

    public static void sendOpeningMessage(MinecraftServer server, DaySchedule s) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            String msg = ScheduleConfig.MSG_SERVER_OPENED.get()
                    .replace("{open}",  s.open().format(FMT))
                    .replace("{close}", s.close().format(FMT))
                    .replace("{day}", RpEssentialsScheduleManager.getDayName(
                            RpEssentialsScheduleManager.today().getDayOfWeek()));
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (RpEssentialsPermissions.isStaff(p))
                    p.sendSystemMessage(ColorHelper.parseColors(msg));
        }
    }


    public static void closeServer(MinecraftServer server) { closeServer(server, true); }

    public static void closeServer(MinecraftServer server, boolean announceToStaff) {
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if ("FORCE_OPEN".equals(ScheduleConfig.FORCE_STATE.get())) return;
            if (!ScheduleConfig.KICK_NON_STAFF.get()) return;
        } else {
            return;
        }
        DaySchedule next = getNextOpenSchedule();
        String open  = next != null ? next.open().format(FMT)  : "?";
        String close = next != null ? next.close().format(FMT) : "?";
        String day   = getNextOpenDayName();

        String kickMsg;
        if (Cfg.ready(ScheduleConfig.SPEC)) {
            kickMsg = ScheduleConfig.MSG_SERVER_CLOSED.get()
                    .replace("{open}",  open)
                    .replace("{close}", close)
                    .replace("{day}",   day);
        } else {
            kickMsg = ScheduleConfig.MSG_SERVER_CLOSED.getDefault().replace("{open}", open).replace("{close}", close).replace("{day}", day);
        }
        String finalKickMsg = kickMsg;
        boolean announce = announceToStaff && !closeNoticeSent;
        List<ServerPlayer> toKick = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!isScheduleExempt(p)) toKick.add(p);
            else if (announce && RpEssentialsPermissions.isStaff(p))
                p.sendSystemMessage(Component.literal(ServerLang.get(p, MessagesConfig.SCHED_SYS_STAFF_CLOSED)));
        }
        if (announce) closeNoticeSent = true;
        for (ServerPlayer p : toKick) {
            p.connection.disconnect(ColorHelper.parseColors(finalKickMsg));
            RpEssentials.LOGGER.info("[Schedule] Kicked {} (server closed)", p.getName().getString());
        }
        if (announceToStaff || !toKick.isEmpty()) {
            sentWarnings.clear();
            RpEssentials.LOGGER.info("[Schedule] Server closed, kicked {} player(s).", toKick.size());
        }
    }

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
            String mode = ScheduleConfig.HRP_MESSAGE_MODE.get();
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                DeathRPManager.sendMessageToPlayer(p, rawMsg, mode);
        }
    }

    // =========================================================================
    // UTILITAIRES PRIVÉS
    // =========================================================================
    private record NextOpening(DayOfWeek day, DaySchedule schedule, boolean today) {}

    private static NextOpening findNextOpening() {
        LocalTime now = nowTime();
        LocalDate todayDate = today();
        // Look a bit further than a week: a closed exception can push the next opening.
        for (int i = 0; i <= 31; i++) {
            LocalDate date = todayDate.plusDays(i);
            DayOfWeek day = date.getDayOfWeek();
            DaySchedule s = scheduleFor(date);
            if (s == null) continue;
            if (i == 0 && !now.isBefore(s.open())) continue;
            return new NextOpening(day, s, i == 0);
        }
        return null;
    }

    private static DaySchedule getNextOpenSchedule() {
        NextOpening n = findNextOpening();
        return n != null ? n.schedule() : null;
    }

    private static String getNextOpenDayName() {
        return getNextOpenDayName(null);
    }

    private static String getNextOpenDayName(ServerPlayer v) {
        NextOpening n = findNextOpening();
        return n != null ? getDayName(v, n.day()) : ServerLang.get(v, MessagesConfig.UI_NA);
    }

    // =====================================================================================
    // UTILITAIRE — parsing de plage "HH:MM-HH:MM", supporte cross-minuit, fuseau horaire
    // =====================================================================================
    public static boolean isInSlot(LocalTime now, String slot) {
        if (slot == null || !slot.contains("-")) return false;
        String[] p = slot.split("-", 2);
        try {
            LocalTime start = LocalTime.parse(p[0].trim(), FMT);
            LocalTime end   = LocalTime.parse(p[1].trim(), FMT);
            // Reuse DaySchedule logic for consistency
            return new DaySchedule(start, end).isOpen(now);
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[Schedule] Invalid slot format: '{}'", slot);
            return false;
        }
    }

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

    public static LocalTime nowTime() { return TimeHelper.nowTime(); }

    public static LocalDate today() { return TimeHelper.today(); }
}