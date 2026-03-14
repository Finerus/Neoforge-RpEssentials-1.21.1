package net.oneria.oneriaserverutilities;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Gestionnaire des restrictions et permissions par métier
 * VERSION CORRIGÉE - Protection contre config non chargée
 */
public class ProfessionRestrictionManager {

    // Cache pour optimiser les performances
    private static final Map<UUID, Set<String>> playerProfessionsCache = new ConcurrentHashMap<>();
    private static final Map<String, ProfessionData> professionDataCache = new ConcurrentHashMap<>();

    // Cache des patterns regex compilés (point 2 de l'optimisation)
    // Evite de recompiler le même pattern à chaque appel
    private static final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    private static long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 30000; // 30 secondes
    private static boolean isInitialized = false;

    /**
     * Classe interne pour stocker les données d'un métier
     */
    public static class ProfessionData {
        public final String id;
        public final String displayName;
        public final String colorCode;

        public ProfessionData(String id, String displayName, String colorCode) {
            this.id = id;
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getFormattedName() {
            return colorCode + displayName;
        }
    }

    /**
     * ✅ FIX: Vérifie si un joueur est exempté des restrictions de profession
     */
    private static boolean isExemptFromProfessionRestrictions(ServerPlayer player) {
        try {
            // Vérifier si la config permet l'exemption whitelist
            if (OneriaConfig.WHITELIST_EXEMPT_PROFESSIONS != null &&
                    OneriaConfig.WHITELIST_EXEMPT_PROFESSIONS.get()) {

                // Vérifier si le joueur est dans la whitelist
                String playerName = player.getGameProfile().getName();
                if (OneriaConfig.WHITELIST != null && OneriaConfig.WHITELIST.get().contains(playerName)) {
                    return true;
                }
            }
        } catch (IllegalStateException e) {
            // Config pas chargée, on continue normalement
            OneriaServerUtilities.LOGGER.debug("[ProfessionRestrictions] Config not loaded yet for exemption check");
        } catch (Exception e) {
            OneriaServerUtilities.LOGGER.error("[ProfessionRestrictions] Error checking exemption: {}", e.getMessage());
        }
        return false;
    }

    /**
     * ✅ FIX: Recharge le cache des métiers depuis la config
     * Avec protection contre config non chargée
     */
    public static void reloadCache() {
        professionDataCache.clear();
        playerProfessionsCache.clear();
        compiledPatterns.clear(); // On vide aussi les patterns compilés
        isInitialized = false;

        try {
            // ✅ PROTECTION: Vérifier que la config est chargée
            if (ProfessionConfig.PROFESSIONS == null) {
                OneriaServerUtilities.LOGGER.warn("[ProfessionRestrictions] Config not loaded yet, skipping cache reload");
                return;
            }

            List<? extends String> professions = ProfessionConfig.PROFESSIONS.get();

            for (String professionEntry : professions) {
                String[] parts = professionEntry.split(";");
                if (parts.length == 3) {
                    String id = parts[0].toLowerCase().trim();
                    String displayName = parts[1].trim();
                    String colorCode = parts[2].trim();

                    professionDataCache.put(id, new ProfessionData(id, displayName, colorCode));
                }
            }

            lastCacheUpdate = System.currentTimeMillis();
            isInitialized = true;
            OneriaServerUtilities.LOGGER.info("[ProfessionRestrictions] Loaded {} professions", professionDataCache.size());
        } catch (IllegalStateException e) {
            // Config pas encore construite
            OneriaServerUtilities.LOGGER.warn("[ProfessionRestrictions] Config not built yet: {}", e.getMessage());
        } catch (Exception e) {
            OneriaServerUtilities.LOGGER.error("[ProfessionRestrictions] Error loading profession data", e);
        }
    }

    /**
     * ✅ FIX: Vérifie si le système est initialisé
     */
    private static boolean ensureInitialized() {
        if (!isInitialized) {
            reloadCache();
        }
        return isInitialized;
    }

    /**
     * Récupère les données d'un métier
     */
    public static ProfessionData getProfessionData(String professionId) {
        if (!ensureInitialized()) {
            return null;
        }

        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_DURATION) {
            reloadCache();
        }
        return professionDataCache.get(professionId.toLowerCase());
    }

