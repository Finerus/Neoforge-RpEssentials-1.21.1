package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;
import net.rp.rpessentials.RpEssentialsIO;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PendingActionManager {

    public static class PendingAction {
        public String action;
        public String issuer;
        public long createdAt;
    }

    public static class Entry {
        public String uuid;
        public String mcName;
        public List<PendingAction> actions = new ArrayList<>();
    }

    private static final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dataFile = null;

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            dataFile = new File(dataFolder, "pending-actions.json");
            if (dataFile.exists()) loadFromFile();
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[PendingActionManager] Failed to initialize", e);
        }
    }

    private static void loadFromFile() {
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Entry>>(){}.getType();
            Map<String, Entry> data = GSON.fromJson(reader, type);
            if (data != null) {
                entries.clear();
                for (Entry e : data.values()) {
                    try { entries.put(UUID.fromString(e.uuid), e); }
                    catch (Exception ex) { RpEssentials.LOGGER.warn("[PendingActionManager] Invalid UUID: {}", e.uuid); }
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[PendingActionManager] Failed to load", e);
            RpEssentialsIO.quarantine(dataFile);
        }
    }

    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;
        Map<String, Entry> data = new LinkedHashMap<>();
        for (Entry e : new ArrayList<>(entries.values())) {
            Entry copy = new Entry();
            copy.uuid = e.uuid;
            copy.mcName = e.mcName;
            copy.actions = new ArrayList<>(e.actions);
            data.put(e.uuid + " (" + e.mcName + ")", copy);
        }
        RpEssentialsIO.saveJson(dataFile, data, GSON);
    }

    public static void add(UUID uuid, String name, ManageService.Action action, String issuer) {
        ensureInitialized();
        Entry e = entries.computeIfAbsent(uuid, k -> {
            Entry n = new Entry();
            n.uuid = uuid.toString();
            n.mcName = name;
            return n;
        });
        for (PendingAction a : e.actions) if (a.action.equals(action.name())) return;
        PendingAction p = new PendingAction();
        p.action = action.name();
        p.issuer = issuer;
        p.createdAt = System.currentTimeMillis();
        e.actions.add(p);
        saveToFile();
    }

    public static void applyOnLogin(MinecraftServer server, ServerPlayer player) {
        ensureInitialized();
        Entry e = entries.remove(player.getUUID());
        if (e == null) return;
        saveToFile();
        for (PendingAction a : e.actions) {
            try {
                ManageService.Action action = ManageService.Action.valueOf(a.action);
                ManageService.execute(server, null, player.getUUID(), action, "pending from " + a.issuer, 0);
            } catch (IllegalArgumentException ex) {
                RpEssentials.LOGGER.warn("[PendingActionManager] Unknown action: {}", a.action);
            }
        }
    }

    public static void reload() {
        dataFile = null;
        entries.clear();
        ensureInitialized();
    }
}