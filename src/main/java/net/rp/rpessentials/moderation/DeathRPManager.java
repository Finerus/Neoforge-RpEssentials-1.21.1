package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.rp.rpessentials.*;
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
 * Manager pour le système de mort RP.
 *
 * 4.1.6 : sendMessageToPlayer() et broadcastGlobalToggle() délèguent à
 * {@link ImmersivePresetHelper} pour supporter les presets nommés
 * (ex : mode "IMMERSIVE:death" utilise le preset "death").
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
            com.google.gson.reflect.TypeToken<java.util.List<DeathHistoryEntry>> t =
                    new com.google.gson.reflect.TypeToken<>(){};
            java.util.List<DeathHistoryEntry> data = GSON.fromJson(reader, t.getType());
            if (data != null) { history.clear(); history.addAll(data); }
        } catch (Exception e) { LOGGER.error("[DeathRP] Failed to load history", e); }
        purgeOldHistory();
    }

    private static void saveHistory() {
        ensureHistoryInitialized();
        java.util.List<DeathHistoryEntry> snapshot = new java.util.ArrayList<>(history);
        File target = historyFile;
        CompletableFuture.runAsync(() -> {
            try (FileWriter w = new FileWriter(target)) { GSON.toJson(snapshot, w); }
            catch (Exception e) { LOGGER.error("[DeathRP] Failed to save history", e); }
        });
    }

    public static void purgeOldHistory() {
        try {
            int maxDays = net.rp.rpessentials.config.ModerationConfig.DEATH_HISTORY_MAX_DAYS.get();
            if (maxDays <= 0) return;
            ensureHistoryInitialized();
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(maxDays);
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            int before = history.size();
            history.removeIf(e -> {
                try { return java.time.LocalDateTime.parse(e.timestamp, fmt).isBefore(cutoff); }
                catch (Exception ex) { return false; }
            });
            int removed = before - history.size();
            if (removed > 0) {
                saveHistory();
                LOGGER.info("[DeathRP] Purged {} history entries older than {} days", removed, maxDays);
            }
        } catch (IllegalStateException ignored) {}
    }

    public static java.util.List<DeathHistoryEntry> getHistory(UUID playerUUID) {
        ensureHistoryInitialized();
        String s = playerUUID.toString();
        return history.stream().filter(e -> s.equals(e.playerUUID)).collect(java.util.stream.Collectors.toList());
    }

    public static java.util.List<DeathHistoryEntry> getAllHistory() {
        ensureHistoryInitialized();
        return new java.util.ArrayList<>(history);
    }

    // ── Init ────────────────────────────────────────────────────────────────────

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File dir = RpEssentialsDataPaths.getDataFolder();
            dir.mkdirs();
            dataFile = new File(dir, "deathrp.json");
            loadFromFile();
        } catch (Exception e) { LOGGER.error("[DeathRP] Failed to init", e); }
    }

    private static void loadFromFile() {
        if (dataFile == null || !dataFile.exists()) return;
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Boolean>>(){}.getType();
            Map<String, Boolean> raw = GSON.fromJson(reader, type);
            if (raw != null) {
                overrides.clear();
                raw.forEach((k, v) -> {
                    String u = k.contains(" ") ? k.substring(0, k.indexOf(' ')) : k;
                    try { overrides.put(UUID.fromString(u), v); } catch (Exception ignored) {}
                });
            }
        } catch (Exception e) { LOGGER.error("[DeathRP] Failed to load deathrp.json", e); }
    }

    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;

        Map<UUID, Boolean> snapshot = new HashMap<>(overrides);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        Map<String, Boolean> out = new HashMap<>();
        for (Map.Entry<UUID, Boolean> e : snapshot.entrySet()) {
            String mcName = "Unknown";
            if (server != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) mcName = p.getName().getString();
                else if (server.getProfileCache() != null)
                    mcName = server.getProfileCache().get(e.getKey())
                            .map(GameProfile::getName).orElse("Unknown");
            }
            out.put(e.getKey() + (mcName.equals("Unknown") ? "" : " (" + mcName + ")"), e.getValue());
        }

        File targetFile = dataFile;
        RpEssentialsIO.submit(() -> {
            try {
                if (!targetFile.getParentFile().exists()) targetFile.getParentFile().mkdirs();
                try (FileWriter w = new FileWriter(targetFile)) { GSON.toJson(out, w); }
            } catch (Exception e) { LOGGER.error("[DeathRP] Failed to save", e); }
        });
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    public static boolean isDeathRPEnabled(UUID uuid) {
        Boolean override = overrides.get(uuid);
        if (override != null) return override;
        try {
            if (ScheduleConfig.DEATH_HOURS_ENABLED.get()
                    && RpEssentialsScheduleManager.isDeathHour()) return true;
        } catch (IllegalStateException ignored) {}
        try {
            return RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null
                    && RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get();
        } catch (IllegalStateException e) { return false; }
    }

    public static void setOverride(UUID uuid, boolean enabled) {
        ensureInitialized(); overrides.put(uuid, enabled); saveToFile();
    }

    public static void removeOverride(UUID uuid) {
        ensureInitialized(); overrides.remove(uuid); saveToFile();
    }

    public static Boolean getOverride(UUID uuid) {
        ensureInitialized(); return overrides.get(uuid);
    }

    public static Map<UUID, Boolean> getAllOverrides() {
        ensureInitialized(); return new HashMap<>(overrides);
    }

    // ── Mort ────────────────────────────────────────────────────────────────────

    public static void onPlayerDeathRP(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String rawDeathMsg = "";

        try {
            if (RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE != null) {
                rawDeathMsg = RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE.get()
                        .replace("{player}",   NicknameManager.getDisplayName(player))
                        .replace("{realname}", player.getName().getString());
                Component comp = ColorHelper.parseColors(rawDeathMsg);
                for (ServerPlayer p : server.getPlayerList().getPlayers())
                    p.sendSystemMessage(comp);
            }
        } catch (IllegalStateException e) { LOGGER.warn("[DeathRP] Config DEATH_MESSAGE unavailable"); }

        playSound(server, null,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND_PITCH, "death sound");

        try {
            if (RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE != null
                    && RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get()
                    && !RpEssentialsRoleManager.has(player, RpEssentialsRoleManager.Permission.BYPASS_DEATH_RP_WHITELIST)) {
                server.getPlayerList().getWhiteList()
                        .remove(new net.minecraft.server.players.UserWhiteListEntry(player.getGameProfile()));
                LOGGER.info("[DeathRP] {} removed from whitelist.", player.getName().getString());
            }
        } catch (Exception e) { LOGGER.error("[DeathRP] Could not remove from whitelist", e); }

        // Historique
        try {
            String cause = player.getLastDamageSource() != null
                    ? player.getLastDamageSource().typeHolder()
                    .unwrapKey().map(k -> k.location().getPath()).orElse("unknown")
                    : "unknown";
            ensureHistoryInitialized();
            history.add(new DeathHistoryEntry(
                    player.getName().getString(), player.getUUID().toString(), cause, rawDeathMsg));
            saveHistory();
        } catch (Exception e) { LOGGER.error("[DeathRP] Failed to record history", e); }
    }

    // ── Toggles ─────────────────────────────────────────────────────────────────

    public static void broadcastGlobalToggle(String staffName, boolean enabled, MinecraftServer server) {
        try {
            var msgConfig  = enabled ? RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MSG  : RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MSG;
            var modeConfig = enabled ? RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MODE : RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MODE;
            if (msgConfig == null || modeConfig == null) return;
            String raw  = msgConfig.get().replace("{staff}", staffName);
            String mode = modeConfig.get();
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                sendMessageToPlayer(p, raw, mode);
        } catch (IllegalStateException e) { LOGGER.warn("[DeathRP] Config GLOBAL_TOGGLE_MSG unavailable"); }

        playSound(server, null,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND_PITCH, "global toggle sound");
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
        } catch (IllegalStateException e) { LOGGER.warn("[DeathRP] Config PLAYER_TOGGLE_MSG unavailable"); }

        playSound(target.getServer(), target,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND_PITCH, "individual toggle sound");
    }

    // ── Son ─────────────────────────────────────────────────────────────────────

    private static void playSound(
            MinecraftServer server, ServerPlayer solo,
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
            if (rl == null) return;
            Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(rl));
            if (solo != null) {
                solo.connection.send(new ClientboundSoundPacket(holder, SoundSource.MASTER,
                        solo.getX(), solo.getY(), solo.getZ(), volume, pitch, solo.getRandom().nextLong()));
            } else {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.connection.send(new ClientboundSoundPacket(holder, SoundSource.MASTER,
                            p.getX(), p.getY(), p.getZ(), volume, pitch, p.getRandom().nextLong()));
                }
            }
        } catch (IllegalStateException e) { LOGGER.warn("[DeathRP] Config {} unavailable", label); }
        catch (Exception e) { LOGGER.error("[DeathRP] Error playing {}", label, e); }
    }

    // ── Affichage (délègue à ImmersivePresetHelper) ──────────────────────────────

    /**
     * Envoie un message à un joueur en utilisant le mode d'affichage configuré.
     *
     * Modes supportés :
     *   CHAT, ACTION_BAR, TITLE                  → comportement inchangé
     *   IMMERSIVE                                 → preset "default"
     *   IMMERSIVE:death, IMMERSIVE:alert, etc.    → preset nommé (4.1.6)
     */
    public static void sendMessageToPlayer(ServerPlayer player, String rawMessage, String mode) {
        ImmersivePresetHelper.send(player, rawMessage, mode);
    }
}