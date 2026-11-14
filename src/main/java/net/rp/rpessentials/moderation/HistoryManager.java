package net.rp.rpessentials.moderation;

import net.rp.rpessentials.config.Cfg;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.rp.rpessentials.util.NameCache;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.core.RpEssentialsIO;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ModerationConfig;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One timeline per player: warns, mutes, notes, manage actions, licenses and RP deaths.
 * Stored in history.json and purged according to the "Player History" config section.
 */
public final class HistoryManager {

    // Types, also used as keys of historyRetention.
    public static final String WARN = "WARN";
    public static final String WARN_REMOVE = "WARN_REMOVE";
    public static final String WARN_CLEAR = "WARN_CLEAR";
    public static final String MUTE = "MUTE";
    public static final String UNMUTE = "UNMUTE";
    public static final String NOTE = "NOTE";
    public static final String NOTE_REMOVE = "NOTE_REMOVE";
    public static final String MANAGE = "MANAGE";
    public static final String LICENSE = "LICENSE";
    public static final String DEATH_RP = "DEATH_RP";
    public static final String NICK = "NICK";

    public static final List<String> TYPES = List.of(WARN, WARN_REMOVE, WARN_CLEAR, MUTE, UNMUTE,
            NOTE, NOTE_REMOVE, MANAGE, LICENSE, DEATH_RP, NICK);

    public static class Entry {
        public long time;
        public String type;
        public String actor;
        public String detail;
        /**
         * Vanish level needed to see this line, 0 = everyone. Set when the action was done by a vanished
         * staff member, or when it is a vanish action itself.
         */
        public int hidden;

        public Entry() {}

        Entry(long time, String type, String actor, String detail, int hidden) {
            this.time = time;
            this.type = type;
            this.actor = actor;
            this.detail = detail;
            this.hidden = hidden;
        }

        /** True when this staff member (null = console) may see the line. */
        public boolean visibleTo(net.minecraft.server.level.ServerPlayer viewer) {
            return hidden <= 0 || viewer == null || VanishManager.levelOf(viewer) >= hidden;
        }
    }

    private static class Stored {
        String uuid;
        String name;
        List<Entry> entries = new ArrayList<>();
    }

    private static final Map<UUID, List<Entry>> history = new ConcurrentHashMap<>();
    private static final Map<UUID, String> names = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File dataFile = null;

    private HistoryManager() {}

    // ── Config ───────────────────────────────────────────────────────────────

    public static boolean enabled() {
        return Cfg.get(ModerationConfig.HISTORY_ENABLED, true);
    }

