package net.rp.rpessentials;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RpEssentialsPermissions {

    private static final Map<UUID, CacheEntry> staffCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 30000; // 30 secondes

    private static class CacheEntry {
        boolean isStaff;
        long timestamp;

        CacheEntry(boolean isStaff) {
            this.isStaff = isStaff;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            return System.currentTimeMillis() - timestamp < CACHE_DURATION;
        }
    }

    public static void clearExpiredCache() {
        staffCache.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }

    public static boolean isStaffSource(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer p) return isStaff(p);
        return src.hasPermission(2);
    }

    public static int sensitiveLevel() {
        try {
            int level = RpEssentialsConfig.OP_LEVEL_BYPASS.get();
            return level > 0 ? level : 4;
        } catch (IllegalStateException e) {
            return 4;
        }
    }

    public static boolean hasSensitiveLevel(net.minecraft.world.entity.player.Player p) {
        return p.hasPermissions(sensitiveLevel());
    }

    public static boolean canManageRoles(ServerPlayer p) { return hasSensitiveLevel(p); }

    public static boolean canEditSensitiveConfig(ServerPlayer p) { return hasSensitiveLevel(p); }

    /**
     * Vérifie si un joueur est staff.
     * Hiérarchie : Tags vanilla → Niveau OP → Groupes LuckPerms (optionnel)
     */
    public static boolean isStaff(ServerPlayer player) {
        if (player == null) return false;

        CacheEntry cached = staffCache.get(player.getUUID());
        if (cached != null && cached.isValid()) return cached.isStaff;

        boolean result = RpEssentialsRoleManager.has(player, RpEssentialsRoleManager.Permission.IS_STAFF)
                || checkOpLevelBypass(player);
        staffCache.put(player.getUUID(), new CacheEntry(result));
        return result;
    }

    private static boolean checkOpLevelBypass(ServerPlayer player) {
        try {
            int opLevel = RpEssentialsConfig.OP_LEVEL_BYPASS.get();
            return opLevel > 0 && player.hasPermissions(opLevel);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    // -----------

    /**
     * Invalide le cache pour un joueur (appeler à la déconnexion).
     */
    public static void invalidateCache(UUID playerUUID) {
        staffCache.remove(playerUUID);
    }

    /**
     * Vide tout le cache (appeler lors d'un reload de config).
     */
    public static void clearCache() {
        staffCache.clear();
    }
}