package net.rp.rpessentials.core;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.rp.rpessentials.RpEssentials;

public final class BackupManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static long lastRun = 0L;

    private BackupManager() {}

    public static void tick() {
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            if (!RpEssentialsConfig.BACKUP_ENABLED.get()) return;
            long every = RpEssentialsConfig.BACKUP_INTERVAL_HOURS.get() * 3_600_000L;
            if (System.currentTimeMillis() - lastRun < every) return;
            run(RpEssentialsConfig.BACKUP_KEEP.get());
        }
    }

    /** Result of a backup: the zip written, or why it failed ({@code file} null). */
    public record Result(File file, String error) {

        /** Full path of the zip, without the "./" of the world path. */
        public String path() { return file.toPath().toAbsolutePath().normalize().toString(); }

        public String size() {
            double kb = file.length() / 1024.0;
            return kb < 1024 ? String.format(java.util.Locale.ROOT, "%.1f KB", kb)
                    : String.format(java.util.Locale.ROOT, "%.1f MB", kb / 1024);
        }
    }

    public static void run(int keep) {
        run(keep, null);
    }

    /** {@code done} (optional) is called on the IO thread once the zip is written, or when it failed. */
    public static void run(int keep, Consumer<Result> done) {
        lastRun = System.currentTimeMillis();
        File data = RpEssentialsDataPaths.getDataFolder();
        File dir = new File(data.getParentFile(), "rpessentials-backups");
        RpEssentialsIO.submit(() -> {
            // Saves wait up to a second before being written: without this the zip misses the latest data.
            RpEssentialsIO.flushPending();
            if (!data.isDirectory()) {
                if (done != null) done.accept(new Result(null, "no data folder"));
                return;
            }
            dir.mkdirs();
            File target = new File(dir, "backup-" + LocalDateTime.now().format(FMT) + ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target));
                 Stream<Path> walk = Files.walk(data.toPath())) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    if (p.toString().endsWith(".tmp")) continue;
                    zip.putNextEntry(new ZipEntry(data.toPath().relativize(p).toString().replace('\\', '/')));
                    Files.copy(p, zip);
                    zip.closeEntry();
                }
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[Backup] Failed", e);
                target.delete();
                if (done != null) done.accept(new Result(null, e.getClass().getSimpleName()
                        + (e.getMessage() != null ? ": " + e.getMessage() : "")));
                return;
            }
            File[] all = dir.listFiles((d, n) -> n.startsWith("backup-") && n.endsWith(".zip"));
            if (all != null) {
                Arrays.sort(all);
                for (int i = 0; i < all.length - Math.max(1, keep); i++) all[i].delete();
            }
            Result result = new Result(target, null);
            RpEssentials.LOGGER.info("[Backup] Saved {} ({})", result.path(), result.size());
            if (done != null) done.accept(result);
        });
    }
}