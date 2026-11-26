package net.rp.rpessentials.permission;

import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Roles of rpessentials-core.toml and the permission nodes they give.
 * <p>
 * Format of a role: {@code id;luckpermsGroup;node1,node2,...}. A node is written in full
 * ({@code rpessentials.moderation.warn}), with a wildcard ({@code rpessentials.moderation.*},
 * {@code rpessentials.*}), or prefixed by "-" to refuse it ({@code -rpessentials.manage.ban}).
 * The most precise entry wins; on a tie, the refusal wins. The old permission names (isStaff, seeAll...)
 * are converted to their node when the roles are loaded.
 * <p>
 * A player has a role when they have the vanilla tag of its id, or (useLuckPermsGroups) the LuckPerms group.
 */
public class RpEssentialsRoleManager {

    /** Role id -> normalized entries ("rpessentials.x", "-rpessentials.x", "rpessentials.x.*"). */
    private static final Map<String, Set<String>> roles = new ConcurrentHashMap<>();
    private record Cached(long expiry, Set<String> entries) {}
    private static final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 30_000L;

    // =========================================================================
    // LOADING
    // =========================================================================
    public static void reload() {
        roles.clear();
        cache.clear();
        if (!Cfg.ready(RpEssentialsConfig.SPEC)) {
            RpEssentials.LOGGER.debug("[RoleManager] Config not loaded yet.");
            return;
        }
        List<String> lines = new ArrayList<>(RpEssentialsConfig.ROLES.get());
        boolean migrated = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = line.split(";", 3);
            String id = parts[0].trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) continue;
            Set<String> entries = parseEntries(parts.length >= 3 ? parts[2] : "");
            roles.put(id, entries);
            String normalized = parts[0].trim() + ";" + (parts.length > 1 ? parts[1].trim() : "") + ";" + String.join(",", entries);
            if (!normalized.equals(line)) {
                lines.set(i, normalized);
                migrated = true;
            }
        }
        // Old permission names written as nodes once, so the file shows what is really applied.
        if (migrated) {
            RpEssentialsConfig.ROLES.set(lines);
            RpEssentialsConfig.SPEC.save();
            RpEssentials.LOGGER.info("[RoleManager] Role permissions converted to permission nodes.");
        }
    }

    /** "isStaff,rpessentials.warn,-manage.ban" -> normalized entries, unknown ones logged and dropped. */
    public static Set<String> parseEntries(String csv) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            String e = normalize(raw);
            if (e != null) out.add(e);
            else if (!raw.isBlank()) RpEssentials.LOGGER.warn("[RoleManager] Unknown permission: '{}'", raw.trim());
        }
        return out;
    }

    /** One entry in its stored form, or null when it is not a node of this mod. */
    public static String normalize(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return null;
        boolean deny = s.startsWith("-") || s.startsWith("!");
        if (deny) s = s.substring(1).trim();
        RpPerm legacy = RpPerm.byLegacy(s);
        // Old LuckPerms sync wrote the flags as rpessentials.<flag> (rpessentials.isstaff...).
        if (legacy == null && s.toLowerCase(Locale.ROOT).startsWith(RpPerms.PREFIX)) {
            legacy = RpPerm.byLegacy(s.substring(RpPerms.PREFIX.length()));
        }
        String node;
        if (legacy != null) {
            node = legacy.node();
        } else {
            s = s.toLowerCase(Locale.ROOT);
            if (s.equals("*")) s = RpPerms.PREFIX + "*";
            if (!s.startsWith(RpPerms.PREFIX)) s = RpPerms.PREFIX + s;
            String key = s.substring(RpPerms.PREFIX.length());
            if (key.equals("*") || key.endsWith(".*")) {
                String cat = key.equals("*") ? "" : key.substring(0, key.length() - 1);
                boolean known = cat.isEmpty() || RpPerm.all().stream().anyMatch(p -> p.key().startsWith(cat));
                if (!known) return null;
            } else if (RpPerm.byKey(key) == null) {
                return null;
            }
            node = s;
        }
        return deny ? "-" + node : node;
    }

    // =========================================================================
    // RESOLUTION
    // =========================================================================
    /** Value the roles of the player give to a node: true, false (refused), or null (not set). */
    public static Boolean roleValue(ServerPlayer player, RpPerm perm) {
        return evaluate(entriesOf(player), perm.node());
    }

    /** Same, for a list of role ids (offline players: their LuckPerms groups). */
    public static Boolean roleValueForRoles(Collection<String> roleIds, RpPerm perm) {
        if (roles.isEmpty()) reload();
        Set<String> entries = new LinkedHashSet<>();
        for (String id : roleIds) {
            Set<String> e = roles.get(id.toLowerCase(Locale.ROOT));
            if (e != null) entries.addAll(e);
        }
        return evaluate(entries, perm.node());
    }

    /** Most precise matching entry wins; a refusal wins a tie. */
    public static Boolean evaluate(Collection<String> entries, String node) {
        int grant = -1, deny = -1;
        for (String e : entries) {
            boolean neg = e.startsWith("-");
            String pattern = neg ? e.substring(1) : e;
            // Granting everything does not grant the nodes that must be written on their own.
            if (!neg && pattern.equals(RpPerms.PREFIX + "*") && RpPerm.explicitOnly(node)) continue;
            int score = match(pattern, node);
            if (score < 0) continue;
            if (neg) deny = Math.max(deny, score);
            else grant = Math.max(grant, score);
        }
        if (grant < 0 && deny < 0) return null;
        return grant > deny;
    }

    /** Length of the matching part (exact > longer wildcard > shorter wildcard), -1 when it does not match. */
    private static int match(String pattern, String node) {
        if (pattern.equals(node)) return 10_000;
        if (pattern.endsWith("*")) {
            String base = pattern.substring(0, pattern.length() - 1);
            if (node.startsWith(base)) return base.length();
        }
        return -1;
    }

    /** True when this tag is the one of a role with permissions, granted or refused (any case, like the roles). */
    public static boolean givesPermissions(String tag) {
        if (roles.isEmpty()) reload();
        Set<String> entries = roles.get(tag.toLowerCase(Locale.ROOT));
        return entries != null && !entries.isEmpty();
    }

    /** True when this tag is the one of a role, with or without permissions. Cheap: called at each tag change of a player. */
    public static boolean isRoleTag(String tag) {
        return roles.containsKey(tag.toLowerCase(Locale.ROOT));
    }

    /** True when the player has at least one role of the config (tag, or LuckPerms group). */
    public static boolean hasRole(ServerPlayer player) {
        if (roles.isEmpty()) reload();
        return !roleIdsOf(player).isEmpty();
    }

    private static Set<String> entriesOf(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Cached c = cache.get(uuid);
        if (c != null && c.expiry() > now) return c.entries();
        if (roles.isEmpty()) reload();
        Set<String> out = new LinkedHashSet<>();
        for (String id : roleIdsOf(player)) {
            Set<String> e = roles.get(id);
            if (e != null) out.addAll(e);
        }
        cache.put(uuid, new Cached(now + CACHE_DURATION, out));
        return out;
    }

    /** Ids of the roles the player has: vanilla tags, then LuckPerms groups (useLuckPermsGroups). */
    public static Set<String> roleIdsOf(ServerPlayer player) {
        Set<String> ids = new LinkedHashSet<>();
        for (String tag : player.getTags()) {
            String t = tag.toLowerCase(Locale.ROOT);
            if (roles.containsKey(t)) ids.add(t);
        }
        ids.addAll(luckPermsRoleIds(player.getUUID()));
        return ids;
    }

    /** Roles given by the LuckPerms groups of a player, online or logging in. */
    public static Set<String> luckPermsRoleIds(UUID uuid) {
        if (!RpEssentials.luckPermsPresent() || !Cfg.get(RpEssentialsConfig.USE_LUCKPERMS_GROUPS, true)) return Set.of();
        if (roles.isEmpty()) reload();
        Set<String> ids = new LinkedHashSet<>();
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(uuid);
            if (user != null) {
                String primary = user.getPrimaryGroup().toLowerCase(Locale.ROOT);
                if (roles.containsKey(primary)) ids.add(primary);
                for (net.luckperms.api.model.group.Group g : user.getInheritedGroups(user.getQueryOptions())) {
                    String name = g.getName().toLowerCase(Locale.ROOT);
                    if (roles.containsKey(name)) ids.add(name);
                }
            }
        } catch (NoClassDefFoundError | IllegalStateException ignored) {
            // LuckPerms absent or not loaded.
        } catch (Exception e) {
            RpEssentials.LOGGER.debug("[RoleManager] LuckPerms check failed: {}", e.getMessage());
        }
        return ids;
    }

    /**
     * Value of a node set for the player in LuckPerms (on them or their groups), when LuckPerms is
     * installed and the sync is on; null when LuckPerms does not define it.
     */
    public static Boolean luckPermsValue(UUID uuid, String node) {
        if (!RpEssentials.luckPermsPresent() || !Cfg.get(RpEssentialsConfig.LP_SYNC_ENABLED, true)) return null;
        try {
            net.luckperms.api.model.user.User user = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(uuid);
            if (user == null) return null;
            if (RpPerm.explicitOnly(node)) {
                // Set on its own in LuckPerms (user or group): a wildcard (*, rpessentials.*) does not count.
                return user.getCachedData().getPermissionData().getPermissionMap().get(node);
            }
            net.luckperms.api.util.Tristate t = user.getCachedData().getPermissionData().checkPermission(node);
            return t == net.luckperms.api.util.Tristate.UNDEFINED ? null : t.asBoolean();
        } catch (NoClassDefFoundError | IllegalStateException ignored) {
            return null;
        } catch (Exception e) {
            RpEssentials.LOGGER.debug("[RoleManager] LuckPerms node check failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * True when the permissions of a player who is not in the world yet can be fully known: no other permission mod,
     * and LuckPerms (if any) read by this mod and done loading the player.
     */
    public static boolean canDecideOffline(UUID uuid) {
        try {
            net.minecraft.resources.ResourceLocation handler = net.neoforged.neoforge.server.permission.PermissionAPI.getActivePermissionHandler();
            boolean luckPerms = handler != null && handler.getNamespace().equals("luckperms");
            if (handler != null && !luckPerms && !handler.getPath().equals("default_handler")) return false;
            if (!RpEssentials.luckPermsPresent()) return true;
            if (!Cfg.get(RpEssentialsConfig.LP_SYNC_ENABLED, true) && luckPerms) return false;
            return net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(uuid) != null;
        } catch (NoClassDefFoundError | IllegalStateException e) {
            return !RpEssentials.luckPermsPresent();
        }
    }

    // =========================================================================
    // CACHE
    // =========================================================================
    public static void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public static void clearExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().expiry() <= now);
    }

    public static void clearAll() {
        roles.clear();
        cache.clear();
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================
    /** Known role ids (completion). */
    public static Set<String> getKnownRoleIds() {
        if (roles.isEmpty()) reload();
        return Collections.unmodifiableSet(roles.keySet());
    }

    /** Entries of every role, in the file order (GUI). */
    public static Map<String, Set<String>> getRoles() {
        if (roles.isEmpty()) reload();
        return Collections.unmodifiableMap(new LinkedHashMap<>(roles));
    }
}
