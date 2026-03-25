package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class NoteManager {

    // =========================================================================
    // INNER CLASS
    // =========================================================================

    public static class NoteEntry {
        public int    id;
        public String text;
        public String authorName;
        public String authorUUID;
        public String timestamp;

        public NoteEntry() {}

        public NoteEntry(int id, String text, String authorName, String authorUUID) {
            this.id         = id;
            this.text       = text;
            this.authorName = authorName;
            this.authorUUID = authorUUID;
            this.timestamp  = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
    }

    // =========================================================================
    // STATE
    // =========================================================================

    // UUID cible → liste de notes
    private static final Map<UUID, List<NoteEntry>> notes = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File notesDir = null;
    private static final AtomicInteger idCounter = new AtomicInteger(1);

    // =========================================================================
    // INIT
    // =========================================================================

    private static synchronized void ensureInitialized() {
        if (notesDir != null) return;
        try {
            notesDir = new File(RpEssentialsDataPaths.getDataFolder(), "notes");
            if (!notesDir.exists()) notesDir.mkdirs();
            loadAll();
            RpEssentials.LOGGER.info("[NoteManager] Initialized - Dir: {}", notesDir.getAbsolutePath());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[NoteManager] Failed to initialize", e);
        }
    }

    // =========================================================================
    // LOAD / SAVE
    // =========================================================================

    private static void loadAll() {
        if (notesDir == null) return;
        File[] files = notesDir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;
        int maxId = 0;
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                String name = file.getName().replace(".json", "");
                String uuidStr = name.contains("_") ? name.substring(0, name.indexOf('_')) : name;
                UUID uuid = UUID.fromString(uuidStr);
                Type type = new TypeToken<List<NoteEntry>>(){}.getType();
                List<NoteEntry> list = GSON.fromJson(reader, type);
                if (list != null) {
                    notes.put(uuid, new ArrayList<>(list));
                    for (NoteEntry e : list) if (e.id > maxId) maxId = e.id;
                }
            } catch (Exception e) {
                RpEssentials.LOGGER.warn("[NoteManager] Could not load file: {}", file.getName());
            }
        }
        idCounter.set(maxId + 1);
        RpEssentials.LOGGER.info("[NoteManager] Loaded notes for {} players", notes.size());
    }

    private static void saveForPlayer(UUID uuid) {
        if (notesDir == null) return;
        List<NoteEntry> list = notes.getOrDefault(uuid, List.of());
        File targetFile = new File(notesDir, uuid.toString() + ".json");

        CompletableFuture.runAsync(() -> {
            try {
                if (list.isEmpty()) {
                    targetFile.delete();
                    return;
                }
                try (FileWriter writer = new FileWriter(targetFile)) {
                    GSON.toJson(list, writer);
                }
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[NoteManager] Failed to save notes for {}", uuid, e);
            }
        });
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    public static int addNote(UUID targetUUID, String authorName, String authorUUID, String text) {
        ensureInitialized();
        int id = idCounter.getAndIncrement();
        notes.computeIfAbsent(targetUUID, k -> new ArrayList<>())
                .add(new NoteEntry(id, text, authorName, authorUUID));
        saveForPlayer(targetUUID);
        return id;
    }

    public static boolean removeNote(UUID targetUUID, int noteId) {
        ensureInitialized();
        List<NoteEntry> list = notes.get(targetUUID);
        if (list == null) return false;
        boolean removed = list.removeIf(e -> e.id == noteId);
        if (removed) saveForPlayer(targetUUID);
        return removed;
    }

    public static void clearNotes(UUID targetUUID) {
        ensureInitialized();
        notes.remove(targetUUID);
        saveForPlayer(targetUUID);
    }

    public static List<NoteEntry> getNotes(UUID targetUUID) {
        ensureInitialized();
        return new ArrayList<>(notes.getOrDefault(targetUUID, List.of()));
    }

    public static boolean hasNotes(UUID targetUUID) {
        ensureInitialized();
        List<NoteEntry> list = notes.get(targetUUID);
        return list != null && !list.isEmpty();
    }
}