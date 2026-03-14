package net.rp.rpessentials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de dernières connexions.
 * Stocke le dernier login ET logout de chaque joueur dans :
 *   world/data/oneriamod/lastconnection.json
 *
 * Configurable via ModerationConfig ([Last Connection] section).
 */
public class LastConnectionManager {

    // =========================================================================
    // INNER CLASS
    // =========================================================================

    public static class ConnectionEntry {
        public String mcName;
        public String lastLogin;   // null si jamais connecté
        public String lastLogout;  // null si jamais déconnecté (ou tracking désactivé)

        public ConnectionEntry() {}

        public ConnectionEntry(String mcName, String lastLogin, String lastLogout) {
            this.mcName = mcName;
            this.lastLogin = lastLogin;
            this.lastLogout = lastLogout;
        }
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private static final Map<UUID, ConnectionEntry> entries = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dataFile = null;

    // =========================================================================
    // INITIALISATION
    // =========================================================================

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File worldFolder = new File("world");
            if (!worldFolder.exists()) worldFolder = new File(".");

            File dataFolder = new File(worldFolder, "data/rpessentials");
            if (!dataFolder.exists()) dataFolder.mkdirs();

            dataFile = new File(dataFolder, "lastconnection.json");
            if (dataFile.exists()) loadFromFile();

            RpEssentials.LOGGER.info("[LastConnectionManager] Initialized - File: {}", dataFile.getAbsolutePath());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[LastConnectionManager] Failed to initialize", e);
        }
    }

    // =========================================================================
    // LOAD
    // =========================================================================

    private static void loadFromFile() {
        if (dataFile == null || !dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, ConnectionEntry>>(){}.getType();
            Map<String, ConnectionEntry> data = GSON.fromJson(reader, type);
            if (data != null) {
                entries.clear();
                for (Map.Entry<String, ConnectionEntry> e : data.entrySet()) {
                    try {
                        String key = e.getKey();
                        String uuidStr = key.contains(" ") ? key.substring(0, key.indexOf(' ')) : key;
                        entries.put(UUID.fromString(uuidStr), e.getValue());
                    } catch (IllegalArgumentException ex) {
                        RpEssentials.LOGGER.warn("[LastConnectionManager] Invalid UUID key: {}", e.getKey());
                    }
                }
                RpEssentials.LOGGER.info("[LastConnectionManager] Loaded {} entries", entries.size());
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[LastConnectionManager] Failed to load", e);
        }
    }

    // =========================================================================
    // SAVE (async)
    // =========================================================================

    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;

        Map<UUID, ConnectionEntry> snapshot = new HashMap<>(entries);
        File targetFile = dataFile;

        CompletableFuture.runAsync(() -> {
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                Map<String, ConnectionEntry> data = new LinkedHashMap<>();

                for (Map.Entry<UUID, ConnectionEntry> e : snapshot.entrySet()) {
                    UUID uuid = e.getKey();
                    ConnectionEntry entry = e.getValue();
                    String mcName = entry.mcName != null ? entry.mcName : "Unknown";

                    if (server != null) {
                        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                        if (online != null) mcName = online.getName().getString();
                    }
                    data.put(uuid.toString() + " (" + mcName + ")", entry);
                }

                try (FileWriter writer = new FileWriter(targetFile)) {
                    GSON.toJson(data, writer);
                }
                RpEssentials.LOGGER.debug("[LastConnectionManager] Saved {} entries", snapshot.size());
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[LastConnectionManager] Failed to save", e);
            }
        });
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static String getNow() {
        try {
            String format = ModerationConfig.LAST_CONNECTION_DATE_FORMAT.get();
            return new SimpleDateFormat(format).format(new Date());
        } catch (Exception e) {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
        }
    }

    private static boolean isEnabled() {
        try {
            return ModerationConfig.ENABLE_LAST_CONNECTION != null
                    && ModerationConfig.ENABLE_LAST_CONNECTION.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /** Appelé lors du login du joueur. */
    public static void recordLogin(ServerPlayer player) {
        if (!isEnabled()) return;
        ensureInitialized();

        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        String now = getNow();

        ConnectionEntry existing = entries.get(uuid);
        String lastLogout = existing != null ? existing.lastLogout : null;
        entries.put(uuid, new ConnectionEntry(name, now, lastLogout));
        saveToFile();
    }

    /** Appelé lors du logout du joueur. */
    public static void recordLogout(ServerPlayer player) {
        if (!isEnabled()) return;
        try {
            if (!ModerationConfig.LAST_CONNECTION_TRACK_LOGOUT.get()) return;
        } catch (IllegalStateException e) {
            return;
        }
        ensureInitialized();

        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        String now = getNow();

        ConnectionEntry existing = entries.get(uuid);
        String lastLogin = existing != null ? existing.lastLogin : null;
        entries.put(uuid, new ConnectionEntry(name, lastLogin, now));
        saveToFile();
    }

    /** Récupère l'entrée d'un joueur (null si inconnu). */
    public static ConnectionEntry getEntry(UUID uuid) {
        ensureInitialized();
        return entries.get(uuid);
    }

    /** Retourne toutes les entrées triées par lastLogin décroissant. */
    public static List<Map.Entry<UUID, ConnectionEntry>> getAllSortedByLogin() {
        ensureInitialized();
        List<Map.Entry<UUID, ConnectionEntry>> list = new ArrayList<>(entries.entrySet());
        list.sort((a, b) -> {
            String la = a.getValue().lastLogin;
            String lb = b.getValue().lastLogin;
            if (la == null && lb == null) return 0;
            if (la == null) return 1;
            if (lb == null) return -1;
            return lb.compareTo(la); // ordre décroissant
        });
        return list;
    }

    /** Recherche par nom de joueur (case-insensitive). */
    public static UUID findUUIDByName(String name) {
        ensureInitialized();
        for (Map.Entry<UUID, ConnectionEntry> e : entries.entrySet()) {
            if (name.equalsIgnoreCase(e.getValue().mcName)) return e.getKey();
        }
        return null;
    }

    /** Recharge les données depuis le fichier. */
    public static void reload() {
        dataFile = null;
        entries.clear();
        ensureInitialized();
    }
}
