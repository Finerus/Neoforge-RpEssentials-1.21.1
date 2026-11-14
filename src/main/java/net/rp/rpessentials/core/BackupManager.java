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

    public static void run(int keep) {
        lastRun = System.currentTimeMillis();
        File data = RpEssentialsDataPaths.getDataFolder();
        File dir = new File(data.getParentFile(), "rpessentials-backups");
        RpEssentialsIO.submit(() -> {
            if (!data.isDirectory()) return;
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
                return;
            }
            File[] all = dir.listFiles((d, n) -> n.startsWith("backup-") && n.endsWith(".zip"));
            if (all == null) return;
            Arrays.sort(all);
            for (int i = 0; i < all.length - Math.max(1, keep); i++) all[i].delete();
            RpEssentials.LOGGER.info("[Backup] Saved {}", target.getName());
        });
    }
}