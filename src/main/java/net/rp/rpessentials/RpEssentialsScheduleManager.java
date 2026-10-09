package net.rp.rpessentials;

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

public class RpEssentialsScheduleManager {

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

    private static final Set<Integer> sentWarnings    = new HashSet<>();
    private static boolean hasClosedToday             = false;
    private static boolean hasOpenedToday             = false;
    private static DayOfWeek lastResetDay             = null;
    private static String lastHrpSlotKey              = "";
    private static String lastDeathHoursSlotKey       = "";

    // =========================================================================
    // RELOAD
    // =========================================================================
    public static void reload() {
        schedules.clear();
        sentWarnings.clear();

        try {
            schedules.put(DayOfWeek.MONDAY,    parseDay(ScheduleConfig.MONDAY_ENABLED,    ScheduleConfig.MONDAY_OPEN,    ScheduleConfig.MONDAY_CLOSE));
            schedules.put(DayOfWeek.TUESDAY,   parseDay(ScheduleConfig.TUESDAY_ENABLED,   ScheduleConfig.TUESDAY_OPEN,   ScheduleConfig.TUESDAY_CLOSE));
            schedules.put(DayOfWeek.WEDNESDAY, parseDay(ScheduleConfig.WEDNESDAY_ENABLED, ScheduleConfig.WEDNESDAY_OPEN, ScheduleConfig.WEDNESDAY_CLOSE));
            schedules.put(DayOfWeek.THURSDAY,  parseDay(ScheduleConfig.THURSDAY_ENABLED,  ScheduleConfig.THURSDAY_OPEN,  ScheduleConfig.THURSDAY_CLOSE));
            schedules.put(DayOfWeek.FRIDAY,    parseDay(ScheduleConfig.FRIDAY_ENABLED,    ScheduleConfig.FRIDAY_OPEN,    ScheduleConfig.FRIDAY_CLOSE));
            schedules.put(DayOfWeek.SATURDAY,  parseDay(ScheduleConfig.SATURDAY_ENABLED,  ScheduleConfig.SATURDAY_OPEN,  ScheduleConfig.SATURDAY_CLOSE));
            schedules.put(DayOfWeek.SUNDAY,    parseDay(ScheduleConfig.SUNDAY_ENABLED,    ScheduleConfig.SUNDAY_OPEN,    ScheduleConfig.SUNDAY_CLOSE));

            for (DayOfWeek day : DayOfWeek.values()) {
                DaySchedule s = schedules.get(day);
                if (s == null)
                    RpEssentials.LOGGER.info("[Schedule]   {} → CLOSED", day);
                else if (s.crossesMidnight())
                    RpEssentials.LOGGER.info("[Schedule]   {} → {}–{} (cross-midnight)", day, s.open().format(FMT), s.close().format(FMT));
                else
                    RpEssentials.LOGGER.info("[Schedule]   {} → {}–{}", day, s.open().format(FMT), s.close().format(FMT));
            }

            net.minecraft.server.MinecraftServer srv = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    if (!RpEssentialsPermissions.isStaff(p)) continue;
                    for (String w : configWarnings) p.sendSystemMessage(Component.literal("§c[Schedule] " + w));
                }
            }
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.debug("[Schedule] Config not built yet, skipping reload.");
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
            warnConfig("Invalid open time '" + openStr + "' (expected HH:MM): day disabled.");
            return null;
        }
        if (!isValidTimeFormat(closeStr)) {
            warnConfig("Invalid close time '" + closeStr + "' (expected HH:MM): day disabled.");
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
        LocalTime now   = LocalTime.now();
        DayOfWeek today = LocalDate.now().getDayOfWeek();

        // Check today first
        DaySchedule todayS = schedules.get(today);
        if (todayS != null && todayS.isOpen(now)) return todayS;

        // Check yesterday (cross-midnight session that started the day before)
        DayOfWeek yesterday = today.minus(1);
        DaySchedule yesterdayS = schedules.get(yesterday);
        if (yesterdayS != null && yesterdayS.crossesMidnight() && yesterdayS.isOpen(now))
            return yesterdayS;

        return null;
    }

    public static DaySchedule getTodaySchedule() {
        return schedules.get(LocalDate.now().getDayOfWeek());
    }

    public static Map<DayOfWeek, DaySchedule> getSchedules() {
        return Collections.unmodifiableMap(schedules);
    }

    public static boolean isScheduleExempt(ServerPlayer player) {
        if (RpEssentialsPermissions.isStaff(player)) return true;
        if (player.getServer() != null && player.getServer().isSingleplayerOwner(player.getGameProfile())) return true;
        if (RpEssentialsRoleManager.has(player, RpEssentialsRoleManager.Permission.SCHEDULE_WHITELIST)) return true;
        try {
            return ScheduleConfig.SCHEDULE_WHITELIST.get().contains(player.getGameProfile().getName());
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static Component canPlayerJoin(ServerPlayer player) {
        if (isScheduleExempt(player)) return null;

        try {
            String state = ScheduleConfig.FORCE_STATE.get();
            if ("FORCE_OPEN".equals(state))   return null;
            if ("FORCE_CLOSED".equals(state))
                return ColorHelper.parseColors(ScheduleConfig.FORCE_CLOSED_MESSAGE.get());
            if (!ScheduleConfig.ENABLE_SCHEDULE.get()) return null;
        } catch (IllegalStateException e) {
            return null;
        }
        if (isServerOpen()) return null;

        DaySchedule next = getNextOpenSchedule();
        String open  = next != null ? next.open().format(FMT)  : "?";
        String close = next != null ? next.close().format(FMT) : "?";
        String day   = getNextOpenDayName();
        String msg;
        try {
            msg = ScheduleConfig.MSG_SERVER_CLOSED.get()
                    .replace("{open}",  open)
                    .replace("{close}", close)
                    .replace("{day}",   day);
        } catch (IllegalStateException e) {
            msg = "§cThe server is currently closed.";
        }
        return ColorHelper.parseColors(msg);
    }

    /**
     * Returns true when the server is currently open.
     * Handles cross-midnight sessions transparently via {@link #getActiveSchedule()}.
     */
    public static boolean isServerOpen() {
        try {
            String state = ScheduleConfig.FORCE_STATE.get();
            if ("FORCE_OPEN".equals(state))   return true;
            if ("FORCE_CLOSED".equals(state)) return false;
            if (!ScheduleConfig.ENABLE_SCHEDULE.get()) return true;
        } catch (IllegalStateException e) {
            return true;
        }
        if (schedules.isEmpty()) return true;
        return getActiveSchedule() != null;
    }

    public static String getTimeUntilNextEvent() {
        try {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get())
                return "Schedule disabled: server always open.";
        } catch (IllegalStateException e) {
            return "Schedule not initialized";
        }
        if (schedules.isEmpty()) return "Schedule not initialized";

        DaySchedule active = getActiveSchedule();
        LocalTime now = LocalTime.now();

        if (active != null) {
            long min = active.minutesUntilClose(now);
            if (min < 0) min = 0;
            return String.format("Open: closing in %dh%02d", min / 60, min % 60);
        }

        // Find next opening
        NextOpening n = findNextOpening();
        if (n == null) return "Closed: no open day configured";
        return String.format("Closed: next open: %s at %s",
                n.today() ? "today" : n.day().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                n.schedule().open().format(FMT));
    }

    public static void enforceOnline(MinecraftServer server) {
        if (server == null) return;
        try {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get() && "NONE".equals(ScheduleConfig.FORCE_STATE.get())) return;
        } catch (IllegalStateException e) { return; }
        if (isServerOpen()) return;
        closeServer(server);
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
    public static void markOpenedToday()   { hasOpenedToday = true; }

    public static void resetDailyFlags() {
        hasClosedToday = false;
        hasOpenedToday = false;
        sentWarnings.clear();
    }

    public static void markClosedToday()   { hasClosedToday = true; }

    // =========================================================================
    // TICK MIDNIGHT — reset flags (every day)
    // =========================================================================
    public static void tickMidnightSweep(MinecraftServer server) {
        try {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get()) return;
        } catch (IllegalStateException e) {
            return;
        }
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        if (today.equals(lastResetDay)) return;

        LocalTime now = LocalTime.now();

        lastResetDay          = today;
        sentWarnings.clear();
        lastHrpSlotKey        = "";
        lastDeathHoursSlotKey = "";

        // Only reset open/close flags if yesterday's schedule does NOT cross midnight
        // (if it does, the session is still ongoing and we must not prematurely reset).
        DayOfWeek yesterday = today.minus(1);
        DaySchedule yesterdayS = schedules.get(yesterday);
        boolean midnightSessionOngoing = yesterdayS != null
                && yesterdayS.crossesMidnight()
                && yesterdayS.isOpen(now);

        if (!midnightSessionOngoing) {
            hasClosedToday = false;
            hasOpenedToday = false;
            RpEssentials.LOGGER.info("[Schedule] Daily flags reset for {}", today);
        } else {
            // Don't reset — the overnight session is still active.
            // Only clear sentWarnings so closing warnings for today's close time can fire.
            RpEssentials.LOGGER.info("[Schedule] Cross-midnight session ongoing at midnight: flags NOT reset for {}", today);
        }
    }

    public static String getDayName(java.time.DayOfWeek day) {
        try {
            return switch (day) {
                case MONDAY    -> MessagesConfig.SCHEDULE_DAY_MONDAY.get();
                case TUESDAY   -> MessagesConfig.SCHEDULE_DAY_TUESDAY.get();
                case WEDNESDAY -> MessagesConfig.SCHEDULE_DAY_WEDNESDAY.get();
                case THURSDAY  -> MessagesConfig.SCHEDULE_DAY_THURSDAY.get();
                case FRIDAY    -> MessagesConfig.SCHEDULE_DAY_FRIDAY.get();
                case SATURDAY  -> MessagesConfig.SCHEDULE_DAY_SATURDAY.get();
                case SUNDAY    -> MessagesConfig.SCHEDULE_DAY_SUNDAY.get();
            };
        } catch (IllegalStateException e) {
            return day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        }
    }

    // =========================================================================
    // DEATH HOURS / HRP HOURS
    // =========================================================================
    public static boolean isDeathHour() {
        try {
            if (!ScheduleConfig.DEATH_HOURS_ENABLED.get()) return false;
            LocalTime now = LocalTime.now();
            for (String slot : ScheduleConfig.DEATH_HOURS_SLOTS.get())
                if (isInSlot(now, slot)) return true;
        } catch (IllegalStateException ignored) {}
        return false;
    }

    public static boolean isHrpTolerated() {
        try {
            if (!ScheduleConfig.ENABLE_HRP_HOURS.get()) return false;
            LocalTime now = LocalTime.now();
            for (String slot : ScheduleConfig.HRP_TOLERATED_SLOTS.get())
                if (isInSlot(now, slot)) return true;
        } catch (IllegalStateException ignored) {}
        return false;
    }

    public static boolean isHrpAllowed() {
        try {
            if (!ScheduleConfig.ENABLE_HRP_HOURS.get()) return false;
            LocalTime now = LocalTime.now();
            for (String slot : ScheduleConfig.HRP_ALLOWED_SLOTS.get())
                if (isInSlot(now, slot)) return true;
        } catch (IllegalStateException ignored) {}
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
        try {
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
        } catch (IllegalStateException ignored) {}
    }

    public static void sendOpeningMessage(MinecraftServer server, DaySchedule s) {
        try {
            String msg = ScheduleConfig.MSG_SERVER_OPENED.get()
                    .replace("{open}",  s.open().format(FMT))
                    .replace("{close}", s.close().format(FMT))
                    .replace("{day}", RpEssentialsScheduleManager.getDayName(
                            java.time.LocalDate.now().getDayOfWeek()));
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (RpEssentialsPermissions.isStaff(p))
                    p.sendSystemMessage(ColorHelper.parseColors(msg));
        } catch (IllegalStateException ignored) {}
    }


    public static void closeServer(MinecraftServer server) { closeServer(server, true); }

    public static void closeServer(MinecraftServer server, boolean announceToStaff) {
        try {
            if ("FORCE_OPEN".equals(ScheduleConfig.FORCE_STATE.get())) return;
            if (!ScheduleConfig.KICK_NON_STAFF.get()) return;
        } catch (IllegalStateException e) {
            return;
        }
        DaySchedule next = getNextOpenSchedule();
        String open  = next != null ? next.open().format(FMT)  : "?";
        String close = next != null ? next.close().format(FMT) : "?";
        String day   = getNextOpenDayName();

        String kickMsg;
        try {
            kickMsg = ScheduleConfig.MSG_SERVER_CLOSED.get()
                    .replace("{open}",  open)
                    .replace("{close}", close)
                    .replace("{day}",   day);
        } catch (IllegalStateException e) {
            kickMsg = "§cThe server is now closed.";
        }
        String finalKickMsg = kickMsg;
        List<ServerPlayer> toKick = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!isScheduleExempt(p)) toKick.add(p);
            else if (announceToStaff && RpEssentialsPermissions.isStaff(p))
                p.sendSystemMessage(Component.literal("§6[STAFF] Server closed: you may remain connected."));
        }
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
        try {
            if (!ScheduleConfig.DEATH_HOURS_ENABLED.get()) return;
        } catch (IllegalStateException e) { return; }

        boolean active = isDeathHour();

        if (!active) { lastDeathHoursSlotKey = ""; return; }

        String currentSlot = null;
        try {
            for (String slot : ScheduleConfig.DEATH_HOURS_SLOTS.get()) {
                if (isInSlot(now, slot)) { currentSlot = slot; break; }
            }
        } catch (IllegalStateException ignored) {}

        if (currentSlot == null || currentSlot.equals(lastDeathHoursSlotKey)) return;
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        lastDeathHoursSlotKey = currentSlot;

        try {
            String slots  = String.join(", ", ScheduleConfig.DEATH_HOURS_SLOTS.get());
            String rawMsg = MessagesConfig.get(MessagesConfig.SCHEDULE_DEATH_HOURS_NOTIFY, "slots", slots);
            String mode   = MessagesConfig.get(MessagesConfig.SCHEDULE_DEATH_HOURS_NOTIFY_MODE);
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                DeathRPManager.sendMessageToPlayer(p, rawMsg, mode);
        } catch (IllegalStateException ignored) {}
    }

    public static void tickHrpNotifications(MinecraftServer server, LocalTime now) {
        try {
            if (!ScheduleConfig.ENABLE_HRP_HOURS.get()) return;
        } catch (IllegalStateException e) { return; }

        boolean allowed   = isHrpAllowed();
        boolean tolerated = isHrpTolerated();
        if (!allowed && !tolerated) { lastHrpSlotKey = ""; return; }

        String currentSlot = null;
        try {
            List<? extends String> slots = allowed
                    ? ScheduleConfig.HRP_ALLOWED_SLOTS.get()
                    : ScheduleConfig.HRP_TOLERATED_SLOTS.get();
            for (String slot : slots) {
                if (isInSlot(now, slot)) { currentSlot = slot; break; }
            }
        } catch (IllegalStateException ignored) {}

        if (currentSlot == null || currentSlot.equals(lastHrpSlotKey)) return;
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        lastHrpSlotKey = currentSlot;

        try {
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
        } catch (IllegalStateException ignored) {}
    }

    // =========================================================================
    // UTILITAIRES PRIVÉS
    // =========================================================================
    private record NextOpening(DayOfWeek day, DaySchedule schedule, boolean today) {}

    private static NextOpening findNextOpening() {
        LocalTime now = LocalTime.now();
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        for (int i = 0; i <= 7; i++) {
            DayOfWeek day = today.plus(i);
            DaySchedule s = schedules.get(day);
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
        NextOpening n = findNextOpening();
        return n != null ? getDayName(n.day()) : "N/A";
    }

    // =========================================================================
    // UTILITAIRE — parsing de plage "HH:MM-HH:MM", supporte cross-minuit
    // =========================================================================
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

    private static final List<String> configWarnings = new ArrayList<>();
    public static List<String> getConfigWarnings() { return new ArrayList<>(configWarnings); }

    private static void warnConfig(String msg) {
        RpEssentials.LOGGER.error("[Schedule] {}", msg);
        configWarnings.add(msg);
    }
}