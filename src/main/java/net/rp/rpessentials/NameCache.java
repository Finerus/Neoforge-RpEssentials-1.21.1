package net.rp.rpessentials;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minecraft names by UUID, kept in memory so that saving a data file does not look up
 * every player again. Filled at login and on first lookup.
 */
public final class NameCache {

    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    private NameCache() {}

    public static void put(UUID uuid, String name) {
        if (uuid != null && name != null && !name.isEmpty()) NAMES.put(uuid, name);
    }

    /** The Minecraft name of a player, or null when it is not known. */
    public static String get(UUID uuid) {
        String n = NAMES.get(uuid);
        if (n != null) return n;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) n = online.getName().getString();
        else if (server.getProfileCache() != null) n = server.getProfileCache().get(uuid).map(p -> p.getName()).orElse(null);
        put(uuid, n);
        return n;
    }

    /** Same as {@link #get(UUID)}, with "Unknown" when the name is not known. */
    public static String getOrUnknown(UUID uuid) {
        String n = get(uuid);
        return n != null ? n : "Unknown";
    }

    public static void clear() {
        NAMES.clear();
    }
}