    /** Days kept for a type: the type's own line, else "*", else 365. -1 = forever. */
    static int retentionDays(String type) {
        int all = 365;
        if (Cfg.ready(ModerationConfig.SPEC)) {
            for (String line : ModerationConfig.HISTORY_RETENTION.get()) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String k = line.substring(0, eq).trim();
                int days;
                try {
                    days = Integer.parseInt(line.substring(eq + 1).trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (k.equalsIgnoreCase(type)) return days;
                if (k.equals("*")) all = days;
            }
        }
        return all;
    }

    static int maxPerPlayer() {
        return Cfg.get(ModerationConfig.HISTORY_MAX_PER_PLAYER, 200);
    }

    // ── Storage ──────────────────────────────────────────────────────────────

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File folder = RpEssentialsDataPaths.getDataFolder();
            if (!folder.exists()) folder.mkdirs();
            dataFile = new File(folder, "history.json");
            if (dataFile.exists()) load();
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[HistoryManager] Failed to initialize", e);
        }
    }

    private static void load() {
        try (FileReader reader = new FileReader(dataFile, java.nio.charset.StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Stored>>() {}.getType();
            Map<String, Stored> data = GSON.fromJson(reader, type);
            history.clear();
            names.clear();
            if (data == null) return;
            for (Stored s : data.values()) {
                try {
                    UUID id = UUID.fromString(s.uuid);
                    history.put(id, Collections.synchronizedList(new ArrayList<>(s.entries)));
                    if (s.name != null) names.put(id, s.name);
                } catch (Exception ex) {
                    RpEssentials.LOGGER.warn("[HistoryManager] Invalid UUID: {}", s.uuid);
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[HistoryManager] Failed to load", e);
            RpEssentialsIO.quarantine(dataFile);
        }
    }

    private static void save() {
        if (dataFile == null) return;
        Map<String, Stored> data = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<Entry>> e : history.entrySet()) {
            Stored s = new Stored();
            s.uuid = e.getKey().toString();
            s.name = names.get(e.getKey());
            synchronized (e.getValue()) {
                for (Entry x : e.getValue()) s.entries.add(new Entry(x.time, x.type, x.actor, x.detail, x.hidden));
            }
            data.put(s.uuid + (s.name != null ? " (" + s.name + ")" : ""), s);
        }
        RpEssentialsIO.saveJson(dataFile, data, GSON);
    }

    public static synchronized void reload() {
        dataFile = null;
        history.clear();
        names.clear();
        ensureInitialized();
        if (purge() > 0) save();
    }

    // ── API ──────────────────────────────────────────────────────────────────

    /** Name stored for the "System" actor (automatic actions), in the server language. */
    public static String system() {
        return ServerLang.get(null, MessagesConfig.HISTORY_SYSTEM);
    }

    /**
     * Adds a line to a player's history.
     * @param actor who did it (staff name), null for an automatic action
     */
    public static void record(UUID target, String targetName, String type, String actor, String detail) {
        record(target, targetName, type, actor, detail, 0);
    }

    /**
     * Same as {@link #record(UUID, String, String, String, String)}, with a minimum vanish level needed
     * to see the line. The level of the actor is added automatically when they are vanished.
     */
    public static void record(UUID target, String targetName, String type, String actor, String detail, int hiddenLevel) {
        if (target == null || !enabled()) return;
        int hidden = Math.max(0, hiddenLevel);
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null && actor != null) {
            net.minecraft.server.level.ServerPlayer a = server.getPlayerList().getPlayerByName(actor);
            if (a != null && VanishManager.isVanished(a.getUUID())) hidden = Math.max(hidden, Math.max(1, VanishManager.getLevel(a.getUUID())));
        }
        ensureInitialized();
        String name = targetName != null ? targetName : NameCache.get(target);
        if (name != null) names.put(target, name);
        List<Entry> list = history.computeIfAbsent(target, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(new Entry(System.currentTimeMillis(), type, actor != null ? actor : system(),
                detail == null ? "" : detail, hidden));
        trim(list);
        save();
    }

    /** History of a player as seen by a staff member (null = console, sees everything), newest first. */
    public static List<Entry> get(UUID target, net.minecraft.server.level.ServerPlayer viewer) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : get(target)) if (e.visibleTo(viewer)) out.add(e);
        return out;
    }

    /** History of a player, newest first. */
    public static List<Entry> get(UUID target) {
        ensureInitialized();
        List<Entry> list = history.get(target);
        if (list == null) return List.of();
        List<Entry> copy;
        synchronized (list) {
            copy = new ArrayList<>(list);
        }
        Collections.reverse(copy);
        return copy;
    }

    public static String nameOf(UUID target) {
        ensureInitialized();
        return names.get(target);
    }

    /** UUID of a player known by the history, by Minecraft name. */
    public static UUID findByName(String name) {
        ensureInitialized();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    /** Applies the retention rules to everyone. Returns the number of removed lines. */
    public static int purge() {
        ensureInitialized();
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Iterator<Map.Entry<UUID, List<Entry>>> it = history.entrySet().iterator(); it.hasNext(); ) {
            List<Entry> list = it.next().getValue();
            synchronized (list) {
                int before = list.size();
                list.removeIf(e -> {
                    int days = retentionDays(e.type);
                    return days >= 0 && now - e.time > days * 86_400_000L;
                });
                trim(list);
                removed += before - list.size();
                if (list.isEmpty()) it.remove();
            }
        }
        if (removed > 0) RpEssentials.LOGGER.info("[HistoryManager] Purged {} old history lines", removed);
        return removed;
    }

    private static net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> typeKey(String type) {
        return switch (type) {
            case WARN -> MessagesConfig.HISTORY_T_WARN;
            case WARN_REMOVE -> MessagesConfig.HISTORY_T_WARN_REMOVE;
            case WARN_CLEAR -> MessagesConfig.HISTORY_T_WARN_CLEAR;
            case MUTE -> MessagesConfig.HISTORY_T_MUTE;
            case UNMUTE -> MessagesConfig.HISTORY_T_UNMUTE;
            case NOTE -> MessagesConfig.HISTORY_T_NOTE;
            case NOTE_REMOVE -> MessagesConfig.HISTORY_T_NOTE_REMOVE;
            case MANAGE -> MessagesConfig.HISTORY_T_MANAGE;
            case LICENSE -> MessagesConfig.HISTORY_T_LICENSE;
            case DEATH_RP -> MessagesConfig.HISTORY_T_DEATH_RP;
            case NICK -> MessagesConfig.HISTORY_T_NICK;
            default -> null;
        };
    }

    /** One history line, in the viewer's language. */
    public static String format(net.minecraft.server.level.ServerPlayer v, Entry e) {
        var key = typeKey(e.type);
        String type = key != null ? ServerLang.get(v, key) : String.valueOf(e.type);
        return ServerLang.get(v, MessagesConfig.HISTORY_LINE,
                "date", net.rp.rpessentials.util.TimeHelper.format("dd/MM/yyyy HH:mm", e.time),
                "type", type, "actor", String.valueOf(e.actor), "detail", String.valueOf(e.detail));
    }

    /** Hourly purge, called from the server tick. */
    public static void tick() {
        if (dataFile == null || !enabled()) return;
        if (purge() > 0) save();
    }

    private static void trim(List<Entry> list) {
        int max = maxPerPlayer();
        if (max <= 0) return;
        synchronized (list) {
            while (list.size() > max) list.remove(0);
        }
    }
}
