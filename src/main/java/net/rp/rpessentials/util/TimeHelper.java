package net.rp.rpessentials.util;

import net.rp.rpessentials.config.Cfg;

import net.rp.rpessentials.config.ScheduleConfig;

import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import net.rp.rpessentials.RpEssentials;

public final class TimeHelper {

    private static volatile ZoneId cached = null;

    private TimeHelper() {}

    public static void reload() {
        cached = null;
        FORMATS.clear();
    }

    /** One formatter per pattern and thread (SimpleDateFormat is not thread-safe), created once. */
    private static final java.util.Map<String, ThreadLocal<SimpleDateFormat>> FORMATS = new java.util.concurrent.ConcurrentHashMap<>();

    public static ZoneId zone() {
        ZoneId z = cached;
        if (z != null) return z;
        // Not cached: the configured zone is used as soon as the config is loaded.
        if (!Cfg.ready(ScheduleConfig.SPEC)) return ZoneId.systemDefault();
        try {
            String id = ScheduleConfig.TIMEZONE.get().trim();
            z = id.isEmpty() || id.equalsIgnoreCase("SYSTEM") ? ZoneId.systemDefault() : ZoneId.of(id);
        } catch (DateTimeException e) {
            RpEssentials.LOGGER.warn("[Time] Unknown timezone, using the system one.");
            z = ZoneId.systemDefault();
        }
        cached = z;
        return z;
    }

    public static LocalDateTime now() { return LocalDateTime.now(zone()); }

    public static LocalTime nowTime() { return LocalTime.now(zone()); }

    public static LocalDate today() { return LocalDate.now(zone()); }

    public static SimpleDateFormat sdf(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern);
        f.setTimeZone(TimeZone.getTimeZone(zone()));
        return f;
    }

    /**
     * Formats a date with a SimpleDateFormat pattern (the syntax the config comments document).
     * The formatter is reused instead of being created for each message.
     */
    public static String format(String pattern, long epochMs) {
        if (FORMATS.size() > 64) FORMATS.clear();   // admin patterns are few; never grows without bound
        ThreadLocal<SimpleDateFormat> local = FORMATS.computeIfAbsent(pattern, p -> ThreadLocal.withInitial(() -> sdf(p)));
        return local.get().format(new Date(epochMs));
    }

    public static String format(String pattern) { return format(pattern, System.currentTimeMillis()); }
}