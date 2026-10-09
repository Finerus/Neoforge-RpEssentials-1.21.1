package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.io.*;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Gestionnaire de dernières connexions + playtime cumulatif.
 *
 * Le champ {@code totalPlaytimeMs} est ajouté à {@link ConnectionEntry}.
 * Il est rétrocompatible : les anciens fichiers JSON sans ce champ
 * le liront comme 0 (valeur par défaut de long en Java/Gson).
 */
public class LastConnectionManager {

    // =========================================================================
    // INNER CLASS
    // =========================================================================

    public static class ConnectionEntry {
        public String mcName;
        public String lastLogin;
        public String lastLogout;
        /** Playtime cumulatif en millisecondes. Ajouté en 4.1.6, défaut 0 (rétrocompat). */
        public long totalPlaytimeMs = 0L;

        public ConnectionEntry() {}

        public ConnectionEntry(String mcName, String lastLogin, String lastLogout) {
            this.mcName    = mcName;
            this.lastLogin = lastLogin;
            this.lastLogout = lastLogout;
        }

        public ConnectionEntry(String mcName, String lastLogin, String lastLogout, long totalPlaytimeMs) {
            this.mcName         = mcName;
            this.lastLogin      = lastLogin;
            this.lastLogout     = lastLogout;
            this.totalPlaytimeMs = totalPlaytimeMs;
        }
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private static final Map<UUID, ConnectionEntry> entries = new ConcurrentHashMap<>();
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
            dataFile = new File(dataFolder, "lastconnection.json");
            if (dataFile.exists()) loadFromFile();
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[LastConnectionManager] Failed to initialize", e);
        }
    }

    // =========================================================================
    // AUTO-UNWHITELIST
    // =========================================================================
    private static java.time.LocalDate lastUnwhitelistDate = null;

    public static void tickAutoUnwhitelist(MinecraftServer server) {
        try {
            if (!ModerationConfig.AUTO_UNWHITELIST_ENABLED.get()) return;
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) return;
        } catch (IllegalStateException e) { return; }

        if (java.time.LocalTime.now().getHour() != 0) return;
        java.time.LocalDate today = java.time.LocalDate.now();
        if (today.equals(lastUnwhitelistDate)) return;
        lastUnwhitelistDate = today;
        RpEssentials.LOGGER.info("[AutoUnwhitelist] Starting daily sweep...");

        int thresholdDays;
        List<? extends String> extraCmds;
        String dateFormat;
        List<String> roles;
        try {
            thresholdDays = ModerationConfig.AUTO_UNWHITELIST_DAYS.get();
            extraCmds = ModerationConfig.AUTO_UNWHITELIST_EXTRA_COMMANDS.get();
            dateFormat = ModerationConfig.LAST_CONNECTION_DATE_FORMAT.get();
            roles = new ArrayList<>(RpEssentialsConfig.ROLES.get());
        } catch (IllegalStateException e) { return; }

        long thresholdMs = (long) thresholdDays * 86400_000L;
        long nowMs = System.currentTimeMillis();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(dateFormat);

        for (Map.Entry<UUID, ConnectionEntry> e : new ArrayList<>(entries.entrySet())) {
            UUID uuid = e.getKey();
            ConnectionEntry entry = e.getValue();
            if (entry.lastLogin == null || entry.mcName == null) continue;
            if (server.getPlayerList().getPlayer(uuid) != null) continue;
            if (!server.getPlayerList().isWhiteListed(new com.mojang.authlib.GameProfile(uuid, entry.mcName))) continue;

            long lastLoginMs;
            try { lastLoginMs = sdf.parse(entry.lastLogin).getTime(); }
            catch (java.text.ParseException ex) { continue; }
            if (nowMs - lastLoginMs < thresholdMs) continue;

            final String playerName = entry.mcName;
            final long inactiveDays = (nowMs - lastLoginMs) / 86400_000L;
            final long totalPlaytimeMs = getTotalPlaytimeMs(uuid);
            final List<String> licenses = net.rp.rpessentials.profession.LicenseManager.getLicenses(uuid);
            final String lastLogin = entry.lastLogin;

            Runnable remove = () -> {
                AutoUnwhitelistHistory.record(playerName, uuid.toString(), lastLogin, totalPlaytimeMs, licenses, inactiveDays);
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), "whitelist remove " + playerName);

                for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
                    if (!RpEssentialsPermissions.isStaff(staff)) continue;
                    net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component.literal(
                            MessagesConfig.get(MessagesConfig.AUTO_UNWHITELIST_STAFF_NOTIFY,
                                    "player", playerName, "days", String.valueOf(inactiveDays)));
                    net.minecraft.network.chat.MutableComponent undo =
                            net.minecraft.network.chat.Component.literal("§a[Undo]")
                                    .withStyle(s -> s
                                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                                    "/whitelist add " + playerName))
                                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                                    net.minecraft.network.chat.Component.literal("Re-whitelist " + playerName))));
                    staff.sendSystemMessage(msg.append(undo));
                }
                for (String cmd : extraCmds) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            cmd.replace("{player}", playerName).replace("{uuid}", uuid.toString()));
                }
                RpEssentials.LOGGER.info("[AutoUnwhitelist] Removed {} (inactive {} days)", playerName, inactiveDays);
            };

            checkBypassAsync(uuid, roles, bypass -> { if (!bypass) server.execute(remove); });
        }
    }

    private static void checkBypassAsync(UUID uuid, List<String> roles, java.util.function.Consumer<Boolean> callback) {
        try {
            net.luckperms.api.LuckPermsProvider.get().getUserManager().loadUser(uuid)
                    .whenComplete((user, err) ->
                            callback.accept(err == null && user != null && hasBypass(user.getPrimaryGroup(), roles)));
        } catch (NoClassDefFoundError | IllegalStateException ex) {
            callback.accept(false);
        }
    }

    private static boolean hasBypass(String primaryGroup, List<String> roles) {
        String primary = primaryGroup.toLowerCase();
        for (String entry : roles) {
            String[] parts = entry.split(";", 3);
            if (!parts[0].trim().equalsIgnoreCase(primary)) continue;
            if (parts.length < 3) return false;
            for (String p : parts[2].split(",")) {
                if (p.trim().equalsIgnoreCase("bypassAutoUnwhitelist")) return true;
            }
            return false;
        }
        return false;
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
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[LastConnectionManager] Failed to load", e);
            RpEssentialsIO.quarantine(dataFile);
        }
    }

    // =========================================================================
    // SAVE (async)
    // =========================================================================
    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;

        Map<UUID, ConnectionEntry> snapshot = new HashMap<>(entries);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        Map<String, ConnectionEntry> data = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, ConnectionEntry> e : snapshot.entrySet()) {
            UUID uuid = e.getKey();
            String mcName = e.getValue().mcName != null ? e.getValue().mcName : "Unknown";
            if (server != null) {
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != null) mcName = online.getName().getString();
            }
            data.put(uuid.toString() + " (" + mcName + ")", e.getValue());
        }

        File targetFile = dataFile;
        RpEssentialsIO.saveJson(targetFile, data, GSON);
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
        try { return ModerationConfig.ENABLE_LAST_CONNECTION != null && ModerationConfig.ENABLE_LAST_CONNECTION.get(); }
        catch (IllegalStateException e) { return false; }
    }

    // =========================================================================
    // PUBLIC API — CONNEXIONS
    // =========================================================================
    public static void recordLogin(ServerPlayer player) {
        if (!isEnabled()) return;
        ensureInitialized();
        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        String now  = getNow();
        ConnectionEntry existing = entries.get(uuid);
        long prevPlaytime = existing != null ? existing.totalPlaytimeMs : 0L;
        entries.put(uuid, new ConnectionEntry(name, now,
                existing != null ? existing.lastLogout : null, prevPlaytime));
        saveToFile();
    }

    public static void recordLogout(ServerPlayer player) {
        if (!isEnabled()) return;
        try { if (!ModerationConfig.LAST_CONNECTION_TRACK_LOGOUT.get()) return; }
        catch (IllegalStateException e) { return; }
        ensureInitialized();
        UUID uuid = player.getUUID();
        String name = player.getName().getString();
        String now  = getNow();
        ConnectionEntry existing = entries.get(uuid);
        long prevPlaytime = existing != null ? existing.totalPlaytimeMs : 0L;
        entries.put(uuid, new ConnectionEntry(name,
                existing != null ? existing.lastLogin : null, now, prevPlaytime));
        saveToFile();
    }

    // =========================================================================
    // PUBLIC API — PLAYTIME
    // =========================================================================
    /**
     * Ajoute de la durée au playtime cumulatif d'un joueur.
     * Appelé par {@link net.rp.rpessentials.moderation.PlaytimeManager#onLogout}.
     */
    public static void addPlaytime(UUID uuid, long durationMs) {
        ensureInitialized();
        ConnectionEntry e = entries.get(uuid);
        if (e == null) return;
        e.totalPlaytimeMs += durationMs;
        saveToFile();
    }

    public static void addPlaytimeSilent(UUID uuid, long durationMs) {
        ensureInitialized();
        ConnectionEntry e = entries.get(uuid);
        if (e == null) return;
        e.totalPlaytimeMs += durationMs;
    }

    public static void flushToDisk() {
        saveToFile();
    }

    /**
     * Retourne le playtime cumulatif persisté (sans la session courante).
     * Pour le total incluant la session courante, voir {@link PlaytimeManager#getTotalPlaytimeMs}.
     */
    public static long getTotalPlaytimeMs(UUID uuid) {
        ensureInitialized();
        ConnectionEntry e = entries.get(uuid);
        return e != null ? e.totalPlaytimeMs : 0L;
    }

    // =========================================================================
    // PUBLIC API — LECTURE
    // =========================================================================
    public static ConnectionEntry getEntry(UUID uuid) {
        ensureInitialized();
        return entries.get(uuid);
    }

    public static List<Map.Entry<UUID, ConnectionEntry>> getAllSortedByLogin() {
        ensureInitialized();
        SimpleDateFormat parser;
        try { parser = new SimpleDateFormat(ModerationConfig.LAST_CONNECTION_DATE_FORMAT.get()); }
        catch (Exception e) { parser = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"); }
        final SimpleDateFormat fmt = parser;
        List<Map.Entry<UUID, ConnectionEntry>> list = new ArrayList<>(entries.entrySet());
        list.sort((a, b) -> Long.compare(loginMillis(b.getValue(), fmt), loginMillis(a.getValue(), fmt)));
        return list;
    }

    private static long loginMillis(ConnectionEntry entry, SimpleDateFormat fmt) {
        if (entry.lastLogin == null) return Long.MIN_VALUE;
        try { return fmt.parse(entry.lastLogin).getTime(); }
        catch (java.text.ParseException e) { return Long.MIN_VALUE; }
    }

    public static UUID findUUIDByName(String name) {
        ensureInitialized();
        for (Map.Entry<UUID, ConnectionEntry> e : entries.entrySet()) {
            if (name.equalsIgnoreCase(e.getValue().mcName)) return e.getKey();
        }
        return null;
    }

    public static String resolveName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        ConnectionEntry entry = getEntry(uuid);
        if (entry != null && entry.mcName != null) return entry.mcName;
        if (server.getProfileCache() != null) {
            Optional<String> cached = server.getProfileCache().get(uuid).map(p -> p.getName());
            if (cached.isPresent()) return cached.get();
        }
        return uuid.toString().substring(0, 8);
    }

    public static void reload() {
        dataFile = null;
        entries.clear();
        ensureInitialized();
    }
}