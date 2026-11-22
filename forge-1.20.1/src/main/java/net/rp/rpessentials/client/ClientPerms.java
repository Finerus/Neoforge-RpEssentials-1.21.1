package net.rp.rpessentials.client;

import net.rp.rpessentials.permission.RpPerm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Permission nodes granted to the local player, sent by the server (SyncPermissionsPacket) at login
 * and when roles change. Only used to show or hide GUI buttons: the server checks every action again.
 */
public final class ClientPerms {

    private static final Set<String> granted = new HashSet<>();

    private ClientPerms() {}

    public static void set(List<String> keys) {
        granted.clear();
        granted.addAll(keys);
    }

    public static boolean has(RpPerm perm) { return granted.contains(perm.key()); }

    public static void clear() { granted.clear(); }
}
