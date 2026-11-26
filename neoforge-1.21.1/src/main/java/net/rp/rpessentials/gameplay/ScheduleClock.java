package net.rp.rpessentials.gameplay;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Opening hours, without anything from Minecraft: slots of each weekday, dated exceptions, and the sessions they make
 * (real start and end dates, so a slot that runs past midnight belongs to the day it starts).
 */
final class ScheduleClock {

    /** One opening of a day. An end at or before the start is on the next day (20:00-02:00, 00:00-24:00). */
    record Slot(LocalTime open, LocalTime close) {
        String text() { return open.format(FMT) + "-" + closeText(); }

        /** "24:00" rather than "00:00" for an end at midnight. */
        String closeText() { return close.equals(LocalTime.MIDNIGHT) ? "24:00" : close.format(FMT); }
    }

    /** One continuous opening, from its first to its last minute. */
    record Session(LocalDateTime start, LocalDateTime end) {
        boolean contains(LocalDateTime t) { return !t.isBefore(start) && t.isBefore(end); }
    }

    static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    /** Days looked ahead for the next opening: a few closed exceptions can push it further than a week. */
    private static final int DAYS_AHEAD = 32;
    /** 23:59 followed by 00:00: nobody is kicked for one minute. */
    private static final Duration JOIN_GAP = Duration.ofMinutes(1);

    private final Map<DayOfWeek, List<Slot>> week = new EnumMap<>(DayOfWeek.class);
    private final Map<LocalDate, List<Slot>> exceptions = new TreeMap<>();

    private LocalDate sessionsDay = null;
    private List<Session> sessions = List.of();

    void setDay(DayOfWeek day, List<Slot> slots) { week.put(day, List.copyOf(slots)); sessionsDay = null; }

    /** Replaces the week for one date; no slot = closed. */
    void setException(LocalDate date, List<Slot> slots) { exceptions.put(date, List.copyOf(slots)); sessionsDay = null; }

    List<Slot> day(DayOfWeek day) { return week.getOrDefault(day, List.of()); }

    List<Slot> day(LocalDate date) {
        List<Slot> e = exceptions.get(date);
        return e != null ? e : day(date.getDayOfWeek());
    }

    Map<LocalDate, List<Slot>> exceptionsFrom(LocalDate date) {
        Map<LocalDate, List<Slot>> out = new TreeMap<>();
        for (var e : exceptions.entrySet()) if (!e.getKey().isBefore(date)) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** The session in progress, null when closed. */
    Session active(LocalDateTime now) {
        for (Session s : sessions(now.toLocalDate())) {
            if (s.contains(now)) return s;
            if (s.start().isAfter(now)) break;
        }
        return null;
    }

    /** The next session to start, null when none is planned. */
    Session next(LocalDateTime now) {
        for (Session s : sessions(now.toLocalDate())) if (s.start().isAfter(now)) return s;
        return null;
    }

    /** Sessions from yesterday (it may not be over) on, built once a day. */
    private List<Session> sessions(LocalDate today) {
        if (today.equals(sessionsDay)) return sessions;
        List<Session> all = new ArrayList<>();
        for (int i = -1; i <= DAYS_AHEAD; i++) {
            LocalDate date = today.plusDays(i);
            for (Slot s : day(date)) {
                all.add(new Session(date.atTime(s.open()),
                        (s.close().isAfter(s.open()) ? date : date.plusDays(1)).atTime(s.close())));
            }
        }
        all.sort(Comparator.comparing(Session::start));
        List<Session> merged = new ArrayList<>();
        for (Session s : all) {
            Session last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && !s.start().isAfter(last.end().plus(JOIN_GAP))) {
                if (s.end().isAfter(last.end())) merged.set(merged.size() - 1, new Session(last.start(), s.end()));
            } else {
                merged.add(s);
            }
        }
        sessions = merged;
        sessionsDay = today;
        return merged;
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    /** "19:00"; "24:00" is midnight, as an end. Null when wrong. */
    static LocalTime parseTime(String text, boolean end) {
        String t = text == null ? "" : text.trim();
        if (end && t.equals("24:00")) return LocalTime.MIDNIGHT;
        return t.matches("([01]\\d|2[0-3]):[0-5]\\d") ? LocalTime.parse(t, FMT) : null;
    }

    /** "14:00-18:00". Null when wrong. */
    static Slot parseSlot(String text) {
        String[] p = text == null ? new String[0] : text.split("-", 2);
        if (p.length != 2) return null;
        LocalTime open = parseTime(p[0], false), close = parseTime(p[1], true);
        return open == null || close == null ? null : new Slot(open, close);
    }

    /** "14:00-18:00,20:00-23:00". Null when one of them is wrong. */
    static List<Slot> parseSlots(String text) {
        List<Slot> out = new ArrayList<>();
        for (String part : text.split(",")) {
            Slot s = parseSlot(part);
            if (s == null) return null;
            out.add(s);
        }
        return out;
    }
}
