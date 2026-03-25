package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;
import net.rp.rpessentials.config.MessagesConfig;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class MuteManager {

    // =========================================================================
    // INNER CLASS
    // =========================================================================

    public static class MuteEntry {
        public String targetUUID;
        public String targetName;
        public String issuerUUID;   // null = console / auto
        public String issuerName;
        public String reason;
        public long issuedAt;
        public Long expiresAt;      // null = permanent

        public MuteEntry() {}

        public MuteEntry(String targetUUID, String targetName,
                         String issuerUUID, String issuerName,
                         String reason, long issuedAt, Long expiresAt) {
            this.targetUUID = targetUUID;
            this.targetName = targetName;
            this.issuerUUID = issuerUUID;
            this.issuerName = issuerName;
            this.reason     = reason;
            this.issuedAt   = issuedAt;
            this.expiresAt  = expiresAt;
        }

        public boolean isExpired() {
            return expiresAt != null && System.currentTimeMillis() > expiresAt;
        }

        public boolean isPermanent() {
            return expiresAt == null;
        }

        public String getFormattedExpiry() {
            if (isPermanent()) return "Permanent";
            if (isExpired()) return "§cExpired";
            long remaining = expiresAt - System.currentTimeMillis();
            long totalMinutes = remaining / 60_000;
            long days    = totalMinutes / 1440;
            long hours   = (totalMinutes % 1440) / 60;
            long minutes = totalMinutes % 60;
            if (days > 0)  return days + "d " + hours + "h remaining";
            if (hours > 0) return hours + "h " + minutes + "min remaining";
            return minutes + "min remaining";
        }
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private static final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dataFile = null;

    // =========================================================================
    // INIT
    // =========================================================================

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            dataFile = new File(dataFolder, "mutes.json");
            if (dataFile.exists()) loadFromFile();
            RpEssentials.LOGGER.info("[MuteManager] Initialized - File: {}", dataFile.getAbsolutePath());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[MuteManager] Failed to initialize", e);
        }
    }

    // =========================================================================
    // LOAD / SAVE
    // =========================================================================

    private static void loadFromFile() {
        if (dataFile == null || !dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, MuteEntry>>(){}.getType();
            Map<String, MuteEntry> data = GSON.fromJson(reader, type);
            if (data != null) {
                mutes.clear();
                for (Map.Entry<String, MuteEntry> e : data.entrySet()) {
                    try {
                        String key = e.getKey();
                        String uuidStr = key.contains(" ") ? key.substring(0, key.indexOf(' ')) : key;
                        mutes.put(UUID.fromString(uuidStr), e.getValue());
                    } catch (IllegalArgumentException ex) {
                        RpEssentials.LOGGER.warn("[MuteManager] Invalid UUID: {}", e.getKey());
                    }
                }
                RpEssentials.LOGGER.info("[MuteManager] Loaded {} mutes", mutes.size());
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[MuteManager] Failed to load", e);
        }
    }

    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;

        // Résolution des noms sur le thread serveur
        Map<UUID, MuteEntry> snapshot = new HashMap<>(mutes);
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        Map<String, MuteEntry> data = new LinkedHashMap<>();
        for (Map.Entry<UUID, MuteEntry> e : snapshot.entrySet()) {
            String mcName = e.getValue().targetName != null ? e.getValue().targetName : "Unknown";
            if (server != null) {
                ServerPlayer online = server.getPlayerList().getPlayer(e.getKey());
                if (online != null) mcName = online.getName().getString();
            }
            data.put(e.getKey().toString() + " (" + mcName + ")", e.getValue());
        }

        File targetFile = dataFile;
        CompletableFuture.runAsync(() -> {
            try (FileWriter writer = new FileWriter(targetFile)) {
                GSON.toJson(data, writer);
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[MuteManager] Failed to save", e);
            }
        });
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Ajoute ou met à jour un mute.
     * @param durationMinutes 0 = permanent
     */
    public static void mute(UUID targetUUID, String targetName,
                            UUID issuerUUID, String issuerName,
                            String reason, int durationMinutes) {
        ensureInitialized();
        Long expiresAt = durationMinutes > 0
                ? System.currentTimeMillis() + (long) durationMinutes * 60_000
                : null;
        mutes.put(targetUUID, new MuteEntry(
                targetUUID.toString(), targetName,
                issuerUUID != null ? issuerUUID.toString() : null,
                issuerName, reason,
                System.currentTimeMillis(), expiresAt));
        saveToFile();
        RpEssentials.LOGGER.info("[MuteManager] {} muted by {} — {}", targetName, issuerName, reason);
    }

    public static void unmute(UUID targetUUID) {
        ensureInitialized();
        mutes.remove(targetUUID);
        saveToFile();
    }

    /**
     * Vérifie si un joueur est muté (et non expiré).
     * Supprime automatiquement les mutes expirés.
     */
    public static boolean isMuted(UUID playerUUID) {
        ensureInitialized();
        MuteEntry entry = mutes.get(playerUUID);
        if (entry == null) return false;
        if (entry.isExpired()) {
            mutes.remove(playerUUID);
            saveToFile();
            return false;
        }
        return true;
    }

    public static MuteEntry getEntry(UUID playerUUID) {
        ensureInitialized();
        return mutes.get(playerUUID);
    }

    public static Map<UUID, MuteEntry> getAllMutes() {
        ensureInitialized();
        // Purge des expirés avant de retourner
        mutes.entrySet().removeIf(e -> e.getValue().isExpired());
        return new HashMap<>(mutes);
    }

    public static void reload() {
        dataFile = null;
        mutes.clear();
        ensureInitialized();
    }
}