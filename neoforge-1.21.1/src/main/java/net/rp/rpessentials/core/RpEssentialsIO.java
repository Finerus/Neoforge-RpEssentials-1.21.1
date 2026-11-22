package net.rp.rpessentials.core;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.rp.rpessentials.RpEssentials;

/**
 * Single thread for every asynchronous disk write.
 * {@link #saveJson} groups the saves of a file: the file is written at most once per
 * {@link #SAVE_DELAY_MS}, always with the latest data. Pending saves are written on shutdown.
 */
public final class RpEssentialsIO {

    /** Delay during which several saves of the same file are merged into one write. */
    public static final long SAVE_DELAY_MS = 1000L;

    private record PendingSave(File target, Object data, Gson gson) {}

    private static final Map<String, PendingSave> PENDING = new ConcurrentHashMap<>();
    private static final Set<String> SCHEDULED = ConcurrentHashMap.newKeySet();

    private static volatile ScheduledThreadPoolExecutor EXECUTOR = createExecutor();
    private RpEssentialsIO() {}

    private static ScheduledThreadPoolExecutor createExecutor() {
        ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "rpessentials-io");
            t.setDaemon(true);
            return t;
        });
        // Delayed flushes are useless after shutdown: flushPending() already wrote everything.
        ex.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return ex;
    }

    public static void submit(Runnable task) {
        if (EXECUTOR.isShutdown()) {
            EXECUTOR = createExecutor();
        }
        EXECUTOR.submit(task);
    }

    public static void shutdown() {
        if (!EXECUTOR.isShutdown()) EXECUTOR.submit(RpEssentialsIO::flushPending);
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS))
                EXECUTOR.shutdownNow();
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void saveJson(File target, Object data, Gson gson) {
        String key = target.getAbsolutePath();
        PENDING.put(key, new PendingSave(target, data, gson));
        if (!SCHEDULED.add(key)) return;
        if (EXECUTOR.isShutdown()) EXECUTOR = createExecutor();
        EXECUTOR.schedule(() -> {
            SCHEDULED.remove(key);
            PendingSave p = PENDING.remove(key);
            if (p != null) writeJsonNow(p.target(), p.data(), p.gson());
        }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Writes every pending save now. Runs on the IO thread. */
    public static void flushPending() {
        for (String key : PENDING.keySet()) {
            PendingSave p = PENDING.remove(key);
            if (p != null) writeJsonNow(p.target(), p.data(), p.gson());
        }
        SCHEDULED.clear();
    }

    public static void writeJsonNow(File target, Object data, Gson gson) {
        // A direct write supersedes a pending grouped save of the same file.
        PENDING.remove(target.getAbsolutePath());
        try {
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            File tmp = new File(target.getPath() + ".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                gson.toJson(data, w);
            }
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[IO] Failed to save {}", target.getName(), e);
        }
    }

    /** Renomme un fichier illisible pour ne pas l'écraser avec des données vides. */
    public static void quarantine(File f) {
        try {
            Files.move(f.toPath(), new File(f.getPath() + ".corrupt-" + System.currentTimeMillis()).toPath());
            RpEssentials.LOGGER.error("[IO] {} was unreadable, moved aside.", f.getName());
        } catch (Exception ignored) {}
    }
}