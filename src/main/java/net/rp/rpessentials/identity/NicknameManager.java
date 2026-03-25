package net.rp.rpessentials.identity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;
import net.rp.rpessentials.SyncNametagDataPacket;
import net.rp.rpessentials.config.RpEssentialsConfig;

/**
 * Gestionnaire de nicknames avec sauvegarde automatique
 * Le fichier est sauvegardé dans le dossier world/data/oneriamod/
 */
public class NicknameManager {
    private static final Map<UUID, String> nicknames = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File nicknameFile = null;

    /**
     * Définit le fichier de sauvegarde et charge les nicknames
     * synchronized : empêche deux threads d'initialiser en même temps
     */
    private static synchronized void ensureInitialized() {
        if (nicknameFile != null) return;
        try {
            File dataFolder = RpEssentialsDataPaths.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            nicknameFile = new File(dataFolder, "nicknames.json");
            if (nicknameFile.exists()) loadFromFile();

            RpEssentials.LOGGER.info("[NicknameManager] Initialized - File: {}", nicknameFile.getAbsolutePath());
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
                RpEssentials.LOGGER.info("[NicknameManager] Loaded {} nicknames", nicknames.size());
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[NicknameManager] Failed to load nicknames", e);
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
            String mcName = "Unknown";
            if (server != null) {
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != null) {
                    mcName = online.getName().getString();
                } else if (server.getProfileCache() != null) {
                    mcName = server.getProfileCache().get(uuid)
                            .map(p -> p.getName()).orElse("Unknown");
                }
            }
            data.put(uuid.toString() + " (" + mcName + ")", entry.getValue());
        }

        File targetFile = nicknameFile;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (java.io.FileWriter writer = new java.io.FileWriter(targetFile)) {
                    GSON.toJson(data, writer);
                }
                RpEssentials.LOGGER.debug("[NicknameManager] Saved {} nicknames", data.size());
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[NicknameManager] Failed to save nicknames", e);
            }
        });
    }

    /**
     * Définit un nickname pour un joueur
     */
    public static void setNickname(UUID playerUUID, String nickname) {
        ensureInitialized();

        if (nickname == null || nickname.isEmpty()) {
            nicknames.remove(playerUUID);
        } else {
            nicknames.put(playerUUID, nickname);
        }

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(playerUUID);
            if (target != null) SyncNametagDataPacket.broadcastForPlayer(target);
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

        if (nickname == null) {
            return null;
        }

        try {
            if (RpEssentialsConfig.SHOW_NAMETAG_PREFIX_SUFFIX != null &&
                    RpEssentialsConfig.SHOW_NAMETAG_PREFIX_SUFFIX.get()) {
                String prefix = RpEssentials.getPlayerPrefix(player);
                String suffix = RpEssentials.getPlayerSuffix(player);
                String fullDisplay = prefix + nickname + suffix;
                return Component.literal(fullDisplay.replace("&", "§"));
            } else {
                return Component.literal(nickname.replace("&", "§"));
            }
        } catch (Exception e) {
            return Component.literal(nickname.replace("&", "§"));
        }
    }

    /**
     * Supprime le nickname d'un joueur
     */
    public static void removeNickname(UUID playerUUID) {
        ensureInitialized();
        nicknames.remove(playerUUID);
        saveToFile();

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(playerUUID);
            if (target != null) SyncNametagDataPacket.broadcastForPlayer(target);
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
    public static void reload() {
        nicknameFile = null;
        nicknames.clear();
        ensureInitialized();
    }
}