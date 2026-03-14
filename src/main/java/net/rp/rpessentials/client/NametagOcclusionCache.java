package net.rp.rpessentials.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache du résultat de l'occlusion (raycast bloc) par UUID cible.
 *
 * Le raycast ClipContext est exécuté côté client, potentiellement chaque frame
 * pour chaque joueur visible. Avec 20 joueurs à l'écran à 60 fps, ça fait
 * 1200 raycasts/seconde sans cache.
 *
 * Ce cache limite le recalcul à 1 fois par TTL_MS (50ms = 3 frames à 60fps).
 *
 * Utilisation :
 *   NametagOcclusionCache.Result r = NametagOcclusionCache.get(uuid);
 *   if (r == null || r.isExpired()) {
 *       boolean occluded = doRaycast(...);
 *       NametagOcclusionCache.put(uuid, occluded);
 *   }
 */
public class NametagOcclusionCache {

    private static final long TTL_MS = 50L; // ~3 frames à 60fps

    private static final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public static Entry get(UUID uuid) {
        return cache.get(uuid);
    }

    public static void put(UUID uuid, boolean occluded) {
        cache.put(uuid, new Entry(occluded, System.currentTimeMillis()));
    }

    /** À appeler à la déconnexion pour vider le cache. */
    public static void reset() {
        cache.clear();
    }

    // =========================================================================
    // ENTRY
    // =========================================================================

    public record Entry(boolean occluded, long timestamp) {
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TTL_MS;
        }
    }
}
