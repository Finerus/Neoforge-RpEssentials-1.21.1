package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;

import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Gestionnaire d'avertissements (warns) joueurs.
 * Stocke les warns dans : world/data/oneriamod/warns.json
 *
 * Fonctionnalités :
 *  - Warn permanent ou temporaire (avec expiration)
 *  - Notification à la connexion du joueur
 *  - Commandes staff : add, temp, remove, list, info
 *  - Commande joueur : /mywarn (voir ses propres warns)
 *
 * Configurable via ModerationConfig ([Warn System] section).
 */
public class WarnManager {

    // =========================================================================
    // INNER CLASS
    // =========================================================================

    public static class WarnEntry {
        public String id;
        public String targetUUID;
        public String targetName;
        public String issuerUUID;   // null = console
        public String issuerName;
        public String reason;
        public long issuedAt;       // epoch millis
        public Long expiresAt;      // null = permanent

        public WarnEntry() {}

        public WarnEntry(String id, String targetUUID, String targetName,
                         String issuerUUID, String issuerName,
                         String reason, long issuedAt, Long expiresAt) {
            this.id = id;
            this.targetUUID = targetUUID;
            this.targetName = targetName;
            this.issuerUUID = issuerUUID;
            this.issuerName = issuerName;
            this.reason = reason;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }

        // ---- Helpers ----

        public boolean isExpired() {
            return expiresAt != null && System.currentTimeMillis() > expiresAt;
        }

        public boolean isPermanent() {
            return expiresAt == null;
        }

        public String getFormattedDate() {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(issuedAt));
        }

        /**
         * Retourne une chaîne lisible de l'expiration.
         * Exemples : "Permanent", "Expiré", "2h 30min restantes", "3j 4h restants"
         */
        public String getFormattedExpiry() {
            if (expiresAt == null) return "Permanent";
            if (isExpired()) return "§cExpiré";
            long remaining = expiresAt - System.currentTimeMillis();
            long totalMinutes = remaining / 60_000;
            long days    = totalMinutes / 1440;
            long hours   = (totalMinutes % 1440) / 60;
            long minutes = totalMinutes % 60;

            if (days > 0) return days + "j " + hours + "h restants";
            if (hours > 0) return hours + "h " + minutes + "min restantes";
            return minutes + "min restantes";
        }
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private static final List<WarnEntry> warns = Collections.synchronizedList(new ArrayList<>());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dataFile = null;

    /**
     * Compteur auto-incrémental pour les IDs de warn.
     * Repart du max chargé +1 pour éviter les collisions.
     */
    private static final AtomicInteger warnCounter = new AtomicInteger(1);

    // =========================================================================
    // INITIALISATION
    // =========================================================================

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            dataFile = new File(dataFolder, "warns.json");
            if (dataFile.exists()) loadFromFile();

