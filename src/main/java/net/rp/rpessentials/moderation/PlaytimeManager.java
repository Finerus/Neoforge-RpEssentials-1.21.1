package net.rp.rpessentials.moderation;

import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.RpEssentials;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère l'uptime (session courante) et le playtime cumulatif des joueurs.
 *
 * Design :
 *   - sessionStart : timestamp de connexion de la session courante (RAM uniquement)
 *   - Le playtime cumulatif est stocké dans {@link LastConnectionManager.ConnectionEntry#totalPlaytimeMs}
 *     et persisté dans lastconnection.json.
 *
 * Performance :
 *   - Aucun tick, aucun fichier supplémentaire.
 *   - Calcul = System.currentTimeMillis() − sessionStart → O(1), zéro overhead.
 */
public class PlaytimeManager {

    /** Timestamps de début de session (login). */
    private static final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();

    // =========================================================================
    // SESSION
    // =========================================================================

    /** Enregistre le début d'une session. Appelé dans onPlayerLogin. */
    public static void onLogin(UUID uuid) {
        sessionStart.put(uuid, System.currentTimeMillis());
    }

    /**
     * Calcule la durée de la session courante et l'ajoute au cumulatif
     * dans {@link LastConnectionManager}. Appelé dans onPlayerLogout.
     */
    public static void onLogout(UUID uuid) {
        Long start = sessionStart.remove(uuid);
        if (start == null) return;
        long sessionDurationMs = System.currentTimeMillis() - start;
        LastConnectionManager.addPlaytime(uuid, sessionDurationMs);
        RpEssentials.LOGGER.debug("[PlaytimeManager] {} played {}ms this session",
                uuid, sessionDurationMs);
    }

    // =========================================================================
    // LECTURE
    // =========================================================================

    /**
     * Retourne la durée de la session courante en ms.
     * Retourne 0 si le joueur n'est pas connecté (pas de session enregistrée).
     */
    public static long getCurrentSessionMs(UUID uuid) {
        Long start = sessionStart.get(uuid);
        return start != null ? System.currentTimeMillis() - start : 0L;
    }

    /**
     * Retourne le playtime total : cumulatif persisté + session courante.
     */
    public static long getTotalPlaytimeMs(UUID uuid) {
        long persisted = LastConnectionManager.getTotalPlaytimeMs(uuid);
        long session   = getCurrentSessionMs(uuid);
        return persisted + session;
    }

    /**
     * Retourne true si le joueur a une session en cours.
     */
    public static boolean isOnline(UUID uuid) {
        return sessionStart.containsKey(uuid);
    }

    // =========================================================================
    // FORMAT
    // =========================================================================

    /**
     * Formate une durée en ms en chaîne lisible.
     * Ex : "3j 4h 12min" ou "45min" ou "< 1min"
     */
    public static String format(long ms) {
        return format(null, ms);
    }

    /** Playtime such as "3d 4h 12min", in the viewer's language (null = server language). */
    public static String format(net.minecraft.server.level.ServerPlayer v, long ms) {
        if (ms < 60_000L) return ServerLang.get(v, MessagesConfig.TIME_LESS_MINUTE);
        long totalMin = ms / 60_000L;
        long days  = totalMin / 1440L;
        long hours = (totalMin % 1440L) / 60L;
        long min   = totalMin % 60L;

        StringBuilder sb = new StringBuilder();
        if (days  > 0) sb.append(ServerLang.get(v, MessagesConfig.TIME_UNIT_DAYS, "n", String.valueOf(days))).append(' ');
        if (hours > 0) sb.append(ServerLang.get(v, MessagesConfig.TIME_UNIT_HOURS, "n", String.valueOf(hours))).append(' ');
        sb.append(ServerLang.get(v, MessagesConfig.TIME_UNIT_MINUTES, "n", String.valueOf(min)));
        return sb.toString().trim();
    }

    /** Format court "3j4h" ou "4h12m" ou "45m". */
    public static String formatShort(long ms) {
        if (ms < 60_000L) return "<1m";
        long totalMin = ms / 60_000L;
        long days  = totalMin / 1440L;
        long hours = (totalMin % 1440L) / 60L;
        long min   = totalMin % 60L;

        if (days  > 0) return days + "j" + hours + "h";
        if (hours > 0) return hours + "h" + min + "m";
        return min + "m";
    }

    public static void flush() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : new java.util.HashMap<>(sessionStart).entrySet()) {
            long elapsed = now - entry.getValue();
            if (elapsed > 0) {
                LastConnectionManager.addPlaytimeSilent(entry.getKey(), elapsed);
                sessionStart.replace(entry.getKey(), now);
            }
        }
        LastConnectionManager.flushToDisk();
    }

    // =========================================================================
    // NETTOYAGE
    // =========================================================================

    /** Appelé au ServerStopping pour éviter les fuites. */
    public static void clearAll() {
        sessionStart.clear();
    }
}