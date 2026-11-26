package net.rp.rpessentials.permission;

import net.minecraft.server.MinecraftServer;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.profession.PendingProfileManager;

import java.util.UUID;

/**
 * Role given to a player who is not connected: applied when they log in, and already counted by the schedule
 * ({@link net.rp.rpessentials.gameplay.ScheduleGate}), so it can let them in while the server is closed.
 */
public final class OfflineRoles {

    private OfflineRoles() {}

    /** Id of the role as written in the config, null when unknown. */
    public static String canonical(String roleId) {
        if (!Cfg.ready(RpEssentialsConfig.SPEC)) return null;
        for (String entry : RpEssentialsConfig.ROLES.get()) {
            String id = entry.split(";", 2)[0].trim();
            if (id.equalsIgnoreCase(roleId)) return id;
        }
        return null;
    }

    /** Keeps the role for the next login of the player. False when the role does not exist. */
    public static boolean give(MinecraftServer server, UUID uuid, String name, String roleId) {
        String id = canonical(roleId);
        if (id == null) return false;
        if (!PendingProfileManager.hasPending(uuid)) PendingProfileManager.addPending(uuid, name);
        PendingProfileManager.setRole(uuid, id);
        String group = id;
        for (String entry : RpEssentialsConfig.ROLES.get()) {
            String[] p = entry.split(";", 3);
            if (p[0].trim().equals(id) && p.length > 1 && !p[1].isBlank()) group = p[1].trim();
        }
        // LuckPerms accepts a UUID: no need to wait for the login.
        if (net.rp.rpessentials.RpEssentials.luckPermsPresent()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    "lp user " + uuid + " parent set " + group);
        }
        return true;
    }
}
