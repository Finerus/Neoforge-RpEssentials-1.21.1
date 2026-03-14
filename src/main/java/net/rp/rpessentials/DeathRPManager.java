package net.rp.rpessentials;

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
 * Gestionnaire du système de Mort RP.
 */
public class DeathRPManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("DeathRPManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ConcurrentHashMap<UUID, Boolean> overrides = new ConcurrentHashMap<>();
    private static File dataFile = null;

    // ─── Initialisation lazy ────────────────────────────────────────────────────

    private static synchronized void ensureInitialized() {
        if (dataFile != null) return;
        try {
            File worldFolder = new File("world");
            if (!worldFolder.exists()) worldFolder = new File(".");
            File dataFolder = new File(worldFolder, "data/rpessentials");
            if (!dataFolder.exists()) dataFolder.mkdirs();
            dataFile = new File(dataFolder, "deathrp.json");
            if (dataFile.exists()) loadFromFile();
            LOGGER.info("[DeathRP] Initialise — fichier : {}", dataFile.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Echec de l'initialisation", e);
        }
    }

    public static void reload() {
        dataFile = null;
        overrides.clear();
        ensureInitialized();
    }

    // ─── Lecture / écriture ─────────────────────────────────────────────────────

    private static void loadFromFile() {
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Boolean>>() {}.getType();
            Map<String, Boolean> data = GSON.fromJson(reader, type);
            if (data == null) return;
            for (Map.Entry<String, Boolean> entry : data.entrySet()) {
                String key = entry.getKey();
                String uuidStr = key.contains(" ") ? key.substring(0, key.indexOf(' ')) : key;
                try {
                    overrides.put(UUID.fromString(uuidStr), entry.getValue());
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[DeathRP] UUID invalide dans la cle : {}", key);
                }
            }
            LOGGER.info("[DeathRP] {} override(s) charge(s).", overrides.size());
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Impossible de lire {}", dataFile.getAbsolutePath(), e);
        }
    }

    private static void saveToFile() {
        ensureInitialized();
        if (dataFile == null) return;
        Map<UUID, Boolean> snapshot = new HashMap<>(overrides);
        File targetFile = dataFile;
        CompletableFuture.runAsync(() -> {
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                Map<String, Boolean> data = new HashMap<>();
                for (Map.Entry<UUID, Boolean> entry : snapshot.entrySet()) {
                    String mcName = resolveMcName(server, entry.getKey());
                    data.put(entry.getKey() + " (" + mcName + ")", entry.getValue());
                }
                try (FileWriter writer = new FileWriter(targetFile)) {
                    GSON.toJson(data, writer);
                }
            } catch (Exception e) {
                LOGGER.error("[DeathRP] Echec de la sauvegarde", e);
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

    // ─── API publique ────────────────────────────────────────────────────────────

    public static boolean isDeathRPEnabled(UUID uuid) {
        // Vérifier d'abord si le système global est actif
        try {
            if (!RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get()) {
                // Si les horaires de mort sont configurés, les vérifier même sans global
                try {
                    if (ScheduleConfig.DEATH_HOURS_ENABLED.get() &&
                            RpEssentialsScheduleManager.isDeathHour()) {
                        // Horaire actif → appliquer quand même (sauf override individuel à false)
                        Boolean override = overrides.get(uuid);
                        return override == null || override;
                    }
                } catch (IllegalStateException ignored) {}
                // Vérifier l'override individuel
                Boolean override = overrides.get(uuid);
                return override != null && override;
            }
        } catch (IllegalStateException e) { return false; }

        // Système global actif : override individuel prioritaire
        Boolean override = overrides.get(uuid);
        if (override != null) return override;

        // Sinon : si horaires configurés, vérifier l'heure
        try {
            if (ScheduleConfig.DEATH_HOURS_ENABLED.get())
                return RpEssentialsScheduleManager.isDeathHour();
        } catch (IllegalStateException ignored) {}

        return true; // global actif, pas d'horaire configuré → toujours actif
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

    // ─── Gestion de la mort RP ──────────────────────────────────────────────────

    public static void onPlayerDeathRP(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // 1. Message de mort personnalisé à tous
        try {
            if (RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE != null) {
                String raw = RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE.get()
                        .replace("%player%",   NicknameManager.getDisplayName(player))
                        .replace("%realname%", player.getName().getString());
                Component component = ColorHelper.parseColors(raw);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(component);
                }
            }
        } catch (IllegalStateException e) {
            LOGGER.warn("[DeathRP] Config DEATH_MESSAGE non disponible", e);
        }

        // 2. Son de mort global
        playSound(server, null,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_DEATH_SOUND_PITCH,
                "son de mort");

        // 3. Retrait whitelist (optionnel)
        try {
            if (RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE != null
                    && RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get()) {
                UserWhiteList whitelist = server.getPlayerList().getWhiteList();
                whitelist.remove(new UserWhiteListEntry(player.getGameProfile()));
                LOGGER.info("[DeathRP] {} retire de la whitelist.", player.getName().getString());
            }
        } catch (IllegalStateException e) {
            LOGGER.warn("[DeathRP] Config WHITELIST_REMOVE non disponible", e);
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Impossible de retirer {} de la whitelist", player.getName().getString(), e);
        }
    }

    // ─── Notifications de toggle ─────────────────────────────────────────────────

    public static void broadcastGlobalToggle(String staffName, boolean enabled, MinecraftServer server) {
        try {
            var msgConfig  = enabled ? RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MSG  : RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MSG;
            var modeConfig = enabled ? RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MODE : RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MODE;
            if (msgConfig == null || modeConfig == null) return;
            String raw  = msgConfig.get().replace("%staff%", staffName);
            String mode = modeConfig.get();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                sendMessageToPlayer(p, raw, mode);
            }
        } catch (IllegalStateException e) {
            LOGGER.warn("[DeathRP] Config GLOBAL_TOGGLE_MSG non disponible", e);
        }

        playSound(server, null,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND_PITCH,
                "son de toggle global");
    }

    public static void notifyPlayerToggle(ServerPlayer target, boolean enabled) {
        try {
            var msgConfig  = enabled ? RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MSG  : RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MSG;
            var modeConfig = enabled ? RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MODE : RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MODE;
            if (msgConfig == null || modeConfig == null) return;
            String raw  = msgConfig.get()
                    .replace("%player%",   NicknameManager.getDisplayName(target))
                    .replace("%realname%", target.getName().getString());
            String mode = modeConfig.get();
            sendMessageToPlayer(target, raw, mode);
        } catch (IllegalStateException e) {
            LOGGER.warn("[DeathRP] Config PLAYER_TOGGLE_MSG non disponible", e);
        }

        playSound(target.getServer(), target,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND_VOLUME,
                RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND_PITCH,
                "son de toggle individuel");
    }

    // ─── Son ─────────────────────────────────────────────────────────────────────

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
                LOGGER.warn("[DeathRP] Identifiant de son invalide pour {} : {}", label, soundId);
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
            LOGGER.warn("[DeathRP] Config {} non disponible", label, e);
        } catch (Exception e) {
            LOGGER.error("[DeathRP] Erreur lors de la lecture du {}", label, e);
        }
    }

    // ─── Utilitaire d'affichage ──────────────────────────────────────────────────

    /**
     * Envoie un message brut (codes §/&) à un joueur selon le mode spécifié.
     * <p>
     * Modes : TITLE, ACTION_BAR, IMMERSIVE (fallback ACTION_BAR), CHAT (défaut).
     * <p>
     * Identique au pattern de WorldBorderManager : le builder ImmersiveMessage
     * reçoit la String brute, pas le Component.
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