            RpEssentials.LOGGER.info("[WarnManager] Initialized - File: {}", dataFile.getAbsolutePath());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[WarnManager] Failed to initialize", e);
        }
    }

    // =========================================================================
    // LOAD
    // =========================================================================

    private static void loadFromFile() {
        if (dataFile == null || !dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<List<WarnEntry>>(){}.getType();
            List<WarnEntry> data = GSON.fromJson(reader, type);
            if (data != null) {
                warns.clear();
                warns.addAll(data);
                // Repositionner le compteur après le max existant
                int maxId = data.stream()
                        .mapToInt(w -> {
                            try { return Integer.parseInt(w.id); }
                            catch (Exception ignored) { return 0; }
                        })
                        .max().orElse(0);
                warnCounter.set(maxId + 1);
                RpEssentials.LOGGER.info("[WarnManager] Loaded {} warns (next id={})", data.size(), maxId + 1);
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[WarnManager] Failed to load warns", e);
        }
    }

    // =========================================================================
    // SAVE (async)
    // =========================================================================

    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;

        List<WarnEntry> snapshot = new ArrayList<>(warns);
        File targetFile = dataFile;

        CompletableFuture.runAsync(() -> {
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileWriter writer = new FileWriter(targetFile)) {
                    GSON.toJson(snapshot, writer);
                }
                RpEssentials.LOGGER.debug("[WarnManager] Saved {} warns", snapshot.size());
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[WarnManager] Failed to save warns", e);
            }
        });
    }

    // =========================================================================
    // PUBLIC API — WRITE
    // =========================================================================

    /**
     * Ajoute un warn. Retourne l'ID généré.
     *
     * @param targetUUID  UUID de la cible
     * @param targetName  Nom MC de la cible (pour lisibilité du JSON)
     * @param issuerUUID  UUID de l'émetteur, null si console
     * @param issuerName  Nom de l'émetteur
     * @param reason      Raison du warn
     * @param expiresAt   Timestamp d'expiration (epoch ms), null = permanent
     */
    public static String addWarn(UUID targetUUID, String targetName,
                                 UUID issuerUUID, String issuerName,
                                 String reason, Long expiresAt) {
        ensureInitialized();
        String id = String.valueOf(warnCounter.getAndIncrement());
        WarnEntry entry = new WarnEntry(
                id,
                targetUUID.toString(),
                targetName,
                issuerUUID != null ? issuerUUID.toString() : null,
                issuerName,
                reason,
                System.currentTimeMillis(),
                expiresAt
        );
        warns.add(entry);
        saveToFile();
        RpEssentials.LOGGER.info("[WarnManager] Warn #{} added for {} by {} — reason: {}", id, targetName, issuerName, reason);
        return id;
    }

    /**
     * Recalcule le compteur d'ID depuis le maximum des IDs encore présents en mémoire.
     * Appelé après toute suppression pour que le prochain warn obtienne max+1.
     * Exemples :
     *   warns = [#1, #3]  → counter = 4
     *   warns = []        → counter = 1
     */
    private static void recalculateCounter() {
        int max = warns.stream()
                .mapToInt(w -> {
                    try { return Integer.parseInt(w.id); }
                    catch (NumberFormatException ignored) { return 0; }
                })
                .max().orElse(0);
        warnCounter.set(max + 1);
        RpEssentials.LOGGER.debug("[WarnManager] Counter recalculated → next id={}", max + 1);
    }

    /**
     * Supprime un warn par ID. Retourne true si un warn a été supprimé.
     * Le compteur d'ID est recalculé depuis le max restant.
     */
    public static boolean removeWarn(String warnId) {
        ensureInitialized();
        boolean removed = warns.removeIf(w -> w.id.equals(warnId));
        if (removed) {
            recalculateCounter();
            saveToFile();
            RpEssentials.LOGGER.info("[WarnManager] Warn #{} removed", warnId);
        }
        return removed;
    }

    /**
     * Supprime tous les warns d'un joueur. Retourne le nombre supprimé.
     * Le compteur d'ID est recalculé depuis le max restant.
     */
    public static int clearWarns(UUID playerUUID) {
        ensureInitialized();
        String uuidStr = playerUUID.toString();
        int before = warns.size();
        warns.removeIf(w -> w.targetUUID.equals(uuidStr));
        int removed = before - warns.size();
        if (removed > 0) {
            recalculateCounter();
            saveToFile();
        }
        return removed;
    }

    /**
     * Supprime tous les warns expirés. Retourne le nombre purgé.
     * Le compteur d'ID est recalculé depuis le max restant.
     */
    public static int purgeExpiredWarns() {
        ensureInitialized();
        int before = warns.size();
        warns.removeIf(WarnEntry::isExpired);
        int removed = before - warns.size();
        if (removed > 0) {
            recalculateCounter();
            saveToFile();
            RpEssentials.LOGGER.info("[WarnManager] Purged {} expired warns", removed);
        }
        return removed;
    }

    // =========================================================================
    // PUBLIC API — READ
    // =========================================================================

    /** Retourne tous les warns d'un joueur (y compris expirés). */
    public static List<WarnEntry> getWarns(UUID playerUUID) {
        ensureInitialized();
        String uuidStr = playerUUID.toString();
        return warns.stream()
                .filter(w -> w.targetUUID.equals(uuidStr))
                .collect(Collectors.toList());
    }

    /** Retourne uniquement les warns actifs (non expirés) d'un joueur. */
    public static List<WarnEntry> getActiveWarns(UUID playerUUID) {
        return getWarns(playerUUID).stream()
                .filter(w -> !w.isExpired())
                .collect(Collectors.toList());
    }

    /** Retourne un warn par son ID. */
    public static Optional<WarnEntry> getWarnById(String warnId) {
        ensureInitialized();
        return warns.stream().filter(w -> w.id.equals(warnId)).findFirst();
    }

    /** Retourne tous les warns (toutes cibles). */
    public static List<WarnEntry> getAll() {
        ensureInitialized();
        return Collections.unmodifiableList(warns);
    }

    /** Compte les warns actifs d'un joueur. */
    public static int countActiveWarns(UUID playerUUID) {
        return getActiveWarns(playerUUID).size();
    }

    /** Recharge depuis le fichier (après reload config). */
    public static void reload() {
        dataFile = null;
        warns.clear();
        warnCounter.set(1);
        ensureInitialized();
    }
}