package net.rp.rpessentials.permission;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Checks of the RpEssentials permission nodes, through the NeoForge permission API. With LuckPerms
 * set as permission handler (permissionHandler in neoforge-server.toml), LuckPerms decides; a node
 * it does not define, or any node without LuckPerms, is resolved here:
 * <ol>
 *   <li>a node set on the player in LuckPerms (when LuckPerms is installed and the sync is on);</li>
 *   <li>the roles of the player (rpessentials-core.toml): granted, or refused with "-node";</li>
 *   <li>staff members get the nodes that staff had before ({@link RpPerm#staffDefault()});</li>
 *   <li>the OP level of the node ({@link RpPerm#opLevel()}).</li>
 * </ol>
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public final class RpPerms {

    public static final String PREFIX = "rpessentials.";

    private static final Map<RpPerm, PermissionNode<Boolean>> NODES = new HashMap<>();
    static {
        for (RpPerm p : RpPerm.all()) {
            NODES.put(p, new PermissionNode<>(RpEssentials.MODID, p.key(), PermissionTypes.BOOLEAN,
                    (player, uuid, ctx) -> resolveDefault(player, uuid, p)));
        }
    }

    private RpPerms() {}

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(new ArrayList<>(NODES.values()));
    }

    // =========================================================================
    // CHECKS
    // =========================================================================
    public static boolean has(ServerPlayer player, RpPerm perm) {
        if (player == null) return false;
        // OP 4 owns the server: no role, refused node or LuckPerms group can lock them out of giving roles.
        if (perm == RpPerm.ROLES_SET && player.hasPermissions(4)) return true;
        // Not asked to the permission handler: LuckPerms would count its wildcards.
        if (RpPerm.explicitOnly(perm.node())) return resolveDefault(player, player.getUUID(), perm);
        try {
            return PermissionAPI.getPermission(player, NODES.get(perm));
        } catch (RuntimeException e) {
            // Permission API not ready (server starting): same answer without the handler.
            return resolveDefault(player, player.getUUID(), perm);
        }
    }

    /**
     * True when a role or LuckPerms refuses this node on purpose (a node that is simply not granted is not refused).
     */
    public static boolean refused(ServerPlayer player, RpPerm perm) {
        Boolean lp = RpEssentialsRoleManager.luckPermsValue(player.getUUID(), perm.node());
        if (lp != null) return !lp;
        return Boolean.FALSE.equals(RpEssentialsRoleManager.roleValue(player, perm));
    }

    /**
     * The player who really runs a command: with "/execute as X", the one who typed it, not X.
     * Null for the console, command blocks and functions acting as nobody.
     */
    public static ServerPlayer actor(CommandSourceStack src) {
        return src.source instanceof ServerPlayer typed ? typed : src.getPlayer();
    }

    /**
     * A player: their nodes ("/execute as" gives nobody else's). Console, command blocks, signs and functions:
     * their OP level against the node default (4 for the console); when they act as a player (a sign clicked by a
     * staff member...), that player must have the node too.
     */
    public static boolean has(CommandSourceStack src, RpPerm perm) {
        if (src.source instanceof ServerPlayer typed) return has(typed, perm);
        if (src.getEntity() instanceof ServerPlayer p && !has(p, perm)) return false;
        int level;
        if (perm.opLevel() == RpPerm.SENSITIVE) level = RpEssentialsPermissions.sensitiveLevel();
        else if (perm.opLevel() <= 4) level = perm.opLevel();
        else level = 2; // staff and flag nodes: OP 2, like the staff check of the console before
        if (perm.staffDefault()) level = Math.min(level, 2);
        return src.hasPermission(level);
    }

    /** For {@code .requires(...)} of a command. */
    public static Predicate<CommandSourceStack> cmd(RpPerm perm) {
        return src -> has(src, perm);
    }

    /** For a command parent: usable when one of the nodes is granted. */
    public static Predicate<CommandSourceStack> any(RpPerm... perms) {
        return src -> {
            for (RpPerm p : perms) if (has(src, p)) return true;
            return false;
        };
    }

    /** /rpessentials manage: at least one manage action allowed. */
    public static boolean anyManage(CommandSourceStack src) {
        for (net.rp.rpessentials.moderation.ManageService.Action a : net.rp.rpessentials.moderation.ManageService.Action.values()) {
            if (has(src, RpPerm.manage(a))) return true;
        }
        return false;
    }

    // =========================================================================
    // DEFAULT RESOLUTION
    // =========================================================================
    private static boolean resolveDefault(ServerPlayer player, UUID uuid, RpPerm perm) {
        if (player == null) return false;
        Boolean lp = RpEssentialsRoleManager.luckPermsValue(uuid, perm.node());
        if (lp != null) return lp;
        Boolean role = RpEssentialsRoleManager.roleValue(player, perm);
        if (role != null) return role;
        if (perm == RpPerm.STAFF) return opBypass(player);
        if (perm.staffDefault() && has(player, RpPerm.STAFF)) return true;
        int level = perm.opLevel() == RpPerm.SENSITIVE ? RpEssentialsPermissions.sensitiveLevel() : perm.opLevel();
        return level <= 4 && player.hasPermissions(level);
    }

    /** opLevelBypass of the config: from this OP level, a player is staff. 0 = off. */
    private static boolean opBypass(ServerPlayer player) {
        int level = Cfg.get(RpEssentialsConfig.OP_LEVEL_BYPASS, 0);
        return level > 0 && player.hasPermissions(level);
    }

    // =========================================================================
    // REFRESH (roles changed, config reloaded, LuckPerms changed)
    // =========================================================================
    /** Nodes granted to a player, sent to their client for the GUIs. */
    public static List<String> granted(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (RpPerm p : RpPerm.all()) if (has(player, p)) out.add(p.key());
        return out;
    }

    /** Forgets the cached roles, then resends the command tree, and the rest when the access changed. */
    public static void refresh(ServerPlayer player) {
        RpEssentialsRoleManager.invalidate(player.getUUID());
        MinecraftServer server = player.getServer();
        if (server != null) AccessRefresh.apply(server, player, true);
    }

    public static void refreshAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) refresh(p);
    }
}
