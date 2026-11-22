package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.core.RpEssentialsIO;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player reports (/rp report): stored in reports.json, handled by the staff
 * from /rpessentials reports or the Reports tab of the player profile GUI.
 */
public final class ReportManager {

    public enum Status { OPEN, CLAIMED, CLOSED }

    public static class Report {
        public int id;
        public String reporterUuid;
        public String reporterName;
        public String targetUuid;
        public String targetName;
        public String reason;
        public long createdAt;
        /** Where the reporter stood when sending the report. */
        public String dimension;
        public int x, y, z;
        public Status status = Status.OPEN;
        public String handlerUuid;
        public String handlerName;
        public long closedAt;
        public String closeNote;

        public Report() {}

        Report copy() {
            Report r = new Report();
            r.id = id; r.reporterUuid = reporterUuid; r.reporterName = reporterName;
            r.targetUuid = targetUuid; r.targetName = targetName; r.reason = reason;
            r.createdAt = createdAt; r.dimension = dimension; r.x = x; r.y = y; r.z = z;
            r.status = status; r.handlerUuid = handlerUuid; r.handlerName = handlerName;
            r.closedAt = closedAt; r.closeNote = closeNote;
            return r;
        }

        public boolean isOpen() { return status != Status.CLOSED; }

        public UUID target() { return UUID.fromString(targetUuid); }

        public UUID reporter() { return UUID.fromString(reporterUuid); }
    }

    /** Result of {@link #create}. */
    public enum CreateResult { OK, DISABLED, SELF, COOLDOWN, TOO_MANY }

    public record Created(CreateResult result, Report report, long waitSeconds) {}

    private static class Stored {
        int nextId = 1;
        List<Report> reports = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Report> reports = new ArrayList<>();
    /** Last report time per reporter, for the cooldown. Not saved: a restart resets it. */
    private static final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();
    private static int nextId = 1;
    private static File dataFile = null;

    private ReportManager() {}

    // =========================================================================
    // CONFIG
    // =========================================================================
    public static boolean enabled() { return Cfg.get(ModerationConfig.ENABLE_REPORTS, true); }

    private static int cooldownSeconds() { return Cfg.get(ModerationConfig.REPORT_COOLDOWN_SECONDS, 60); }

    private static int maxOpenPerReporter() { return Cfg.get(ModerationConfig.REPORT_MAX_OPEN_PER_PLAYER, 3); }

    public static int maxReasonLength() { return Cfg.get(ModerationConfig.REPORT_REASON_MAX_LENGTH, 200); }

    private static int closedRetentionDays() { return Cfg.get(ModerationConfig.REPORT_CLOSED_RETENTION_DAYS, 90); }

    // =========================================================================
    // LOAD / SAVE
    // =========================================================================
    public static synchronized void reload() {
        reports.clear();
        lastReport.clear();
        nextId = 1;
        dataFile = RpEssentialsDataPaths.getDataFile("reports.json");
        if (dataFile.exists()) {
            try (var reader = new InputStreamReader(Files.newInputStream(dataFile.toPath()), StandardCharsets.UTF_8)) {
                Stored s = GSON.fromJson(reader, Stored.class);
                if (s != null) {
                    if (s.reports != null) {
                        for (Report r : s.reports) if (r != null && r.targetUuid != null) reports.add(r);
                    }
                    nextId = Math.max(1, s.nextId);
                    for (Report r : reports) nextId = Math.max(nextId, r.id + 1);
                }
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[Reports] Failed to load reports.json", e);
                RpEssentialsIO.quarantine(dataFile);
            }
        }
        if (purgeClosed() > 0) save();
        RpEssentials.LOGGER.info("[Reports] {} report(s), {} open", reports.size(), countOpen());
    }

    private static void save() {
        if (dataFile == null) return;
        // Copies: the IO thread writes later, while the server thread may change the reports.
        Stored s = new Stored();
        s.nextId = nextId;
        for (Report r : reports) s.reports.add(r.copy());
        RpEssentialsIO.saveJson(dataFile, s, GSON);
    }

    /** Removes closed reports older than reportClosedRetentionDays (0 = keep forever). */
    private static int purgeClosed() {
        int days = closedRetentionDays();
        if (days <= 0) return 0;
        long limit = System.currentTimeMillis() - days * 86_400_000L;
        int before = reports.size();
        reports.removeIf(r -> r.status == Status.CLOSED && r.closedAt > 0 && r.closedAt < limit);
        return before - reports.size();
    }

