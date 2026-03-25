package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.RpEssentialsDataPaths;
import net.rp.rpessentials.RpEssentialsScheduleManager;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.config.ScheduleConfig;
import net.rp.rpessentials.identity.NicknameManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for the Death RP system.
 */
public class DeathRPManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("DeathRPManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ConcurrentHashMap<UUID, Boolean> overrides = new ConcurrentHashMap<>();
    private static File dataFile = null;

    // ── Historique ──────────────────────────────────────────────────────────────

    public static class DeathHistoryEntry {
        public String playerName;
        public String playerUUID;
        public String timestamp;
        public String damageCause;
        public String broadcastMessage;

        public DeathHistoryEntry() {}

        public DeathHistoryEntry(String playerName, String playerUUID,
                                 String damageCause, String broadcastMessage) {
            this.playerName       = playerName;
            this.playerUUID       = playerUUID;
            this.damageCause      = damageCause;
            this.broadcastMessage = broadcastMessage;
            this.timestamp        = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        }
    }

    private static final java.util.List<DeathHistoryEntry> history =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private static File historyFile = null;

    private static void ensureHistoryInitialized() {
        if (historyFile != null) return;
        try {
            File dataDir = RpEssentialsDataPaths.getDataFolder();
            dataDir.mkdirs();
            historyFile = new File(dataDir, "deathrp-history.json");
            if (historyFile.exists()) loadHistory();
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Failed to init history file", e);
        }
    }

    private static void loadHistory() {
        try (FileReader reader = new FileReader(historyFile)) {
            com.google.gson.reflect.TypeToken<java.util.List<DeathHistoryEntry>> type =
                    new com.google.gson.reflect.TypeToken<>(){};
            java.util.List<DeathHistoryEntry> data = GSON.fromJson(reader, type.getType());
            if (data != null) {
                history.clear();
                history.addAll(data);
            }
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Failed to load history", e);
        }
    }

    private static void saveHistory() {
        ensureHistoryInitialized();
        java.util.List<DeathHistoryEntry> snapshot = new java.util.ArrayList<>(history);
        File target = historyFile;
        CompletableFuture.runAsync(() -> {
            try (FileWriter writer = new FileWriter(target)) {
                GSON.toJson(snapshot, writer);
            } catch (Exception e) {
                LOGGER.error("[DeathRP] Failed to save history", e);
            }
        });
    }

    public static java.util.List<DeathHistoryEntry> getHistory(UUID playerUUID) {
        ensureHistoryInitialized();
        String uuidStr = playerUUID.toString();
        return history.stream()
                .filter(e -> uuidStr.equals(e.playerUUID))
                .collect(java.util.stream.Collectors.toList());
    }

    public static java.util.List<DeathHistoryEntry> getAllHistory() {
        ensureHistoryInitialized();
        return new java.util.ArrayList<>(history);
    }

    // ─── Lazy init ──────────────────────────────────────────────────────────────

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File dataDir = RpEssentialsDataPaths.getDataFolder();
            dataDir.mkdirs();
            dataFile = new File(dataDir, "deathrp.json");
            loadFromFile();
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Failed to initialize data file", e);
        }
    }

    private static void loadFromFile() {
        if (dataFile == null || !dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Boolean>>() {}.getType();
            Map<String, Boolean> raw = GSON.fromJson(reader, type);
            if (raw != null) {
                overrides.clear();
                raw.forEach((key, value) -> {
                    // Support both "UUID (McName)" and bare UUID keys
                    String uuidStr = key.contains(" ") ? key.substring(0, key.indexOf(' ')) : key;
                    try { overrides.put(UUID.fromString(uuidStr), value); } catch (Exception ignored) {}
                });
            }
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Failed to load deathrp.json", e);
        }
    }

    private static void saveToFile() {
        Map<UUID, Boolean> snapshot = new HashMap<>(overrides);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        Map<String, Boolean> out = new HashMap<>();
        for (Map.Entry<UUID, Boolean> entry : snapshot.entrySet()) {
            UUID uuid = entry.getKey();
            String mcName = "Unknown";
            if (server != null) {
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != null) {
                    mcName = online.getName().getString();
                } else if (server.getProfileCache() != null) {
                    mcName = server.getProfileCache().get(uuid)
                            .map(com.mojang.authlib.GameProfile::getName)
                            .orElse("Unknown");
                }
            }
            out.put(uuid + (mcName.equals("Unknown") ? "" : " (" + mcName + ")"), entry.getValue());
        }

        CompletableFuture.runAsync(() -> {
            try {
                ensureInitialized();
                if (dataFile == null) return;
                try (java.io.FileWriter writer = new java.io.FileWriter(dataFile)) {
                    GSON.toJson(out, writer);
                }
            } catch (Exception e) {
                LOGGER.error("[DeathRP] Failed to save deathrp.json", e);
            }
        });
    }

    private static String resolveMcName(MinecraftServer server, UUID uuid) {
        if (server == null) return "Unknown";
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(uuid).map(GameProfile::getName).orElse("Unknown");
        }
        return "Unknown";
    }

    // ─── Public API ─────────────────────────────────────────────────────────────

    public static boolean isDeathRPEnabled(UUID uuid) {
        // Individual override — highest priority
        Boolean override = overrides.get(uuid);
        if (override != null) return override;

        // Death Hours active → force everyone to true
        try {
            if (ScheduleConfig.DEATH_HOURS_ENABLED.get()
                    && RpEssentialsScheduleManager.isDeathHour()) {
                return true;
            }
        } catch (IllegalStateException ignored) {}

        // Fall back to global state
        try {
            return RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null
                    && RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static void setOverride(UUID playerUuid, boolean enabled) {
        ensureInitialized();
        overrides.put(playerUuid, enabled);
        saveToFile();
    }

    public static void removeOverride(UUID playerUuid) {
        ensureInitialized();
        overrides.remove(playerUuid);
        saveToFile();
    }

    public static Boolean getOverride(UUID playerUuid) {
        ensureInitialized();
        return overrides.get(playerUuid);
    }

    public static Map<UUID, Boolean> getAllOverrides() {
        ensureInitialized();
        return new HashMap<>(overrides);
    }

    // ─── Death handling ─────────────────────────────────────────────────────────

    public static void onPlayerDeathRP(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String rawDeathMsg = "";

        // 1. Death message broadcast
        try {
            if (RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE != null) {
                rawDeathMsg = RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE.get()
                        .replace("{player}",   NicknameManager.getDisplayName(player))
                        .replace("{realname}", player.getName().getString());
                Component component = ColorHelper.parseColors(rawDeathMsg);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(component);
                }
            }
        } catch (IllegalStateException e) {
            LOGGER.warn("[DeathRP] Config DEATH_MESSAGE unavailable", e);
        }

        // 2. Death sound
        playSound(server, null,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND_PITCH,
                "death sound");

        // 3. Whitelist removal
        try {
            if (RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE != null
                    && RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get()) {
                net.minecraft.server.players.UserWhiteList whitelist =
                        server.getPlayerList().getWhiteList();
                whitelist.remove(new net.minecraft.server.players.UserWhiteListEntry(
                        player.getGameProfile()));
                LOGGER.info("[DeathRP] {} removed from whitelist.", player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Could not remove {} from whitelist",
                    player.getName().getString(), e);
        }

        // Feature 5 — enregistrement dans l'historique
        try {
            String cause = player.getLastDamageSource() != null
                    ? player.getLastDamageSource().typeHolder()
                    .unwrapKey().map(k -> k.location().getPath()).orElse("unknown")
                    : "unknown";

            ensureHistoryInitialized();
            history.add(new DeathHistoryEntry(
                    player.getName().getString(),
                    player.getUUID().toString(),
                    cause,
                    rawDeathMsg));
            saveHistory();
            LOGGER.info("[DeathRP] Death recorded in history for {}", player.getName().getString());
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Failed to record death history", e);
        }
    }

    // ─── Toggle notifications ────────────────────────────────────────────────────

    public static void broadcastGlobalToggle(String staffName, boolean enabled, MinecraftServer server) {
        try {
            var msgConfig  = enabled ? RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MSG  : RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MSG;
            var modeConfig = enabled ? RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MODE : RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MODE;
            if (msgConfig == null || modeConfig == null) return;
            String raw  = msgConfig.get().replace("{staff}", staffName);
            String mode = modeConfig.get();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                sendMessageToPlayer(p, raw, mode);
            }
        } catch (IllegalStateException e) {
            LOGGER.warn("[DeathRP] Config GLOBAL_TOGGLE_MSG unavailable", e);
        }

        playSound(server, null,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND_PITCH,
                "global toggle sound");
    }

    public static void notifyPlayerToggle(ServerPlayer target, boolean enabled) {
        try {
            var msgConfig  = enabled ? RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MSG  : RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MSG;
            var modeConfig = enabled ? RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MODE : RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MODE;
            if (msgConfig == null || modeConfig == null) return;
            String raw  = msgConfig.get()
                    .replace("{player}",   NicknameManager.getDisplayName(target))
                    .replace("{realname}", target.getName().getString());
            String mode = modeConfig.get();
            sendMessageToPlayer(target, raw, mode);
        } catch (IllegalStateException e) {
            LOGGER.warn("[DeathRP] Config PLAYER_TOGGLE_MSG unavailable", e);
        }

        playSound(target.getServer(), target,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND_PITCH,
                "individual toggle sound");
    }

    // ─── Sound ──────────────────────────────────────────────────────────────────

    private static void playSound(
            MinecraftServer server,
            ServerPlayer soloTarget,
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> soundConfig,
            net.neoforged.neoforge.common.ModConfigSpec.DoubleValue volumeConfig,
            net.neoforged.neoforge.common.ModConfigSpec.DoubleValue pitchConfig,
            String label) {

        if (server == null) return;
        try {
            if (soundConfig == null) return;
            String soundId = soundConfig.get();
            if ("none".equalsIgnoreCase(soundId) || soundId.isBlank()) return;

            float volume = ((Number) volumeConfig.get()).floatValue();
            float pitch  = ((Number) pitchConfig.get()).floatValue();

            ResourceLocation rl = ResourceLocation.tryParse(soundId);
            if (rl == null) {
                LOGGER.warn("[DeathRP] Invalid sound ID for {}: {}", label, soundId);
                return;
            }
            Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(rl));

            if (soloTarget != null) {
                soloTarget.connection.send(new ClientboundSoundPacket(
                        holder, SoundSource.MASTER,
                        soloTarget.getX(), soloTarget.getY(), soloTarget.getZ(),
                        volume, pitch, soloTarget.getRandom().nextLong()));
            } else {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.connection.send(new ClientboundSoundPacket(
                            holder, SoundSource.MASTER,
                            p.getX(), p.getY(), p.getZ(),
                            volume, pitch, p.getRandom().nextLong()));
                }
            }
        } catch (IllegalStateException e) {
            LOGGER.warn("[DeathRP] Config {} unavailable", label, e);
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Error playing {}", label, e);
        }
    }

    // ─── Display utility ────────────────────────────────────────────────────────

    /**
     * Sends a raw message (§/& codes) to a player using the specified display mode.
     * Modes: TITLE, ACTION_BAR, IMMERSIVE (fallback ACTION_BAR), CHAT (default).
     */
    public static void sendMessageToPlayer(ServerPlayer player, String rawMessage, String mode) {
        Component component = ColorHelper.parseColors(rawMessage);
        switch (mode.toUpperCase()) {
            case "TITLE" -> {
                player.connection.send(new ClientboundSetTitleTextPacket(component));
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            }
            case "ACTION_BAR" -> player.displayClientMessage(component, true);
            case "IMMERSIVE" -> {
                try {
                    toni.immersivemessages.api.ImmersiveMessage.builder(3f, rawMessage)
                            .fadeIn(0.5f)
                            .fadeOut(0.5f)
                            .sendServer(player);
                } catch (Exception | NoClassDefFoundError e) {
                    player.displayClientMessage(component, true);
                }
            }
            default -> player.sendSystemMessage(component); // CHAT
        }
    }
}