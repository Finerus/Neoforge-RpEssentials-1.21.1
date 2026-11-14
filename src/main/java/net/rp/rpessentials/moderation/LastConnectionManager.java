package net.rp.rpessentials.moderation;

import net.rp.rpessentials.lang.ServerLang;
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
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.core.RpEssentialsIO;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.util.TimeHelper;

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
    /** Days of daily activity kept per player (enough for the "last 30 days" stats). */
    public static final int KEEP_DAYS = 35;

    public static class ConnectionEntry {
        public transient String parsedFrom;
        public transient long parsedMs;
        public String mcName;
        public String lastLogin;
        public String lastLogout;
        /** Playtime cumulatif en millisecondes. Ajouté en 4.1.6, défaut 0 (rétrocompat). */
        public long totalPlaytimeMs = 0L;
        public long lastLoginMs = 0L;
        /** First login seen by the mod (epoch ms). 0 = unknown (player not seen since 5.1.0). */
        public long firstLoginMs = 0L;
        /** Number of logins since the mod tracks them. */
        public int loginCount = 0;
        /** Logins per day (yyyy-MM-dd), last {@link #KEEP_DAYS} days only. */
        public Map<String, Integer> dailyLogins = new TreeMap<>();
        /** Playtime per day in ms (yyyy-MM-dd), last {@link #KEEP_DAYS} days only. */
        public Map<String, Long> dailyPlaytimeMs = new TreeMap<>();

        public ConnectionEntry() {}

        /** Keeps the activity data of the previous version of this entry. */
        void copyActivity(ConnectionEntry old) {
            if (old == null) return;
            firstLoginMs = old.firstLoginMs;
            loginCount = old.loginCount;
            if (old.dailyLogins != null) dailyLogins = new TreeMap<>(old.dailyLogins);
            if (old.dailyPlaytimeMs != null) dailyPlaytimeMs = new TreeMap<>(old.dailyPlaytimeMs);
        }

        /** Copy written to disk, so the save thread never reads a map that the game is changing. */
        ConnectionEntry snapshot() {
            ConnectionEntry c = new ConnectionEntry(mcName, lastLogin, lastLogout, totalPlaytimeMs);
            c.lastLoginMs = lastLoginMs;
            c.copyActivity(this);
            return c;
        }

        /** Logins during the last {@code days} days (today included). */
        public int loginsLastDays(int days) {
            if (dailyLogins == null) return 0;
            String from = TimeHelper.today().minusDays(days - 1L).toString();
            int n = 0;
            for (Map.Entry<String, Integer> e : dailyLogins.entrySet()) if (e.getKey().compareTo(from) >= 0) n += e.getValue();
            return n;
        }

        /** Playtime during the last {@code days} days (today included), current session not included. */
        public long playtimeLastDays(int days) {
            if (dailyPlaytimeMs == null) return 0L;
            String from = TimeHelper.today().minusDays(days - 1L).toString();
            long n = 0;
            for (Map.Entry<String, Long> e : dailyPlaytimeMs.entrySet()) if (e.getKey().compareTo(from) >= 0) n += e.getValue();
            return n;
        }

        private void prune() {
            String keepFrom = TimeHelper.today().minusDays(KEEP_DAYS).toString();
            if (dailyLogins != null) dailyLogins.keySet().removeIf(k -> k.compareTo(keepFrom) < 0);
            if (dailyPlaytimeMs != null) dailyPlaytimeMs.keySet().removeIf(k -> k.compareTo(keepFrom) < 0);
        }

        void addDailyPlaytime(long ms) {
            if (dailyPlaytimeMs == null) dailyPlaytimeMs = new TreeMap<>();
            dailyPlaytimeMs.merge(TimeHelper.today().toString(), ms, Long::sum);
            prune();
        }

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
    private static final java.util.concurrent.atomic.AtomicBoolean dirty =
            new java.util.concurrent.atomic.AtomicBoolean(false);

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

        if (TimeHelper.nowTime().getHour() != 0) return;
        java.time.LocalDate today = TimeHelper.today();
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
        java.text.SimpleDateFormat sdf = TimeHelper.sdf(dateFormat);

        for (Map.Entry<UUID, ConnectionEntry> e : new ArrayList<>(entries.entrySet())) {
            UUID uuid = e.getKey();
            ConnectionEntry entry = e.getValue();
            if (entry.lastLogin == null || entry.mcName == null) continue;
            if (server.getPlayerList().getPlayer(uuid) != null) continue;
            if (!server.getPlayerList().isWhiteListed(new com.mojang.authlib.GameProfile(uuid, entry.mcName))) continue;

            long lastLoginMs = loginMillis(entry, sdf);
            if (lastLoginMs == Long.MIN_VALUE) continue;
            long reference = Math.max(lastLoginMs, AbsenceManager.getEndMs(uuid));
            if (nowMs - reference < thresholdMs) continue;

            final String playerName = entry.mcName;
            final long inactiveDays = (nowMs - lastLoginMs) / 86400_000L;
            final long totalPlaytimeMs = getTotalPlaytimeMs(uuid);
            final List<String> licenses = net.rp.rpessentials.profession.LicenseManager.getLicenses(uuid);
            final String lastLogin = entry.lastLogin;

            Runnable remove = () -> {
                AutoUnwhitelistHistory.record(playerName, uuid.toString(), lastLogin, totalPlaytimeMs, licenses, inactiveDays);
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), "whitelist remove " + playerName);
                ManageSuggestions.propose(server, uuid, playerName, ManageSuggestions.Kind.UNWHITELISTED);

                for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
                    if (!RpEssentialsPermissions.isStaff(staff)) continue;
                    net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component.literal(
                            ServerLang.get(staff, MessagesConfig.AUTO_UNWHITELIST_STAFF_NOTIFY,
                                    "player", playerName, "days", String.valueOf(inactiveDays)));
                    net.minecraft.network.chat.MutableComponent undo =
                            net.minecraft.network.chat.Component.literal(ServerLang.get(staff, MessagesConfig.UNWHITELIST_UNDO))
                                    .withStyle(s -> s
                                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                                    "/whitelist add " + playerName))
                                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                                    net.minecraft.network.chat.Component.literal(ServerLang.get(staff, MessagesConfig.UNWHITELIST_UNDO_HOVER, "player", playerName)))));
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
    private static void saveToFile(boolean sync) {
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
            data.put(uuid.toString() + " (" + mcName + ")", e.getValue().snapshot());
        }

        File targetFile = dataFile;
        if (sync) RpEssentialsIO.writeJsonNow(targetFile, data, GSON);
        else RpEssentialsIO.saveJson(targetFile, data, GSON);
    }

    private static void markDirty() {
        ensureInitialized();
        dirty.set(true);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    private static String getNow() {
        try {
            return TimeHelper.format(ModerationConfig.LAST_CONNECTION_DATE_FORMAT.get());
        } catch (Exception e) {
            return TimeHelper.format("dd/MM/yyyy HH:mm:ss");
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
        ConnectionEntry existing = entries.get(uuid);
        long prevPlaytime = existing != null ? existing.totalPlaytimeMs : 0L;
        ConnectionEntry e = new ConnectionEntry(player.getName().getString(), getNow(),
                existing != null ? existing.lastLogout : null, prevPlaytime);
        e.lastLoginMs = System.currentTimeMillis();
        e.copyActivity(existing);
        if (e.firstLoginMs <= 0) e.firstLoginMs = firstSeen(player, e.lastLoginMs);
        e.loginCount++;
        e.dailyLogins.merge(TimeHelper.today().toString(), 1, Integer::sum);
        e.prune();
        entries.put(uuid, e);
        markDirty();
    }

    /**
     * First login of a player who has no recorded one: the creation date of their save file when it is
     * clearly older than this login (players who joined before 5.1.0), otherwise now.
     */
    private static long firstSeen(ServerPlayer player, long now) {
        try {
            java.nio.file.Path p = player.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                    .resolve(player.getUUID() + ".dat");
            if (java.nio.file.Files.exists(p)) {
                long created = java.nio.file.Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class)
                        .creationTime().toMillis();
                if (created > 0 && created < now - 60_000L) return created;
            }
        } catch (Exception ignored) {}
        return now;
    }

    public static void recordLogout(ServerPlayer player) {
        if (!isEnabled()) return;
        try { if (!ModerationConfig.LAST_CONNECTION_TRACK_LOGOUT.get()) return; }
        catch (IllegalStateException e) { return; }
        ensureInitialized();
        UUID uuid = player.getUUID();
        ConnectionEntry existing = entries.get(uuid);
        long prevPlaytime = existing != null ? existing.totalPlaytimeMs : 0L;
        ConnectionEntry e = new ConnectionEntry(player.getName().getString(),
                existing != null ? existing.lastLogin : null, getNow(), prevPlaytime);
        e.lastLoginMs = existing != null ? existing.lastLoginMs : 0L;
        e.copyActivity(existing);
        entries.put(uuid, e);
        markDirty();
        flushToDisk();
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
        e.addDailyPlaytime(durationMs);
        markDirty();
    }

    public static void addPlaytimeSilent(UUID uuid, long durationMs) {
        ensureInitialized();
        ConnectionEntry e = entries.get(uuid);
        if (e == null) return;
        e.totalPlaytimeMs += durationMs;
        e.addDailyPlaytime(durationMs);
        dirty.set(true);
    }

    public static void flushToDisk() {
        if (dirty.getAndSet(false)) markDirty();;
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
        try { parser = TimeHelper.sdf(ModerationConfig.LAST_CONNECTION_DATE_FORMAT.get()); }
        catch (Exception e) { parser = TimeHelper.sdf("dd/MM/yyyy HH:mm:ss"); }
        final SimpleDateFormat fmt = parser;
        List<Map.Entry<UUID, ConnectionEntry>> list = new ArrayList<>(entries.entrySet());
        list.sort((a, b) -> Long.compare(loginMillis(b.getValue(), fmt), loginMillis(a.getValue(), fmt)));
        return list;
    }

    private static long loginMillis(ConnectionEntry entry, SimpleDateFormat fmt) {
        if (entry.lastLoginMs > 0) return entry.lastLoginMs;
        if (entry.lastLogin == null) return Long.MIN_VALUE;
        if (entry.lastLogin.equals(entry.parsedFrom)) return entry.parsedMs;
        try {
            long ms = fmt.parse(entry.lastLogin).getTime();
            entry.parsedFrom = entry.lastLogin;
            entry.parsedMs = ms;
            return ms;
        } catch (java.text.ParseException e) {
            return Long.MIN_VALUE;
        }
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
        if (dataFile != null && dirty.getAndSet(false)) saveToFile(true);
        dataFile = null;
        entries.clear();
        ensureInitialized();
    }
}