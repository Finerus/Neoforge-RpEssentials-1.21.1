package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ModerationConfig;

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
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            dataFile = new File(dataFolder, "lastconnection.json");
            if (dataFile.exists()) loadFromFile();

            RpEssentials.LOGGER.info("[LastConnectionManager] Initialized - File: {}", dataFile.getAbsolutePath());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[LastConnectionManager] Failed to initialize", e);
        }
    }

    private static boolean hasDoneUnwhitelistToday = false;
    private static int     lastUnwhitelistDay       = -1;

    public static void tickAutoUnwhitelist(MinecraftServer server, int hour, int minute) {
        try {
            if (!ModerationConfig.AUTO_UNWHITELIST_ENABLED.get()) return;
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get())   return;
        } catch (IllegalStateException e) { return; }

        int today = java.time.LocalDate.now().getDayOfYear();
        if (today != lastUnwhitelistDay) {
            hasDoneUnwhitelistToday = false;
            lastUnwhitelistDay = today;
        }
        if (hasDoneUnwhitelistToday) return;
        if (hour != 0 || minute > 1)  return;

        hasDoneUnwhitelistToday = true;
        RpEssentials.LOGGER.info("[AutoUnwhitelist] Starting daily sweep...");

        int thresholdDays;
        List<? extends String> extraCmds;
        String dateFormat;
        try {
            thresholdDays = ModerationConfig.AUTO_UNWHITELIST_DAYS.get();
            extraCmds     = ModerationConfig.AUTO_UNWHITELIST_EXTRA_COMMANDS.get();
            dateFormat    = ModerationConfig.LAST_CONNECTION_DATE_FORMAT.get();
        } catch (IllegalStateException e) { return; }

        long thresholdMs = (long) thresholdDays * 86400_000L;
        long nowMs       = System.currentTimeMillis();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(dateFormat);
        int[] removed = {0};

        for (Map.Entry<UUID, ConnectionEntry> e : new ArrayList<>(entries.entrySet())) {
            UUID uuid = e.getKey();
            ConnectionEntry entry = e.getValue();
            if (entry.lastLogin == null || entry.mcName == null) continue;
            if (server.getPlayerList().getPlayer(uuid) != null)  continue;
            if (!server.getPlayerList().isWhiteListed(
                    new com.mojang.authlib.GameProfile(uuid, entry.mcName))) continue;

            long lastLoginMs;
            try { lastLoginMs = sdf.parse(entry.lastLogin).getTime(); }
            catch (java.text.ParseException ex) { continue; }

            if (nowMs - lastLoginMs < thresholdMs) continue;

            final String playerName   = entry.mcName;
            final UUID   playerUUID   = uuid;
            final long   inactiveDays = (nowMs - lastLoginMs) / 86400_000L;

            server.execute(() -> {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), "whitelist remove " + playerName);

                for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
                    if (!RpEssentialsPermissions.isStaff(staff)) continue;
                    net.minecraft.network.chat.MutableComponent msg =
                            net.minecraft.network.chat.Component.literal(
                                    MessagesConfig.get(MessagesConfig.AUTO_UNWHITELIST_STAFF_NOTIFY,
                                            "player", playerName,
                                            "days",   String.valueOf(inactiveDays)));
                    net.minecraft.network.chat.MutableComponent undo =
                            net.minecraft.network.chat.Component.literal("§a[Annuler]")
                                    .withStyle(s -> s
                                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                                    "/whitelist add " + playerName))
                                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                                    net.minecraft.network.chat.Component.literal(
                                                            "Re-whitelist " + playerName))));
                    staff.sendSystemMessage(msg.append(undo));
                }

                for (String cmd : extraCmds) {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(),
                            cmd.replace("{player}", playerName)
                                    .replace("{uuid}",   playerUUID.toString()));
                }
                RpEssentials.LOGGER.info("[AutoUnwhitelist] Removed {} — inactive {} days",
                        playerName, inactiveDays);
            });
            removed[0]++;
        }
        RpEssentials.LOGGER.info("[AutoUnwhitelist] Sweep done — {} player(s) removed.", removed[0]);
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
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        Map<String, ConnectionEntry> data = new java.util.LinkedHashMap<>();
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

        File targetFile = dataFile;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (java.io.FileWriter writer = new java.io.FileWriter(targetFile)) {
                    GSON.toJson(data, writer);
                }
                RpEssentials.LOGGER.debug("[LastConnectionManager] Saved {} entries", data.size());
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
