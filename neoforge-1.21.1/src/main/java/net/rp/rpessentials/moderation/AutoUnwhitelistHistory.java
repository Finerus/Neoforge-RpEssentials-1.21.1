package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.core.RpEssentialsIO;
import net.rp.rpessentials.util.TimeHelper;

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
            this.timestamp      = TimeHelper.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            this.playerName     = playerName;
            this.playerUUID     = playerUUID;
            this.lastConnection = lastConnection;
            this.totalPlaytime  = PlaytimeManager.format(totalPlaytimeMs);
            this.licenses       = new ArrayList<>(licenses);
            this.inactiveDays   = inactiveDays;
        }
    }

    private static final net.rp.rpessentials.core.JsonStore<List<Entry>> STORE = new net.rp.rpessentials.core.JsonStore<>(
            "autounwhitelist-history.json", new TypeToken<List<Entry>>(){}.getType(), "AutoUnwhitelistHistory");

    private static final List<Entry> history =
            Collections.synchronizedList(new ArrayList<>());
    private static boolean loaded = false;

    private static synchronized void ensureInitialized() {
        if (loaded) return;
        loaded = true;
        List<Entry> data = STORE.load();
        if (data != null) {
            history.clear();
            history.addAll(data);
        }
    }

    private static void save() {
        if (!loaded) return;
        STORE.save(new ArrayList<>(history));
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
        loaded = false;
        STORE.reset();
        history.clear();
        ensureInitialized();
    }
}