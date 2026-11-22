package net.rp.rpessentials.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.*;
import net.rp.rpessentials.api.event.LicenseGrantedEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.core.RpEssentialsIO;
import net.rp.rpessentials.network.SyncNametagDataPacket;
import net.rp.rpessentials.util.TimeHelper;

public class LicenseManager {

    // =========================================================================
    // STRUCTURES
    // =========================================================================

    public static class AuditEntry {
        public String timestamp;
        public String action;       // "GIVE", "REVOKE", "GIVE_RP"
        public String staffName;
        public String staffUUID;
        public String targetName;
        public String targetUUID;
        public String profession;
        public String extra;        // Pour GIVE_RP: "30 jours, expire le 01/04/2025"

        public AuditEntry(String action, String staffName, String staffUUID,
                          String targetName, String targetUUID,
                          String profession, String extra) {
            this.timestamp = TimeHelper.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            this.action = action;
            this.staffName = staffName;
            this.staffUUID = staffUUID;
            this.targetName = targetName;
            this.targetUUID = targetUUID;
            this.profession = profession;
            this.extra = extra;
        }
    }

    public static class TempLicenseEntry {
        public String issuedAt;
        public String expiresAt;
        public String staffName;
        public String staffUUID;
        public String targetName;
        public String targetUUID;
        public String profession;
        public int durationDays;

        public TempLicenseEntry(String staffName, String staffUUID,
                                String targetName, String targetUUID,
                                String profession, int durationDays,
                                String issuedAt, String expiresAt) {
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.staffName = staffName;
            this.staffUUID = staffUUID;
            this.targetName = targetName;
            this.targetUUID = targetUUID;
            this.profession = profession;
            this.durationDays = durationDays;
        }
    }

    // =========================================================================
    // STATE
    // =========================================================================
    private static final Map<UUID, List<String>> playerLicenses = new ConcurrentHashMap<>();
    private static final List<AuditEntry> auditLog = Collections.synchronizedList(new ArrayList<>());
    private static final List<TempLicenseEntry> tempLicenses = Collections.synchronizedList(new ArrayList<>());

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File licenseFile = null;
    private static File auditFile   = null;
    private static File tempFile    = null;

    // =========================================================================
    // INITIALISATION
    // =========================================================================
    private static volatile boolean ready = false;

    private static void ensureInitialized() {
        if (!ready) init();
    }

    private static synchronized void init() {
        if (ready) return;
        try {
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            licenseFile = new File(dataFolder, "licenses.json");
            auditFile   = new File(dataFolder, "license-audit.json");
            tempFile    = new File(dataFolder, "licenses-temp.json");

            if (licenseFile.exists()) loadFromFile();
            if (auditFile.exists())   loadAuditFromFile();
            if (tempFile.exists())    loadTempFromFile();
            ready = true;
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[LicenseManager] Failed to initialize", e);
        }
    }

