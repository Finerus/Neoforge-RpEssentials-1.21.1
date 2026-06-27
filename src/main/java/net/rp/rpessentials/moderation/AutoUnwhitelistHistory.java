package net.rp.rpessentials.moderation;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AutoUnwhitelistHistory {

    public static class Entry {
        public String timestamp;
        public String playerName;
        public String playerUUID;
        public String lastConnection;
        public String totalPlaytime;
        public List<String> licenses;
        public long inactiveDays;

        public Entry() {}

        public Entry(String playerName, String playerUUID, String lastConnection,
                     long totalPlaytimeMs, List<String> licenses, long inactiveDays) {
            this.timestamp      = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            this.playerName     = playerName;
            this.playerUUID     = playerUUID;
            this.lastConnection = lastConnection;
            this.totalPlaytime  = PlaytimeManager.format(totalPlaytimeMs);
            this.licenses       = new ArrayList<>(licenses);
            this.inactiveDays   = inactiveDays;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILENAME = "autounwhitelist-history.json";

    private static final List<Entry> history =
            Collections.synchronizedList(new ArrayList<>());
    private static File dataFile = null;

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File folder = RpEssentialsDataPaths.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            dataFile = new File(folder, FILENAME);
            if (dataFile.exists()) load();
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[AutoUnwhitelistHistory] Failed to initialize.", e);
        }
    }

    private static void load() {
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<List<Entry>>(){}.getType();
            List<Entry> data = GSON.fromJson(reader, type);
            if (data != null) {
                history.clear();
                history.addAll(data);
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[AutoUnwhitelistHistory] Failed to load.", e);
        }
    }

    private static void save() {
        if (dataFile == null) return;
        List<Entry> snapshot = new ArrayList<>(history);
        File target = dataFile;
        RpEssentialsIO.submit(() -> {
            try (FileWriter writer = new FileWriter(target)) {
                GSON.toJson(snapshot, writer);
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[AutoUnwhitelistHistory] Failed to save.", e);
            }
        });
    }

    public static void record(String playerName, String playerUUID, String lastConnection,
                              long totalPlaytimeMs, List<String> licenses, long inactiveDays) {
        ensureInitialized();
        history.add(new Entry(playerName, playerUUID, lastConnection,
                totalPlaytimeMs, licenses, inactiveDays));
        save();
        RpEssentials.LOGGER.info("[AutoUnwhitelistHistory] Recorded unwhitelist for {}.", playerName);
    }

    public static List<Entry> getAll() {
        ensureInitialized();
        return new ArrayList<>(history);
    }

    public static void reload() {
        dataFile = null;
        history.clear();
        ensureInitialized();
    }
}