package net.rp.rpessentials;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldBorderManager {

    private static final Map<UUID, Boolean> hasBeenPrewarned = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> hasBeenWarned    = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> playerZoneState = new ConcurrentHashMap<>();
    private static boolean systemInitialized = false;

    // =========================================================================
    // TICK PRINCIPAL
    // =========================================================================
    public static void tick(MinecraftServer server) {
        try {
            if (RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING == null) return;
            if (!RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING.get()) return;
            if (!systemInitialized) {
                systemInitialized = true;
                RpEssentials.LOGGER.info("[RpEssentials] System initialized. Hi, have a great day! - Finerus");
            }
        } catch (Exception e) { return; }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                checkPlayerDistance(server, player);
                checkNamedZones(player);
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[WorldBorder] Error checking player {}", player.getName().getString(), e);
            }
        }
    }

    // =========================================================================
    // DISTANCE + TELEPORT
    // =========================================================================
    private static void checkPlayerDistance(MinecraftServer server, ServerPlayer player) {
        String dimId = player.level().dimension().location().toString();
        DimBorderConfig cfg = resolveDimConfig(dimId);

        if (cfg.distance <= 0) return;

        double maxDistSq     = (double) cfg.distance * cfg.distance;
        int prewarnDist      = resolvePrewarnDistance(dimId, cfg.distance);
        double prewarnDistSq = prewarnDist > 0 ? (double) prewarnDist * prewarnDist : -1;

        var spawn = player.serverLevel().getSharedSpawnPos();
        double dx    = player.getX() - spawn.getX();
        double dz    = player.getZ() - spawn.getZ();
        double distSq  = dx * dx + dz * dz;
        double actual  = Math.sqrt(distSq);
        UUID uuid = player.getUUID();

        boolean outsideBorder  = distSq > maxDistSq;
        boolean outsidePrewarn = prewarnDistSq > 0 && distSq > prewarnDistSq;

        // Prewarn
        if (outsidePrewarn && !outsideBorder) {
            if (!hasBeenPrewarned.getOrDefault(uuid, false)) {
                sendPrewarnMessage(player, actual, cfg.distance);
                hasBeenPrewarned.put(uuid, true);
            }
        } else if (!outsidePrewarn) {
            hasBeenPrewarned.put(uuid, false);
        }

        // Bordure atteinte
        if (outsideBorder) {
            if (!hasBeenWarned.getOrDefault(uuid, false)) {
                sendBorderWarning(player, actual);
                hasBeenWarned.put(uuid, true);
                hasBeenPrewarned.put(uuid, true);

                if (cfg.teleport) {
                    teleportToOpposite(server, player, spawn.getX(), spawn.getZ(), cfg.distance);
                }
            }
        } else {
            hasBeenWarned.put(uuid, false);
        }
    }

    private static void teleportToOpposite(MinecraftServer server, ServerPlayer player,
                                           double spawnX, double spawnZ, int distance) {
        double dx = player.getX() - spawnX;
        double dz = player.getZ() - spawnZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist == 0) return;

        // Direction normalisée inversée, à 95% du rayon pour éviter la boucle immédiate
        double ratio = (distance * 0.95) / dist;
        double targetX = spawnX - dx * ratio;
        double targetZ = spawnZ - dz * ratio;

        ServerLevel level = player.serverLevel();
        double targetY = findSafeY(level, (int) Math.floor(targetX), (int) Math.floor(targetZ));

        player.teleportTo(level, targetX, targetY, targetZ,
                player.getYRot(), player.getXRot());

        RpEssentials.LOGGER.info("[WorldBorder] Teleported {} to opposite side ({},{},{}).",
                player.getName().getString(),
                Math.floor(targetX), targetY, Math.floor(targetZ));
    }

    private static double findSafeY(ServerLevel level, int x, int z) {
        // Cherche un bloc solide en descendant depuis y=320
        for (int y = 320; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isSolid()
                    && !level.getBlockState(pos.above()).isSolid()
                    && !level.getBlockState(pos.above(2)).isSolid()) {
                return y + 1;
            }
        }
        return 64;
    }

    // =========================================================================
    // NAMED ZONE
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

                String dimension = null;
                String msgEnter, msgExit;
                if (parts.length >= 6 && parts[4].contains(":")) {
                    dimension = parts[4].trim();
                    msgEnter  = parts[5].trim();
                    msgExit   = parts.length >= 7 ? parts[6].trim() : "";
                } else {
                    msgEnter = parts[4].trim();
                    msgExit  = parts.length >= 6 ? parts[5].trim() : "";
                }

                if (dimension != null) {
                    String playerDim = player.level().dimension().location().toString();
                    if (!playerDim.equals(dimension)) {
                        if (current.contains(zoneName)) {
                            current.remove(zoneName);
                            if (!msgExit.isEmpty()) sendZoneMessage(player, msgExit);
                        }
                        continue;
                    }
                }

                double ddx    = player.getX() - cx;
                double ddz    = player.getZ() - cz;
                boolean inZone = (ddx * ddx + ddz * ddz) <= (radius * radius);
                boolean wasIn  = current.contains(zoneName);

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
    // RÉSOLUTION CONFIG PAR DIMENSION
    // =========================================================================
    private record DimBorderConfig(int distance, boolean teleport) {}

    private static DimBorderConfig resolveDimConfig(String dimId) {
        try {
            List<? extends String> overrides = RpEssentialsConfig.WORLD_BORDER_DIMENSION_OVERRIDES.get();
            for (String entry : overrides) {
                String[] parts = entry.split(";", 3);
                if (parts.length < 3) continue;
                if (!parts[0].trim().equals(dimId)) continue;
                int dist      = Integer.parseInt(parts[1].trim());
                boolean tp    = Boolean.parseBoolean(parts[2].trim());
                int effective = dist > 0 ? dist : RpEssentialsConfig.WORLD_BORDER_DISTANCE.get();
                return new DimBorderConfig(effective, tp);
            }
            // Pas d'override : valeurs globales
            return new DimBorderConfig(
                    RpEssentialsConfig.WORLD_BORDER_DISTANCE.get(),
                    RpEssentialsConfig.WORLD_BORDER_TELEPORT_ENABLED.get());
        } catch (IllegalStateException e) {
            return new DimBorderConfig(0, false);
        }
    }

    private static int resolvePrewarnDistance(String dimId, int borderDistance) {
        try {
            // override dim
            List<? extends String> overrides = RpEssentialsConfig.WORLD_BORDER_DIMENSION_OVERRIDES.get();
            for (String entry : overrides) {
                String[] parts = entry.split(";", 4);
                if (parts.length < 4) continue;
                if (!parts[0].trim().equals(dimId)) continue;
                // dimension;distance;teleport;prewarnDistance
                int prewarn = Integer.parseInt(parts[3].trim());
                return prewarn > 0 && prewarn < borderDistance ? prewarn : 0;
            }
            int global = RpEssentialsConfig.WORLD_BORDER_PREWARN_DISTANCE.get();
            return global > 0 && global < borderDistance ? global : 0;
        } catch (IllegalStateException e) {
            return 0;
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
            RpEssentials.LOGGER.error("[WorldBorder] Error sending border warning.", e);
        }
    }

    private static void sendPrewarnMessage(ServerPlayer player, double distance, int borderDistance) {
        try {
            String message = RpEssentialsConfig.WORLD_BORDER_PREWARN_MESSAGE.get()
                    .replace("{distance}", String.format("%.0f", distance))
                    .replace("{player}",   player.getName().getString())
                    .replace("{border}",   String.valueOf(borderDistance));
            sendMessage(player, message);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0f, 1.2f);
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[WorldBorder] Error sending prewarn message.", e);
        }
    }

    private static void sendZoneMessage(ServerPlayer player, String message) {
        try { sendMessage(player, message); }
        catch (Exception e) { RpEssentials.LOGGER.error("[WorldBorder] Error sending zone message.", e); }
    }

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
        hasBeenPrewarned.remove(id);
        playerZoneState.remove(id);
    }

    public static void clearAllCache() {
        hasBeenWarned.clear();
        hasBeenPrewarned.clear();
        playerZoneState.clear();
        systemInitialized = false;
    }
}