    // =========================================================================
    // LOAD
    // =========================================================================
    private static void loadFromFile() {
        if (licenseFile == null || !licenseFile.exists()) return;

        try (FileReader reader = new FileReader(licenseFile)) {
            Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
            Map<String, List<String>> data = GSON.fromJson(reader, type);

            if (data != null) {
                playerLicenses.clear();
                for (Map.Entry<String, List<String>> entry : data.entrySet()) {
                    try {
                        String key = entry.getKey();
                        String uuidStr = key.contains(" ") ? key.substring(0, key.indexOf(' ')) : key;
                        UUID uuid = UUID.fromString(uuidStr);
                        playerLicenses.put(uuid, new ArrayList<>(entry.getValue()));
                    } catch (IllegalArgumentException e) {
                        RpEssentials.LOGGER.warn("[LicenseManager] Invalid UUID in key: {}", entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[LicenseManager] Failed to load licenses", e);
            RpEssentialsIO.quarantine(licenseFile);
        }
    }

    private static void loadAuditFromFile() {
        List<AuditEntry> data = net.rp.rpessentials.core.JsonStore.read(auditFile,
                new TypeToken<List<AuditEntry>>(){}.getType(), GSON, "LicenseManager");
        if (data != null) {
            auditLog.clear();
            auditLog.addAll(data);
        }
    }

    private static void loadTempFromFile() {
        List<TempLicenseEntry> data = net.rp.rpessentials.core.JsonStore.read(tempFile,
                new TypeToken<List<TempLicenseEntry>>(){}.getType(), GSON, "LicenseManager");
        if (data != null) {
            tempLicenses.clear();
            tempLicenses.addAll(data);
        }
    }

    // =========================================================================
    // SAVE (async)
    // =========================================================================
    private static void saveToFile() {
        ensureInitialized();
        if (licenseFile == null) return;

        Map<UUID, List<String>> snapshot = new HashMap<>(playerLicenses);
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();

        Map<String, List<String>> data = new HashMap<>();
        for (Map.Entry<UUID, List<String>> entry : snapshot.entrySet()) {
            UUID uuid = entry.getKey();
            String mcName = net.rp.rpessentials.util.NameCache.getOrUnknown(uuid);
            data.put(uuid.toString() + " (" + mcName + ")", new ArrayList<>(entry.getValue()));
        }

        File targetFile = licenseFile;
        RpEssentialsIO.saveJson(targetFile, data, GSON);

        RpEssentials.LOGGER.debug(
                "[LicenseManager] Queued licenses save for {} players",
                data.size()
        );
    }

    private static void saveAuditToFile() {
        if (auditFile == null) return;

        List<AuditEntry> snapshot = new ArrayList<>(auditLog);
        File targetFile = auditFile;

        RpEssentialsIO.saveJson(targetFile, snapshot, GSON);

        RpEssentials.LOGGER.debug(
                "[LicenseManager] Queued {} audit entries",
                snapshot.size()
        );
    }

    private static void saveTempToFile() {
        if (tempFile == null) return;

        List<TempLicenseEntry> snapshot = new ArrayList<>(tempLicenses);
        File targetFile = tempFile;

        RpEssentialsIO.saveJson(targetFile, snapshot, GSON);

        RpEssentials.LOGGER.debug(
                "[LicenseManager] Queued {} temp licenses",
                snapshot.size()
        );
    }

    // =========================================================================
    // PUBLIC API — LICENSES
    // =========================================================================

    public static void addLicense(UUID playerUUID, String profession) {
        ensureInitialized();
        List<String> licenses = playerLicenses.computeIfAbsent(playerUUID, k -> new ArrayList<>());
        if (licenses.contains(profession)) return;
        licenses.add(profession);
        saveToFile();
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(playerUUID);
            if (target != null) SyncNametagDataPacket.broadcastForPlayer(target);
        }
    }

    public static void removeLicense(UUID playerUUID, String profession) {
        ensureInitialized();
        List<String> licenses = playerLicenses.get(playerUUID);
        boolean removed = false;
        if (licenses != null) {
            removed = licenses.remove(profession);
            if (licenses.isEmpty()) playerLicenses.remove(playerUUID);
            saveToFile();
        }
        String uuidStr = playerUUID.toString();
        if (tempLicenses.removeIf(e -> e.targetUUID.equals(uuidStr) && e.profession.equalsIgnoreCase(profession))) {
            saveTempToFile();
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(playerUUID);
            if (target != null) SyncNametagDataPacket.broadcastForPlayer(target);
        }
        if (removed) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new net.rp.rpessentials.api.event.LicenseRevokedEvent(playerUUID, profession));
        }
    }

    public static List<String> getLicenses(UUID playerUUID) {
        ensureInitialized();
        return new ArrayList<>(playerLicenses.getOrDefault(playerUUID, new ArrayList<>()));
    }

    public static boolean hasLicense(UUID playerUUID, String profession) {
        ensureInitialized();
        List<String> licenses = playerLicenses.get(playerUUID);
        return licenses != null && licenses.contains(profession);
    }

    public static Map<UUID, List<String>> getAllLicenses() {
        ensureInitialized();
        Map<UUID, List<String>> result = new HashMap<>();
        for (Map.Entry<UUID, List<String>> entry : playerLicenses.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Supprime une entree precise de licenses-temp.json.
     * Compare par targetUUID + profession + expiresAt pour etre precis.
     */
    public static void removeTempLicense(TempLicenseEntry toRemove) {
        ensureInitialized();
        tempLicenses.removeIf(e ->
                e.targetUUID.equals(toRemove.targetUUID) &&
                        e.profession.equals(toRemove.profession) &&
                        e.expiresAt.equals(toRemove.expiresAt)
        );
        saveTempToFile();
    }

    /**
     * Retourne la date d'expiration d'un permis RP pour un joueur,
     * ou null si la profession n'est pas temporaire.
     */
    public static String getTempExpirationDate(UUID playerUUID, String profession) {
        ensureInitialized();
        String uuidStr = playerUUID.toString();
        for (TempLicenseEntry entry : tempLicenses) {
            if (entry.targetUUID.equals(uuidStr) && entry.profession.equalsIgnoreCase(profession)) {
                return entry.expiresAt;
            }
        }
        return null;
    }

    public static synchronized void reload() {
        ready = false;
        licenseFile = null;
        auditFile = null;
        tempFile = null;
        playerLicenses.clear();
        auditLog.clear();
        tempLicenses.clear();
        init();
    }

    // =========================================================================
    // PUBLIC API — AUDIT LOG
    // =========================================================================

    /**
     * Enregistre une action dans l'audit log.
     * @param action     "GIVE", "REVOKE" ou "GIVE_RP"
     * @param staff      Le joueur staff qui effectue l'action (null si console)
     * @param target     Le joueur qui reçoit/perd le permis
     * @param profession L'ID du métier
     * @param extra      Infos supplémentaires (ex: durée pour GIVE_RP), null sinon
     */
    public static void logAction(String action, ServerPlayer staff, ServerPlayer target,
                                 String profession, String extra) {
        ensureInitialized();

        String staffName = staff != null ? staff.getName().getString() : net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE);
        String staffUUID = staff != null ? staff.getUUID().toString() : "console";

        AuditEntry entry = new AuditEntry(
                action,
                staffName, staffUUID,
                target.getName().getString(), target.getUUID().toString(),
                profession,
                extra
        );

        auditLog.add(entry);
        saveAuditToFile();
        net.rp.rpessentials.moderation.HistoryManager.record(target.getUUID(), target.getName().getString(),
                net.rp.rpessentials.moderation.HistoryManager.LICENSE, staffName,
                action + " " + profession + (extra != null && !extra.isEmpty() ? " - " + extra : ""));

        RpEssentials.LOGGER.info("[LicenseAudit] {} | {} -> {} | {} | {}",
                action, staffName, target.getName().getString(), profession,
                extra != null ? extra : "");
    }

    /**
     * Enregistre une action systeme dans l'audit log (sans joueur staff humain).
     * Utilise pour les expirations automatiques (EXPIRE_RP).
     *
     * @param action      "EXPIRE_RP"
     * @param targetName  Nom du joueur cible
     * @param targetUUID  UUID du joueur cible (String)
     * @param profession  ID du metier
     * @param extra       Infos supplementaires (ex: "Expire le 31/03/2025")
     */
    public static void logActionSystem(String action, String targetName, String targetUUID,
                                       String profession, String extra) {
        ensureInitialized();
        AuditEntry entry = new AuditEntry(action, net.rp.rpessentials.moderation.HistoryManager.system(), "system",
                targetName, targetUUID, profession, extra);
        auditLog.add(entry);
        saveAuditToFile();
        try {
            net.rp.rpessentials.moderation.HistoryManager.record(UUID.fromString(targetUUID), targetName,
                    net.rp.rpessentials.moderation.HistoryManager.LICENSE, null,
                    action + " " + profession + (extra != null && !extra.isEmpty() ? " - " + extra : ""));
        } catch (IllegalArgumentException ignored) {}
        RpEssentials.LOGGER.info("[LicenseAudit] {} | System -> {} | {} | {}",
                action, targetName, profession, extra != null ? extra : "");
    }

    public static List<AuditEntry> getAuditLog() {
        ensureInitialized();
        return new ArrayList<>(auditLog);
    }

    // =========================================================================
    // PUBLIC API — TEMP LICENSES
    // =========================================================================

    /**
     * Enregistre un permis temporaire dans licenses-temp.json.
     */
    public static void addTempLicense(ServerPlayer staff, ServerPlayer target,
                                      String profession, int durationDays,
                                      String issuedAt, String expiresAt) {
        ensureInitialized();

        String staffName = staff != null ? staff.getName().getString() : net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.UI_CONSOLE);
        String staffUUID = staff != null ? staff.getUUID().toString() : "console";

        TempLicenseEntry entry = new TempLicenseEntry(
                staffName, staffUUID,
                target.getName().getString(), target.getUUID().toString(),
                profession, durationDays,
                issuedAt, expiresAt
        );

        tempLicenses.add(entry);
        saveTempToFile();
    }

    public static List<TempLicenseEntry> getAllTempLicenses() {
        ensureInitialized();
        return new ArrayList<>(tempLicenses);
    }
}