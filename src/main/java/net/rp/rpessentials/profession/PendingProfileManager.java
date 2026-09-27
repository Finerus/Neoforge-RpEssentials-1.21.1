package net.rp.rpessentials.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;
import net.rp.rpessentials.RpEssentialsIO;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stocke les profils préparés par le staff pour des joueurs jamais connectés.
 * Appliqué automatiquement à la première connexion du joueur concerné.
 */
public class PendingProfileManager {

    public static class PendingEntry {
        public String uuid;
        public String mcName;
        public String nickname = "";
        public String role = "";
        public List<String> licenses = new ArrayList<>();

        public PendingEntry() {}

        public PendingEntry(String uuid, String mcName) {
            this.uuid = uuid;
            this.mcName = mcName;
        }
    }

    private static final Map<UUID, PendingEntry> entries = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dataFile = null;

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            dataFile = new File(dataFolder, "pending-profiles.json");
            if (dataFile.exists()) loadFromFile();
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[PendingProfileManager] Failed to initialize", e);
        }
    }

    private static void loadFromFile() {
        if (dataFile == null || !dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, PendingEntry>>(){}.getType();
            Map<String, PendingEntry> data = GSON.fromJson(reader, type);
            if (data != null) {
                entries.clear();
                for (PendingEntry e : data.values()) {
                    try { entries.put(UUID.fromString(e.uuid), e); }
                    catch (Exception ex) { RpEssentials.LOGGER.warn("[PendingProfileManager] Invalid UUID: {}", e.uuid); }
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[PendingProfileManager] Failed to load", e);
        }
    }

    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;
        Map<UUID, PendingEntry> snapshot = new HashMap<>(entries);
        File targetFile = dataFile;
        RpEssentialsIO.submit(() -> {
            try {
                Map<String, PendingEntry> data = new LinkedHashMap<>();
                for (PendingEntry e : snapshot.values()) {
                    data.put(e.uuid + " (" + e.mcName + ")", e);
                }
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileWriter writer = new FileWriter(targetFile)) {
                    GSON.toJson(data, writer);
                }
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[PendingProfileManager] Failed to save", e);
            }
        });
    }

    // =========================================================================
    // API PUBLIQUE
    // =========================================================================

    public static boolean hasPending(UUID uuid) {
        ensureInitialized();
        return entries.containsKey(uuid);
    }

    public static void addPending(UUID uuid, String mcName) {
        ensureInitialized();
        entries.put(uuid, new PendingEntry(uuid.toString(), mcName));
        saveToFile();
    }

    public static String getMcName(UUID uuid) {
        ensureInitialized();
        PendingEntry e = entries.get(uuid);
        return e != null ? e.mcName : "Unknown";
    }

    public static void setNickname(UUID uuid, String nickname) {
        ensureInitialized();
        PendingEntry e = entries.get(uuid);
        if (e == null) return;
        e.nickname = nickname;
        saveToFile();
    }

    public static void setRole(UUID uuid, String role) {
        ensureInitialized();
        PendingEntry e = entries.get(uuid);
        if (e == null) return;
        e.role = role;
        saveToFile();
    }

    public static void addLicense(UUID uuid, String profession) {
        ensureInitialized();
        PendingEntry e = entries.get(uuid);
        if (e == null || e.licenses.contains(profession)) return;
        e.licenses.add(profession);
        saveToFile();
    }

    public static void removeLicense(UUID uuid, String profession) {
        ensureInitialized();
        PendingEntry e = entries.get(uuid);
        if (e == null) return;
        e.licenses.remove(profession);
        saveToFile();
    }

    public static List<PendingEntry> getAllPending() {
        ensureInitialized();
        return new ArrayList<>(entries.values());
    }

    /** Retire et retourne l'entrée en attente d'un joueur, appelé à sa première connexion. */
    public static PendingEntry consumePending(UUID uuid) {
        ensureInitialized();
        PendingEntry e = entries.remove(uuid);
        if (e != null) saveToFile();
        return e;
    }

    public static void reload() {
        dataFile = null;
        entries.clear();
        ensureInitialized();
    }
}