    /**
     * Récupère tous les métiers disponibles
     */
    public static Collection<ProfessionData> getAllProfessions() {
        if (!ensureInitialized()) {
            return Collections.emptyList();
        }

        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_DURATION) {
            reloadCache();
        }
        return professionDataCache.values();
    }

    /**
     * ✅ FIX: Vérifie si un joueur peut crafter un item
     */
    public static boolean canCraft(ServerPlayer player, ResourceLocation itemId) {
        // ✅ PROTECTION: Vérifier que le système est initialisé
        if (!ensureInitialized()) {
            OneriaServerUtilities.LOGGER.debug("[ProfessionRestrictions] System not initialized, allowing craft");
            return true; // Permissif si pas initialisé
        }

        // Check exemption whitelist
        if (isExemptFromProfessionRestrictions(player)) {
            return true;
        }

        try {
            // Vérifier si l'item est bloqué globalement
            if (!isGloballyBlocked(itemId, ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get())) {
                return true; // Pas de restriction globale
            }

            // Vérifier si le joueur a un métier qui autorise ce craft
            List<String> playerProfessions = LicenseManager.getLicenses(player.getUUID());
            return hasPermission(playerProfessions, itemId, ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get());
        } catch (Exception e) {
            OneriaServerUtilities.LOGGER.error("[ProfessionRestrictions] Error checking craft permission: {}", e.getMessage());
            return true; // Permissif en cas d'erreur
        }
    }

    /**
     * ✅ FIX: Vérifie si un joueur peut casser un bloc
     */
    public static boolean canBreakBlock(ServerPlayer player, ResourceLocation blockId) {
        if (!ensureInitialized()) {
            return true;
        }

        if (isExemptFromProfessionRestrictions(player)) {
            return true;
        }

        try {
            if (!isGloballyBlocked(blockId, ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS.get())) {
                return true;
            }

            List<String> playerProfessions = LicenseManager.getLicenses(player.getUUID());
            return hasPermission(playerProfessions, blockId, ProfessionConfig.PROFESSION_ALLOWED_BLOCKS.get());
        } catch (Exception e) {
            OneriaServerUtilities.LOGGER.error("[ProfessionRestrictions] Error checking break permission: {}", e.getMessage());
            return true;
        }
    }

    /**
     * ✅ FIX: Vérifie si un joueur peut utiliser un item
     */
    public static boolean canUseItem(ServerPlayer player, ResourceLocation itemId) {
        if (!ensureInitialized()) {
            return true;
        }

        if (isExemptFromProfessionRestrictions(player)) {
            return true;
        }

        try {
            if (!isGloballyBlocked(itemId, ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get())) {
                return true;
            }

            List<String> playerProfessions = LicenseManager.getLicenses(player.getUUID());
            return hasPermission(playerProfessions, itemId, ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get());
        } catch (Exception e) {
            OneriaServerUtilities.LOGGER.error("[ProfessionRestrictions] Error checking use permission: {}", e.getMessage());
            return true;
        }
    }

    /**
     * ✅ FIX: Vérifie si un joueur peut équiper un item
     */
    public static boolean canEquip(ServerPlayer player, ResourceLocation itemId) {
        if (!ensureInitialized()) {
            return true;
        }

        if (isExemptFromProfessionRestrictions(player)) {
            return true;
        }

        try {
            if (!isGloballyBlocked(itemId, ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get())) {
                return true;
            }

            List<String> playerProfessions = LicenseManager.getLicenses(player.getUUID());
            return hasPermission(playerProfessions, itemId, ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get());
        } catch (Exception e) {
            OneriaServerUtilities.LOGGER.error("[ProfessionRestrictions] Error checking equip permission: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Vérifie si une ressource est bloquée globalement (PUBLIC pour tooltips)
     */
    public static boolean isGloballyBlocked(ResourceLocation resourceId, List<? extends String> blockedList) {
        String resourceString = resourceId.toString();

        for (String blocked : blockedList) {
            if (matchesPattern(resourceString, blocked)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie si un joueur a la permission via ses métiers
     */
    private static boolean hasPermission(List<String> professions, ResourceLocation resourceId, List<? extends String> allowedList) {
        String resourceString = resourceId.toString();

        for (String profession : professions) {
            for (String allowEntry : allowedList) {
                if (!allowEntry.contains(";")) continue;

                String[] parts = allowEntry.split(";", 2);
                String professionId = parts[0].toLowerCase().trim();
                String allowedItems = parts[1];

                if (!professionId.equals(profession.toLowerCase())) continue;

                for (String allowedItem : allowedItems.split(",")) {
                    allowedItem = allowedItem.trim();
                    if (matchesPattern(resourceString, allowedItem)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Vérifie si une ressource correspond à un pattern (supporte les wildcards *)
     * Les patterns avec wildcard sont compilés une seule fois et mis en cache.
     */
    private static boolean matchesPattern(String resourceId, String pattern) {
        pattern = pattern.trim();

        // Correspondance exacte - pas besoin de regex
        if (resourceId.equals(pattern)) {
            return true;
        }

        // Pattern avec wildcard : on compile une seule fois et on met en cache
        if (pattern.contains("*")) {
            Pattern compiled = compiledPatterns.computeIfAbsent(pattern, p -> {
                String regex = p.replace(".", "\\.").replace("*", ".*");
                return Pattern.compile(regex);
            });
            return compiled.matcher(resourceId).matches();
        }

        return false;
    }

    /**
     * Récupère les métiers requis pour une action
     */
    public static String getRequiredProfessions(ResourceLocation resourceId, List<? extends String> allowedList) {
        if (!ensureInitialized()) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_SYSTEM_NOT_INIT);
        }

        String resourceString = resourceId.toString();
        Set<String> requiredProfessions = new HashSet<>();

        for (String allowEntry : allowedList) {
            if (!allowEntry.contains(";")) continue;

            String[] parts = allowEntry.split(";", 2);
            String professionId = parts[0].toLowerCase().trim();
            String allowedItems = parts[1];

            for (String allowedItem : allowedItems.split(",")) {
                allowedItem = allowedItem.trim();
                if (matchesPattern(resourceString, allowedItem)) {
                    ProfessionData data = getProfessionData(professionId);
                    if (data != null) {
                        requiredProfessions.add(data.getFormattedName());
                    }
                }
            }
        }

        return requiredProfessions.isEmpty()
                ? MessagesConfig.get(MessagesConfig.PROFESSION_NONE_AVAILABLE)
                : String.join("§7, ", requiredProfessions);
    }

    /**
     * Récupère le message formaté pour un craft bloqué
     */
    public static String getCraftBlockedMessage(ResourceLocation itemId) {
        try {
            String professions = getRequiredProfessions(itemId, ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get());
            return ProfessionConfig.MSG_CRAFT_BLOCKED.get()
                    .replace("{item}", itemId.toString())
                    .replace("{profession}", professions);
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_CRAFT_BLOCKED_FALLBACK);
        }
    }

    /**
     * Récupère le message formaté pour un bloc incassable
     */
    public static String getBlockBreakBlockedMessage(ResourceLocation blockId) {
        try {
            String professions = getRequiredProfessions(blockId, ProfessionConfig.PROFESSION_ALLOWED_BLOCKS.get());
            return ProfessionConfig.MSG_BLOCK_BREAK_BLOCKED.get()
                    .replace("{item}", blockId.toString())
                    .replace("{profession}", professions);
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_BREAK_BLOCKED_FALLBACK);
        }
    }

    /**
     * Récupère le message formaté pour un item bloqué
     */
    public static String getItemUseBlockedMessage(ResourceLocation itemId) {
        try {
            String professions = getRequiredProfessions(itemId, ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get());
            return ProfessionConfig.MSG_ITEM_USE_BLOCKED.get()
                    .replace("{item}", itemId.toString())
                    .replace("{profession}", professions);
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_USE_BLOCKED_FALLBACK);
        }
    }

    /**
     * Récupère le message formaté pour un équipement bloqué
     */
    public static String getEquipmentBlockedMessage(ResourceLocation itemId) {
        try {
            String professions = getRequiredProfessions(itemId, ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get());
            return ProfessionConfig.MSG_EQUIPMENT_BLOCKED.get()
                    .replace("{item}", itemId.toString())
                    .replace("{profession}", professions);
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_EQUIP_BLOCKED_FALLBACK);
        }
    }

    /**
     * Invalide le cache pour un joueur
     */
    public static void invalidatePlayerCache(UUID playerUUID) {
        playerProfessionsCache.remove(playerUUID);
    }

    /**
     * Nettoie tout le cache
     */
    public static void clearCache() {
        playerProfessionsCache.clear();
        professionDataCache.clear();
        compiledPatterns.clear();
        lastCacheUpdate = 0;
        isInitialized = false;
    }
}