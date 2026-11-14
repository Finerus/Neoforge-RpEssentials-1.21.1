package net.rp.rpessentials.identity;

import net.rp.rpessentials.config.Cfg;
import java.util.Objects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.RpConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.core.RpEssentialsIO;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.network.SyncNametagDataPacket;
import net.rp.rpessentials.util.ColorHelper;

/**
 * Gestionnaire de nicknames avec sauvegarde automatique
 */
public class NicknameManager {
    private static final Map<UUID, String> nicknames = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File nicknameFile = null;

    /**
     * Définit le fichier de sauvegarde et charge les nicknames
     * synchronized : empêche deux threads d'initialiser en même temps
     */
    private static volatile boolean ready = false;

    private static void ensureInitialized() {
        if (!ready) init();
    }

    private static synchronized void init() {
        if (ready) return;
        try {
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();
            nicknameFile = new File(dataFolder, "nicknames.json");
            if (nicknameFile.exists()) loadFromFile();
            ready = true;
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[NicknameManager] Failed to initialize", e);
        }
    }

    /**
     * Charge les nicknames depuis le fichier JSON
     */
    private static void loadFromFile() {
        if (nicknameFile == null || !nicknameFile.exists()) return;

        try (FileReader reader = new FileReader(nicknameFile)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> data = GSON.fromJson(reader, type);

            if (data != null) {
                nicknames.clear();
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    try {
                        String key = entry.getKey();
                        // Rétrocompatible : "UUID (McName)" ou juste "UUID"
                        String uuidStr = key.contains(" ") ? key.substring(0, key.indexOf(' ')) : key;
                        UUID uuid = UUID.fromString(uuidStr);
                        nicknames.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        RpEssentials.LOGGER.warn("[NicknameManager] Invalid UUID in key: {}", entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[NicknameManager] Failed to load nicknames", e);
            RpEssentialsIO.quarantine(nicknameFile);
        }
    }

    /**
     * Sauvegarde les nicknames dans le fichier JSON de manière asynchrone.
     * On prend un snapshot des données d'abord pour ne pas bloquer le thread serveur.
     */
    private static void saveToFile() {
        ensureInitialized();
        if (nicknameFile == null) return;

        // Bug 7 — résolution des noms sur le thread serveur avant d'aller async
        Map<UUID, String> snapshot = new HashMap<>(nicknames);
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        Map<String, String> data = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, String> entry : snapshot.entrySet()) {
            UUID uuid = entry.getKey();
            String mcName = net.rp.rpessentials.util.NameCache.getOrUnknown(uuid);
            data.put(uuid.toString() + " (" + mcName + ")", entry.getValue());
        }

        File targetFile = nicknameFile;
        RpEssentialsIO.saveJson(targetFile, data, GSON);
    }

    /**
     * Définit un nickname pour un joueur
     */
    /** Adds the nickname change to the player history (only when it really changes). */
    private static void recordChange(UUID playerUUID, String newNick) {
        String old = nicknames.get(playerUUID);
        String n = newNick == null || newNick.isEmpty() ? null : newNick;
        if (Objects.equals(old, n)) return;
        net.rp.rpessentials.moderation.HistoryManager.record(playerUUID, null,
                net.rp.rpessentials.moderation.HistoryManager.NICK, "-",
                (old == null ? "-" : old) + " §7→ §f" + (n == null ? "-" : n));
    }

    public static void setNickname(UUID playerUUID, String nickname) {
        ensureInitialized();
        recordChange(playerUUID, nickname);

        if (nickname == null || nickname.isEmpty()) {
            nicknames.remove(playerUUID);
        } else {
            nicknames.put(playerUUID, nickname);
        }

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(playerUUID);
            if (target != null) SyncNametagDataPacket.refreshNow(target);
        }

        saveToFile();
    }

    /**
     * Récupère le nickname d'un joueur
     */
    public static String getNickname(UUID playerUUID) {
        ensureInitialized();
        return nicknames.get(playerUUID);
    }

    /**
     * Récupère le nom d'affichage (nickname ou nom réel)
     */
    public static String getDisplayName(ServerPlayer player) {
        String nickname = getNickname(player.getUUID());
        return nickname != null ? nickname : player.getGameProfile().getName();
    }

    /**
     * Récupère le nom d'affichage pour le nametag (avec ou sans prefix/suffix)
     */
    public static Component getNametagDisplay(ServerPlayer player) {
        String nickname = getNickname(player.getUUID());
        if (nickname == null) return null;
        try {
            if (RpEssentialsConfig.SHOW_NAMETAG_PREFIX_SUFFIX != null
                    && RpEssentialsConfig.SHOW_NAMETAG_PREFIX_SUFFIX.get()) {
                return ColorHelper.parseColors(RpEssentials.getPlayerPrefix(player) + nickname
                        + RpEssentials.getPlayerSuffix(player));
            }
        } catch (Exception ignored) {}
        return ColorHelper.parseColors(nickname);
    }

    /**
     * Supprime le nickname d'un joueur
     */
    public static void removeNickname(UUID playerUUID) {
        ensureInitialized();
        recordChange(playerUUID, null);
        nicknames.remove(playerUUID);
        saveToFile();

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(playerUUID);
            if (target != null) SyncNametagDataPacket.refreshNow(target);
        }
    }

    /**
     * Vérifie si un joueur a un nickname
     */
    public static boolean hasNickname(UUID playerUUID) {
        ensureInitialized();
        return nicknames.containsKey(playerUUID);
    }

    /**
     * Unicité du nickname et blacklist
     */
    public static String plain(String nick) {
        return ColorHelper.stripAll(ColorHelper.translateAlternateColorCodes(nick)).trim();
    }

    public static String rejection(UUID self, String nick, boolean staff, ServerPlayer viewer) {
        ensureInitialized();
        String n = plain(nick);
        if (Cfg.ready(RpConfig.SPEC)) {
            if (RpConfig.NICK_UNIQUE.get()) {
                for (Map.Entry<UUID, String> e : nicknames.entrySet()) {
                    if (!e.getKey().equals(self) && plain(e.getValue()).equalsIgnoreCase(n))
                        return ServerLang.get(viewer, MessagesConfig.NICK_USED);
                }
            }
            if (!staff) {
                for (String w : RpConfig.NICK_BLACKLIST.get()) {
                    if (!w.isBlank() && n.toLowerCase().contains(w.trim().toLowerCase()))
                        return ServerLang.get(viewer, MessagesConfig.NICK_FORBIDDEN);
                }
            }
        }
        return null;
    }

    /**
     * Supprime tous les nicknames
     */
    public static void clearAll() {
        ensureInitialized();
        nicknames.clear();
        saveToFile();
    }

    /**
     * Retourne le nombre de nicknames enregistrés
     */
    public static int count() {
        ensureInitialized();
        return nicknames.size();
    }

    public static Map<UUID, String> getAllNicknames() {
        ensureInitialized();
        return new HashMap<>(nicknames);
    }

    /**
     * Recharge les nicknames depuis le fichier
     */
    public static synchronized void reload() {
        ready = false;
        nicknameFile = null;
        nicknames.clear();
        init();
    }
}