    // =========================================================================
    // ACTIONS
    // =========================================================================
    public static synchronized Created create(ServerPlayer reporter, UUID target, String targetName, String reason) {
        if (!enabled()) return new Created(CreateResult.DISABLED, null, 0);
        UUID rid = reporter.getUUID();
        if (rid.equals(target)) return new Created(CreateResult.SELF, null, 0);

        long now = System.currentTimeMillis();
        Long last = lastReport.get(rid);
        long cd = cooldownSeconds() * 1000L;
        if (last != null && now - last < cd) {
            return new Created(CreateResult.COOLDOWN, null, (cd - (now - last) + 999) / 1000);
        }
        int max = maxOpenPerReporter();
        if (max > 0 && countOpenBy(rid) >= max) return new Created(CreateResult.TOO_MANY, null, max);

        int maxLen = maxReasonLength();
        String clean = reason.strip();
        if (maxLen > 0 && clean.length() > maxLen) clean = clean.substring(0, maxLen);

        Report r = new Report();
        r.id = nextId++;
        r.reporterUuid = rid.toString();
        r.reporterName = reporter.getGameProfile().getName();
        r.targetUuid = target.toString();
        r.targetName = targetName;
        r.reason = clean;
        r.createdAt = now;
        r.dimension = reporter.level().dimension().location().toString();
        r.x = reporter.getBlockX();
        r.y = reporter.getBlockY();
        r.z = reporter.getBlockZ();
        reports.add(r);
        lastReport.put(rid, now);
        save();
        HistoryManager.record(target, targetName, HistoryManager.REPORT, r.reporterName, "#" + r.id + " " + clean);
        return new Created(CreateResult.OK, r, 0);
    }

    public static synchronized boolean claim(int id, ServerPlayer staff) {
        Report r = find(id);
        if (r == null || r.status == Status.CLOSED) return false;
        r.status = Status.CLAIMED;
        r.handlerUuid = staff.getUUID().toString();
        r.handlerName = staff.getGameProfile().getName();
        save();
        return true;
    }

    public static synchronized boolean close(int id, String staffName, String staffUuid, String note) {
        Report r = find(id);
        if (r == null || r.status == Status.CLOSED) return false;
        r.status = Status.CLOSED;
        r.handlerName = staffName;
        r.handlerUuid = staffUuid;
        r.closedAt = System.currentTimeMillis();
        r.closeNote = note == null || note.isBlank() ? null : note.strip();
        save();
        HistoryManager.record(r.target(), r.targetName, HistoryManager.REPORT, staffName,
                "#" + r.id + " ✔" + (r.closeNote != null ? " " + r.closeNote : ""));
        return true;
    }

    public static synchronized boolean reopen(int id) {
        Report r = find(id);
        if (r == null || r.status != Status.CLOSED) return false;
        r.status = Status.OPEN;
        r.closedAt = 0;
        r.closeNote = null;
        save();
        return true;
    }

    public static synchronized boolean delete(int id) {
        boolean removed = reports.removeIf(r -> r.id == id);
        if (removed) save();
        return removed;
    }

    // =========================================================================
    // QUERIES (copies, safe to read anywhere)
    // =========================================================================
    public static synchronized Report get(int id) {
        Report r = find(id);
        return r != null ? r.copy() : null;
    }

    /** Reports against a player: open ones first, then the newest. */
    public static synchronized List<Report> forTarget(UUID target) {
        String t = target.toString();
        List<Report> out = new ArrayList<>();
        for (Report r : reports) if (t.equals(r.targetUuid)) out.add(r.copy());
        out.sort(ORDER);
        return out;
    }

    /** Open reports (open or in progress), oldest first. */
    public static synchronized List<Report> open() {
        List<Report> out = new ArrayList<>();
        for (Report r : reports) if (r.isOpen()) out.add(r.copy());
        out.sort(Comparator.comparingInt(r -> r.id));
        return out;
    }

    public static synchronized int countOpen() {
        int n = 0;
        for (Report r : reports) if (r.isOpen()) n++;
        return n;
    }

    public static synchronized int countOpenAgainst(UUID target) {
        String t = target.toString();
        int n = 0;
        for (Report r : reports) if (r.isOpen() && t.equals(r.targetUuid)) n++;
        return n;
    }

    /** Number of open reports against each reported player. */
    public static synchronized Map<UUID, Integer> openCountsByTarget() {
        Map<UUID, Integer> out = new java.util.HashMap<>();
        for (Report r : reports) {
            if (!r.isOpen()) continue;
            try {
                out.merge(r.target(), 1, Integer::sum);
            } catch (IllegalArgumentException ignored) {
                // Malformed UUID in a hand-edited file.
            }
        }
        return out;
    }

    private static int countOpenBy(UUID reporter) {
        String t = reporter.toString();
        int n = 0;
        for (Report r : reports) if (r.isOpen() && t.equals(r.reporterUuid)) n++;
        return n;
    }

    private static Report find(int id) {
        for (Report r : reports) if (r.id == id) return r;
        return null;
    }

    private static final Comparator<Report> ORDER =
            Comparator.comparing((Report r) -> !r.isOpen()).thenComparing(r -> -r.createdAt);
}
