package net.rp.rpessentials;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.moderation.PlaytimeManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestion des avertissements de bordure de monde et des zones nommées.
 *
 * 4.1.6 : sendMessage() délègue à {@link ImmersivePresetHelper}
 * pour supporter les modes IMMERSIVE:zone, IMMERSIVE:alert, etc.
 */
public class WorldBorderManager {

    private static final Map<UUID, Boolean>      hasBeenWarned   = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>>  playerZoneState = new ConcurrentHashMap<>();
    private static boolean systemInitialized = false;

    public static void tick(MinecraftServer server) {
        try {
            if (RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING == null) return;
            if (!RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING.get()) return;
            if (!systemInitialized) {
                systemInitialized = true;
                RpEssentials.LOGGER.info("[RpEssentials] System initialized. Hi, have a great day! - Finerus");
            }
        } catch (Exception e) { return; }

        double maxDist   = RpEssentialsConfig.WORLD_BORDER_DISTANCE.get();
        double maxDistSq = maxDist * maxDist;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                checkPlayerDistance(player, maxDistSq, maxDist);
                checkNamedZones(player);
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[WorldBorder] Error checking player {}", player.getName().getString(), e);
            }
        }
    }

    // =========================================================================
    // DISTANCE
    // =========================================================================

    private static void checkPlayerDistance(ServerPlayer player, double maxDistSq, double maxDist) {
        var spawn = player.serverLevel().getSharedSpawnPos();
        double dx = player.getX() - spawn.getX();
        double dz = player.getZ() - spawn.getZ();
        double distSq  = dx * dx + dz * dz;
        double actual  = Math.sqrt(distSq);
        UUID   uuid    = player.getUUID();
        boolean outside = distSq > maxDistSq;
        boolean warned  = hasBeenWarned.getOrDefault(uuid, false);

        if (outside && !warned) {
            sendBorderWarning(player, actual);
            hasBeenWarned.put(uuid, true);
        } else if (!outside && warned) {
            hasBeenWarned.put(uuid, false);
        }
    }

    // =========================================================================
    // ZONES NOMMÉES
    // =========================================================================

    private static void checkNamedZones(ServerPlayer player) {
        List<? extends String> zones;
        try { zones = RpEssentialsConfig.NAMED_ZONES.get(); }
        catch (Exception e) { return; }

        UUID uuid = player.getUUID();
        Set<String> current = playerZoneState.computeIfAbsent(uuid,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

        for (String zoneDef : zones) {
            String[] parts = zoneDef.split(";");
            if (parts.length < 5) continue;
            try {
                String zoneName = parts[0].trim();
                double cx       = Double.parseDouble(parts[1].trim());
                double cz       = Double.parseDouble(parts[2].trim());
                double radius   = Double.parseDouble(parts[3].trim());
                String msgEnter = parts[4].trim();
                String msgExit  = parts.length >= 6 ? parts[5].trim() : "";

                double dx = player.getX() - cx;
                double dz = player.getZ() - cz;
                boolean inZone  = (dx * dx + dz * dz) <= (radius * radius);
                boolean wasIn   = current.contains(zoneName);

                if (inZone && !wasIn) {
                    current.add(zoneName);
                    sendZoneMessage(player, msgEnter);
                } else if (!inZone && wasIn) {
                    current.remove(zoneName);
                    if (!msgExit.isEmpty()) sendZoneMessage(player, msgExit);
                }
            } catch (Exception e) {
                RpEssentials.LOGGER.warn("[WorldBorder] Invalid zone: {}", zoneDef);
            }
        }
    }

    // =========================================================================
    // ENVOI
    // =========================================================================

    private static void sendBorderWarning(ServerPlayer player, double distance) {
        try {
            String message = RpEssentialsConfig.WORLD_BORDER_MESSAGE.get()
                    .replace("{distance}", String.format("%.0f", distance))
                    .replace("{player}",   player.getName().getString());
            sendMessage(player, message);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 1.0f, 0.5f);
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[WorldBorder] Error sending border warning", e);
        }
    }

    private static void sendZoneMessage(ServerPlayer player, String message) {
        try { sendMessage(player, message); }
        catch (Exception e) { RpEssentials.LOGGER.error("[WorldBorder] Error sending zone message", e); }
    }

    /**
     * Délègue à ImmersivePresetHelper pour supporter les modes nommés.
     * Le mode est lu depuis la config : "ACTION_BAR", "CHAT", "IMMERSIVE",
     * "IMMERSIVE:zone", "IMMERSIVE:alert", etc.
     */
    private static void sendMessage(ServerPlayer player, String message) {
        String mode = "ACTION_BAR";
        try { mode = RpEssentialsConfig.ZONE_MESSAGE_MODE.get().toUpperCase(); }
        catch (Exception ignored) {}

        ImmersivePresetHelper.send(player, message, mode);
    }

    // =========================================================================
    // CACHE
    // =========================================================================

    public static void clearCache(UUID id) {
        hasBeenWarned.remove(id);
        playerZoneState.remove(id);
    }

    public static void clearAllCache() {
        hasBeenWarned.clear();
        playerZoneState.clear();
        systemInitialized = false;
    }
}