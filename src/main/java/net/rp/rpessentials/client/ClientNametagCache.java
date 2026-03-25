package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.rp.rpessentials.SyncNametagDataPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientNametagCache {

    private static final Map<UUID, NametagData> cache = new ConcurrentHashMap<>();

    public static void update(SyncNametagDataPacket packet) {
        cache.put(packet.targetUUID(), new NametagData(
                packet.displayName(),
                packet.prefix(),
                packet.suffix(),
                packet.profession(),
                packet.isStaff()
        ));
    }

    public static NametagData get(UUID uuid) {
        return cache.get(uuid);
    }

    public static boolean has(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /**
     * Amélioration 10 — Nettoie les entrées des joueurs qui ne sont plus
     * dans la liste du serveur (déconnectés pendant que le client reste connecté).
     * Appelé périodiquement depuis RpClientTickHandler.
     */
    public static void evictDisconnected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        cache.keySet().removeIf(uuid ->
                mc.getConnection().getPlayerInfo(uuid) == null);
    }

    /** Appelé à la déconnexion du client. */
    public static void reset() {
        cache.clear();
    }

    public record NametagData(
            String displayName,
            String prefix,
            String suffix,
            String profession,
            boolean isStaff
    ) {}
}