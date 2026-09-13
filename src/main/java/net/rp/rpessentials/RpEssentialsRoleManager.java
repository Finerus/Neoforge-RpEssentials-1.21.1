package net.rp.rpessentials;

import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestion des permissions par rôle.
 * Les rôles sont détectés via tags vanilla + groupes LuckPerms.
 * Format config : id;luckpermsGroup;perm1,perm2,...
 */
public class RpEssentialsRoleManager {

    public enum Permission {
        IS_STAFF,
        TAB_WHITELIST,
        SCHEDULE_WHITELIST,
        PROFESSION_WHITELIST,
        SEE_NICKNAMES,
        SEE_ALL,
        BYPASS_WORLD_BORDER,
        SPY_MESSAGES,
        BYPASS_AUTO_UNWHITELIST,
        BYPASS_DEATH_RP_WHITELIST
    }

    private static final Map<String, Set<Permission>> rolePermissions = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<Permission>> playerPermissionCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 30_000L;
    private static final Map<UUID, Long> cacheTimestamps = new ConcurrentHashMap<>();

    // =========================================================================
    // CHARGEMENT
    // =========================================================================
    public static void reload() {
        rolePermissions.clear();
        playerPermissionCache.clear();
        cacheTimestamps.clear();

        try {
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String[] parts = entry.split(";", 3);
                if (parts.length < 1) continue;
                String id = parts[0].trim().toLowerCase();
                if (id.isEmpty()) continue;

                Set<Permission> perms = EnumSet.noneOf(Permission.class);
                if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                    for (String perm : parts[2].split(",")) {
                        parsePermission(perm.trim()).ifPresent(perms::add);
                    }
                }
                rolePermissions.put(id, perms);
            }
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.debug("[RoleManager] Config not loaded yet.");
        }
    }

    private static Optional<Permission> parsePermission(String raw) {
        return switch (raw.toLowerCase()) {
            case "isstaff"                  -> Optional.of(Permission.IS_STAFF);
            case "tabwhitelist"             -> Optional.of(Permission.TAB_WHITELIST);
            case "schedulewhitelist"        -> Optional.of(Permission.SCHEDULE_WHITELIST);
            case "professionwhitelist"      -> Optional.of(Permission.PROFESSION_WHITELIST);
            case "seenicknames"             -> Optional.of(Permission.SEE_NICKNAMES);
            case "seeall"                   -> Optional.of(Permission.SEE_ALL);
            case "bypassworldborder"        -> Optional.of(Permission.BYPASS_WORLD_BORDER);
            case "spymessages"              -> Optional.of(Permission.SPY_MESSAGES);
            case "bypassautounwhitelist"    -> Optional.of(Permission.BYPASS_AUTO_UNWHITELIST);
            case "bypassdeathrpwhitelist"   -> Optional.of(Permission.BYPASS_DEATH_RP_WHITELIST);
            default -> {
                RpEssentials.LOGGER.warn("[RoleManager] Unknown permission: '{}'", raw);
                yield Optional.empty();
            }
        };
    }

    // =========================================================================
    // RÉSOLUTION DES PERMISSIONS D'UN JOUEUR
    // =========================================================================
    public static Set<Permission> getPermissions(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Long ts = cacheTimestamps.get(uuid);
        if (ts != null && System.currentTimeMillis() - ts < CACHE_DURATION) {
            Set<Permission> cached = playerPermissionCache.get(uuid);
            if (cached != null) return cached;
        }

        if (rolePermissions.isEmpty()) reload();

        Set<Permission> result = EnumSet.noneOf(Permission.class);
        Set<String> playerTags = player.getTags();

        for (String tag : playerTags) {
            Set<Permission> rolePerms = rolePermissions.get(tag.toLowerCase());
            if (rolePerms != null) result.addAll(rolePerms);
        }
        try {
            for (String staffTag : RpEssentialsConfig.STAFF_TAGS.get()) {
                if (playerTags.contains(staffTag)) {
                    result.add(Permission.IS_STAFF);
                    break;
                }
            }
        } catch (IllegalStateException ignored) {}

        try {
            if (!RpEssentialsConfig.USE_LUCKPERMS_GROUPS.get()) {
                playerPermissionCache.put(uuid, result);
                cacheTimestamps.put(uuid, System.currentTimeMillis());
                return result;
            }
        } catch (IllegalStateException ignored) {}

        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
            if (user != null) {
                String primary = user.getPrimaryGroup().toLowerCase();
                Set<Permission> primaryPerms = rolePermissions.get(primary);
                if (primaryPerms != null) result.addAll(primaryPerms);

                for (net.luckperms.api.model.group.Group group :
                        user.getInheritedGroups(user.getQueryOptions())) {
                    Set<Permission> groupPerms = rolePermissions.get(group.getName().toLowerCase());
                    if (groupPerms != null) result.addAll(groupPerms);
                }

                try {
                    List<? extends String> staffGroups = RpEssentialsConfig.LUCKPERMS_STAFF_GROUPS.get();
                    if (staffGroups.contains(primary)) result.add(Permission.IS_STAFF);
                    for (net.luckperms.api.model.group.Group group :
                            user.getInheritedGroups(user.getQueryOptions())) {
                        if (staffGroups.contains(group.getName().toLowerCase())) {
                            result.add(Permission.IS_STAFF);
                            break;
                        }
                    }
                } catch (IllegalStateException ignored) {}
            }
        } catch (NoClassDefFoundError | IllegalStateException ignored) {
        } catch (Exception e) {
            RpEssentials.LOGGER.debug("[RoleManager] LuckPerms check failed: {}", e.getMessage());
        }

        playerPermissionCache.put(uuid, result);
        cacheTimestamps.put(uuid, System.currentTimeMillis());
        return result;
    }

    public static boolean has(ServerPlayer player, Permission permission) {
        return getPermissions(player).contains(permission);
    }

    // =========================================================================
    // CACHE
    // =========================================================================
    public static void invalidate(UUID uuid) {
        playerPermissionCache.remove(uuid);
        cacheTimestamps.remove(uuid);
    }

    public static void clearExpired() {
        long now = System.currentTimeMillis();
        cacheTimestamps.entrySet().removeIf(e -> {
            if (now - e.getValue() >= CACHE_DURATION) {
                playerPermissionCache.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    public static void clearAll() {
        rolePermissions.clear();
        playerPermissionCache.clear();
        cacheTimestamps.clear();
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================
    /** Retourne les ids de rôles connus (pour l'autocomplétion). */
    public static Set<String> getKnownRoleIds() {
        if (rolePermissions.isEmpty()) reload();
        return Collections.unmodifiableSet(rolePermissions.keySet());
    }
}