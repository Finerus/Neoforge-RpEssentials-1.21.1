package net.rp.rpessentials.permission;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.UUID;

/**
 * Shortcuts kept for the code and the API: each one is now a permission node (see {@link RpPerm}).
 */
public class RpEssentialsPermissions {

    /** Staff member: node rpessentials.staff (role, LuckPerms, or opLevelBypass). */
    public static boolean isStaff(ServerPlayer player) {
        return RpPerms.has(player, RpPerm.STAFF);
    }

    public static boolean isStaffSource(CommandSourceStack src) {
        return RpPerms.has(src, RpPerm.STAFF);
    }

    /** OP level of the sensitive actions: opLevelBypass, or 4 when it is 0. */
    public static int sensitiveLevel() {
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            int level = RpEssentialsConfig.OP_LEVEL_BYPASS.get();
            return level > 0 ? level : 4;
        }
        return 4;
    }

    /** OP check only (also used on the client, which has no permission nodes). */
    public static boolean hasSensitiveLevel(net.minecraft.world.entity.player.Player p) {
        return p.hasPermissions(sensitiveLevel());
    }

    public static boolean canManageRoles(ServerPlayer p) { return RpPerms.has(p, RpPerm.ROLES_MANAGE); }

    public static boolean canEditSensitiveConfig(ServerPlayer p) { return RpPerms.has(p, RpPerm.CONFIG_SENSITIVE); }

    /** Forgets the cached roles of a player (logout, role change). */
    public static void invalidateCache(UUID playerUUID) {
        RpEssentialsRoleManager.invalidate(playerUUID);
    }

    public static void clearCache() {
        RpEssentialsRoleManager.clearAll();
    }

    public static void clearExpiredCache() {
        RpEssentialsRoleManager.clearExpired();
    }
}
