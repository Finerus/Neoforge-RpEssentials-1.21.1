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

    public static void reload() { cached = null; }

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

    public static String format(String pattern, long epochMs) { return sdf(pattern).format(new Date(epochMs)); }

    public static String format(String pattern) { return format(pattern, System.currentTimeMillis()); }
}