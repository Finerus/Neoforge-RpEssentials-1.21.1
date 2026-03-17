package net.rp.rpessentials;

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

    /**
     * Vérifie si un joueur est staff.
     * Hiérarchie : Tags vanilla → Niveau OP → Groupes LuckPerms (optionnel)
     */
    public static boolean isStaff(ServerPlayer player) {
        if (player == null) return false;

        CacheEntry cached = staffCache.get(player.getUUID());
        if (cached != null && cached.isValid()) {
            return cached.isStaff;
        }

        boolean result = checkStaffStatus(player);
        staffCache.put(player.getUUID(), new CacheEntry(result));
        return result;
    }

    private static boolean checkStaffStatus(ServerPlayer player) {

        // ── 1. Tags vanilla scoreboard ────────────────────────────────────────
        try {
            for (String tag : RpEssentialsConfig.STAFF_TAGS.get()) {
                if (player.getTags().contains(tag)) {
                    return true;
                }
            }
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.debug("[Permissions] Config not loaded yet for tag check");
        }

        // ── 2. Niveau OP ──────────────────────────────────────────────────────
        try {
            int opLevel = RpEssentialsConfig.OP_LEVEL_BYPASS.get();
            if (opLevel > 0 && player.hasPermissions(opLevel)) {
                return true;
            }
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.debug("[Permissions] Config not loaded yet for OP level check");
        }

        // ── 3. Groupes LuckPerms (entièrement optionnel) ──────────────────────
        // Utilise NoClassDefFoundError ET Exception pour couvrir tous les cas :
        //   - NoClassDefFoundError : LuckPerms absent du classpath
        //   - IllegalStateException : LuckPerms présent mais pas encore initialisé
        //   - Exception : toute autre erreur LuckPerms
        try {
            boolean useLp = RpEssentialsConfig.USE_LUCKPERMS_GROUPS.get();
            if (!useLp) return false;

            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUUID());

            if (user == null) return false;

            // Vérifie le groupe primaire
            String primary = user.getPrimaryGroup();
            for (String staffGroup : RpEssentialsConfig.LUCKPERMS_STAFF_GROUPS.get()) {
                if (staffGroup.equalsIgnoreCase(primary)) return true;
            }

            // Vérifie les groupes hérités
            for (net.luckperms.api.model.group.Group group :
                    user.getInheritedGroups(user.getQueryOptions())) {
                for (String staffGroup : RpEssentialsConfig.LUCKPERMS_STAFF_GROUPS.get()) {
                    if (staffGroup.equalsIgnoreCase(group.getName())) return true;
                }
            }

        } catch (NoClassDefFoundError e) {
            // LuckPerms absent — normal, on continue sans lui
        } catch (IllegalStateException e) {
            // LuckPerms pas encore chargé — normal au démarrage
        } catch (Exception e) {
            RpEssentials.LOGGER.debug("[Permissions] LuckPerms check failed: {}", e.getMessage());
        }

        return false;
    }

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