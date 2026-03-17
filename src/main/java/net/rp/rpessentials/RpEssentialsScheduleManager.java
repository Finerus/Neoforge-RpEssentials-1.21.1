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

public class RpEssentialsScheduleManager {

    // =========================================================================
    // STRUCTURE
    // =========================================================================

    public record DaySchedule(LocalTime open, LocalTime close) {
        public boolean isOpen(LocalTime now) {
            return !now.isBefore(open) && now.isBefore(close);
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
        hasClosedToday = false;
        hasOpenedToday = false;

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
                if (s == null) RpEssentials.LOGGER.info("[Schedule]   {} → CLOSED", day);
                else           RpEssentials.LOGGER.info("[Schedule]   {} → {}–{}", day, s.open().format(FMT), s.close().format(FMT));
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
        try {
            return new DaySchedule(LocalTime.parse(open.get(), FMT), LocalTime.parse(close.get(), FMT));
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[Schedule] Invalid time format — day disabled. Error: {}", e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // API PUBLIQUE
    // =========================================================================

    public static DaySchedule getTodaySchedule() {
        return schedules.get(LocalDate.now().getDayOfWeek());
    }

    public static Map<DayOfWeek, DaySchedule> getSchedules() {
        return Collections.unmodifiableMap(schedules);
    }

    public static Component canPlayerJoin(ServerPlayer player) {
        try {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get()) return null;
        } catch (IllegalStateException e) {
            return null;
        }
        if (RpEssentialsPermissions.isStaff(player)) return null;
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

    public static boolean isServerOpen() {
        try {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get()) return true;
        } catch (IllegalStateException e) {
            return true;
        }
        if (schedules.isEmpty()) return true;
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        DaySchedule s   = schedules.get(today);
        if (s == null) return false;
        return s.isOpen(LocalTime.now());
    }

    public static String getTimeUntilNextEvent() {
        try {
            if (!ScheduleConfig.ENABLE_SCHEDULE.get())
                return "Schedule disabled — server always open.";
        } catch (IllegalStateException e) {
            return "Schedule not initialized";
        }
        if (schedules.isEmpty()) return "Schedule not initialized";

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        DaySchedule s   = schedules.get(today);
        LocalTime now   = LocalTime.now();

        if (s != null && s.isOpen(now)) {
            long min = Duration.between(now, s.close()).toMinutes();
            if (min < 0) min += 24 * 60;
            return String.format("Open — closing in %dh%02d", min / 60, min % 60);
        }
        for (int i = 1; i <= 7; i++) {
            DayOfWeek next = today.plus(i);
            DaySchedule ns = schedules.get(next);
            if (ns != null)
                return String.format("Closed — next open: %s at %s",
                        next.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        ns.open().format(FMT));
        }
        return "Closed — no open day configured";
    }

    // =========================================================================
    // ACCESSEURS D'ÉTAT — utilisés par RpEssentials.onServerTick
    // =========================================================================

    public static boolean hasOpenedToday() { return hasOpenedToday; }
    public static boolean hasClosedToday() { return hasClosedToday; }
    public static void markOpenedToday()   { hasOpenedToday = true; }
    public static void markClosedToday()   { hasClosedToday = true; }

    // =========================================================================
    // TICK MIDNIGHT — appelé toutes les 1200 ticks depuis RpEssentials
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
        if (now.getHour() == 0 && now.getMinute() <= 1) {
            lastResetDay          = today;
            hasClosedToday        = false;
            hasOpenedToday        = false;
            sentWarnings.clear();
            lastHrpSlotKey        = "";
            lastDeathHoursSlotKey = "";
            RpEssentials.LOGGER.info("[Schedule] Daily flags reset for {}", today);
        }
    }

    // =========================================================================
    // DEATH HOURS
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

    // =========================================================================
    // HRP HOURS
    // =========================================================================

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
    // MÉTHODES DE TICK — appelées depuis RpEssentials.onServerTick
    // =========================================================================

    public static void checkWarnings(MinecraftServer server, LocalTime now, DaySchedule s) {
        try {
            for (int minutes : ScheduleConfig.WARNING_TIMES.get()) {
                LocalTime warnTime = s.close().minusMinutes(minutes);
                if (now.getHour()   == warnTime.getHour()
                        && now.getMinute() == warnTime.getMinute()
                        && !sentWarnings.contains(minutes)) {
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
                    .replace("{day}",   LocalDate.now().getDayOfWeek()
                            .getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (RpEssentialsPermissions.isStaff(p))
                    p.sendSystemMessage(ColorHelper.parseColors(msg));
        } catch (IllegalStateException ignored) {}
    }

    public static void closeServer(MinecraftServer server) {
        try {
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
            if (!RpEssentialsPermissions.isStaff(p)) toKick.add(p);
            else p.sendSystemMessage(Component.literal("§6[STAFF] Server closed — you may remain connected."));
        }
        for (ServerPlayer p : toKick) {
            p.connection.disconnect(ColorHelper.parseColors(finalKickMsg));
            RpEssentials.LOGGER.info("[Schedule] Kicked {} (server closed)", p.getName().getString());
        }
        sentWarnings.clear();
        RpEssentials.LOGGER.info("[Schedule] Server closed, kicked {} player(s).", toKick.size());
    }

    public static void tickDeathHoursNotifications(MinecraftServer server, LocalTime now) {
        try {
            if (!ScheduleConfig.DEATH_HOURS_ENABLED.get()) return;
        } catch (IllegalStateException e) { return; }

        boolean active = isDeathHour();

        // Auto-toggle du global Death RP selon les death hours
        try {
            boolean currentGlobal = RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get();
            if (active && !currentGlobal) {
                RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.set(true);
                RpEssentials.LOGGER.info("[DeathRP] Death hours started — global Death RP enabled.");
            } else if (!active && currentGlobal) {
                RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.set(false);
                RpEssentials.LOGGER.info("[DeathRP] Death hours ended — global Death RP disabled.");
            }
        } catch (IllegalStateException ignored) {}

        if (!active) {
            lastDeathHoursSlotKey = "";
            return;
        }

        // Clé = le slot actif → fire une seule fois par slot
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

        // Clé = le slot actif → fire une seule fois par slot
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

    private static DaySchedule getNextOpenSchedule() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        LocalTime now   = LocalTime.now();
        for (int i = 0; i <= 7; i++) {
            DayOfWeek day = today.plus(i);
            DaySchedule s = schedules.get(day);
            if (s == null) continue;
            if (i == 0 && !s.open().isAfter(now)) continue;
            return s;
        }
        return null;
    }

    private static String getNextOpenDayName() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        LocalTime now   = LocalTime.now();
        for (int i = 0; i <= 7; i++) {
            DayOfWeek next = today.plus(i);
            DaySchedule s  = schedules.get(next);
            if (s == null) continue;
            if (i == 0 && !s.open().isAfter(now)) continue;
            return i == 0 ? "Today" : next.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        }
        return "N/A";
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
            return end.isAfter(start)
                    ? (!now.isBefore(start) && now.isBefore(end))
                    : (!now.isBefore(start) || now.isBefore(end));
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[Schedule] Invalid slot format: '{}'", slot);
            return false;
        }
    }
}