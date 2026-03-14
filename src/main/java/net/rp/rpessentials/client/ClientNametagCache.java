package net.rp.rpessentials.client;

import net.rp.rpessentials.SyncNametagDataPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache côté CLIENT des données nametag reçues via SyncNametagDataPacket.
 *
 * Mis à jour par handle() à chaque réception de packet.
 * Consulté par NametagEventHandler à chaque frame de rendu.
 *
 * Thread-safe : ConcurrentHashMap (tick thread + render thread).
 */
public class ClientNametagCache {

    // UUID du joueur cible → ses données nametag
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

    /** Appelé à la déconnexion du client (ClientEventHandler). */
    public static void reset() {
        cache.clear();
    }

    // =========================================================================
    // DATA CLASS
    // =========================================================================

    public record NametagData(
            String displayName,
            String prefix,
            String suffix,
            String profession,
            boolean isStaff
    ) {}
}
