package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;
import net.rp.rpessentials.RpEssentialsIO;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AbsenceManager {

    public static class AbsenceEntry {
        public String uuid;
        public String mcName;
        public String reason = "";
        public String issuerName;
        public long createdAt;
        public long endsAt;

        public AbsenceEntry() {}

        public boolean isExpired() {
            return System.currentTimeMillis() > endsAt;
        }

        public String formatEnd() {
            return java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(endsAt), java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }

    private static final Map<UUID, AbsenceEntry> entries = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dataFile = null;

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            dataFile = new File(dataFolder, "absences.json");
            if (dataFile.exists()) loadFromFile();
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[AbsenceManager] Failed to initialize", e);
        }
    }

    private static void loadFromFile() {
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, AbsenceEntry>>(){}.getType();
            Map<String, AbsenceEntry> data = GSON.fromJson(reader, type);
            if (data != null) {
                entries.clear();
                for (AbsenceEntry e : data.values()) {
                    try { entries.put(UUID.fromString(e.uuid), e); }
                    catch (Exception ex) { RpEssentials.LOGGER.warn("[AbsenceManager] Invalid UUID: {}", e.uuid); }
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[AbsenceManager] Failed to load", e);
            RpEssentialsIO.quarantine(dataFile);
        }
    }

    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;
        Map<String, AbsenceEntry> data = new LinkedHashMap<>();
        for (AbsenceEntry e : new ArrayList<>(entries.values())) {
            data.put(e.uuid + " (" + e.mcName + ")", e);
        }
        RpEssentialsIO.saveJson(dataFile, data, GSON);
    }

    public static void add(UUID uuid, String name, int days, String reason, String issuer) {
        ensureInitialized();
        AbsenceEntry e = new AbsenceEntry();
        e.uuid = uuid.toString();
        e.mcName = name;
        e.reason = reason == null ? "" : reason;
        e.issuerName = issuer;
        e.createdAt = System.currentTimeMillis();
        e.endsAt = e.createdAt + days * 86_400_000L;
        entries.put(uuid, e);
        saveToFile();
    }

    public static boolean remove(UUID uuid) {
        ensureInitialized();
        boolean removed = entries.remove(uuid) != null;
        if (removed) saveToFile();
        return removed;
    }

    public static void onLogin(UUID uuid) {
        ensureInitialized();
        if (entries.remove(uuid) != null) saveToFile();
    }

    public static boolean isAbsent(UUID uuid) {
        ensureInitialized();
        AbsenceEntry e = entries.get(uuid);
        return e != null && !e.isExpired();
    }

    /** Date de fin de l'absence en ms, 0 si aucune. */
    public static long getEndMs(UUID uuid) {
        ensureInitialized();
        AbsenceEntry e = entries.get(uuid);
        return e != null ? e.endsAt : 0L;
    }

    public static List<AbsenceEntry> getAll() {
        ensureInitialized();
        return new ArrayList<>(entries.values());
    }

    public static void reload() {
        dataFile = null;
        entries.clear();
        ensureInitialized();
    }
}