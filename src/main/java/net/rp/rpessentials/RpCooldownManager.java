package net.rp.rpessentials;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de cooldowns pour les commandes RP.
 * Thread-safe — partagé entre le tick serveur et les handlers de commandes.
 */
public class RpCooldownManager {

    // Map<UUID, Map<commandName, lastUsedMillis>>
    private static final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /**
     * Vérifie si un joueur est en cooldown pour une commande donnée.
     */
    public static boolean isOnCooldown(UUID playerUUID, String command) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns == null) return false;
        Long lastUsed = playerCooldowns.get(command);
        if (lastUsed == null) return false;
        int cooldownMs = getCooldownMs(command);
        if (cooldownMs <= 0) return false;
        return (System.currentTimeMillis() - lastUsed) < cooldownMs;
    }

    /**
     * Retourne le nombre de secondes restantes avant la fin du cooldown.
     */
    public static long getRemainingSeconds(UUID playerUUID, String command) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns == null) return 0;
        Long lastUsed = playerCooldowns.get(command);
        if (lastUsed == null) return 0;
        int cooldownMs = getCooldownMs(command);
        long remaining = cooldownMs - (System.currentTimeMillis() - lastUsed);
        return remaining > 0 ? (remaining / 1000) + 1 : 0;
    }

    /**
     * Enregistre l'utilisation d'une commande pour un joueur.
     */
    public static void setCooldown(UUID playerUUID, String command) {
        cooldowns.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>())
                .put(command, System.currentTimeMillis());
    }

    /**
     * Supprime le cooldown d'un joueur (utile pour le staff).
     */
    public static void clearCooldown(UUID playerUUID, String command) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUUID);
        if (playerCooldowns != null) playerCooldowns.remove(command);
    }

    /**
     * Vide tous les cooldowns d'un joueur (à la déconnexion).
     */
    public static void clearAll(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }

    // =========================================================================
    // INTERNAL
    // =========================================================================

    private static int getCooldownMs(String command) {
        try {
            return switch (command) {
                case "action"    -> net.rp.rpessentials.config.RpConfig.ACTION_COOLDOWN_SECONDS.get() * 1000;
                case "commerce"  -> net.rp.rpessentials.config.RpConfig.COMMERCE_COOLDOWN_SECONDS.get() * 1000;
                case "incognito" -> net.rp.rpessentials.config.RpConfig.INCOGNITO_COOLDOWN_SECONDS.get() * 1000;
                default          -> 0;
            };
        } catch (IllegalStateException e) {
            return 0;
        }
    }
}