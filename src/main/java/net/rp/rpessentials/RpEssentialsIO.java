package net.rp.rpessentials;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Executor unique pour toutes les écritures disque asynchrones.
 * Remplace les CompletableFuture.runAsync() épars qui créaient un thread par sauvegarde.
 */
public final class RpEssentialsIO {

    private static volatile ExecutorService EXECUTOR = createExecutor();
    private RpEssentialsIO() {}

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rpessentials-io");
            t.setDaemon(true);
            return t;
        });
    }

    public static void submit(Runnable task) {
        if (EXECUTOR.isShutdown()) {
            EXECUTOR = createExecutor();
        }
        EXECUTOR.submit(task);
    }

    public static void shutdown() {
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
        submit(() -> writeJsonNow(target, data, gson));
    }

    public static void writeJsonNow(File target, Object data, Gson gson) {
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