package net.rp.rpessentials.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ProfessionConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ProfessionRestrictionManager {

    // =========================================================================
    // CACHES
    // =========================================================================

    private static final Map<UUID, Set<String>> playerProfessionsCache = new ConcurrentHashMap<>();
    private static final Map<String, ProfessionData> professionDataCache = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    // Anti-spam pour les messages de craft bloqué (utilisé par les mixins)
    private static final Map<UUID, Long> craftMessageCooldown = new ConcurrentHashMap<>();
    private static final long CRAFT_MESSAGE_COOLDOWN_MS = 2000L;

    private static long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 30000L;
    private static boolean isInitialized = false;

    // =========================================================================
    // DATA CLASS
    // =========================================================================

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

    // =========================================================================
    // INITIALISATION
    // =========================================================================

    public static void reloadCache() {
        professionDataCache.clear();
        playerProfessionsCache.clear();
        compiledPatterns.clear();
        craftMessageCooldown.clear();
        isInitialized = false;

        try {
            if (ProfessionConfig.PROFESSIONS == null) {
                RpEssentials.LOGGER.warn("[ProfessionRestrictions] Config not loaded yet, skipping cache reload");
                return;
            }
            for (String entry : ProfessionConfig.PROFESSIONS.get()) {
                String[] parts = entry.split(";");
                if (parts.length == 3) {
                    String id = parts[0].toLowerCase().trim();
                    professionDataCache.put(id, new ProfessionData(id, parts[1].trim(), parts[2].trim()));
                }
            }
            lastCacheUpdate = System.currentTimeMillis();
            isInitialized = true;
            RpEssentials.LOGGER.info("[ProfessionRestrictions] Loaded {} professions", professionDataCache.size());
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.warn("[ProfessionRestrictions] Config not built yet: {}", e.getMessage());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[ProfessionRestrictions] Error loading profession data", e);
        }
    }

    private static boolean ensureInitialized() {
        if (!isInitialized) reloadCache();
        return isInitialized;
    }

    // =========================================================================
    // EXEMPTION WHITELIST
    // =========================================================================

    private static boolean isExemptFromProfessionRestrictions(ServerPlayer player) {
        try {
            if (RpEssentialsConfig.WHITELIST_EXEMPT_PROFESSIONS != null
                    && RpEssentialsConfig.WHITELIST_EXEMPT_PROFESSIONS.get()) {
                String playerName = player.getGameProfile().getName();
                if (RpEssentialsConfig.WHITELIST != null
                        && RpEssentialsConfig.WHITELIST.get().contains(playerName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // =========================================================================
    // MÉTHODE GÉNÉRIQUE — cœur du système
    // Remplace la duplication dans canCraft / canBreakBlock / canUseItem / canEquip
    // =========================================================================

    private static boolean canPerformAction(ServerPlayer player, ResourceLocation id,
                                            Supplier<List<? extends String>> globalList,
                                            Supplier<List<? extends String>> allowedList) {
        if (!ensureInitialized()) return true;
        if (isExemptFromProfessionRestrictions(player)) return true;
        try {
            if (!isGloballyBlocked(id, globalList.get())) return true;
            return hasPermission(LicenseManager.getLicenses(player.getUUID()), id, allowedList.get());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[ProfessionRestrictions] Error checking action: {}", e.getMessage());
            return true;
        }
    }

    // =========================================================================
    // API PUBLIQUE — VÉRIFICATIONS
    // =========================================================================

    public static boolean canCraft(ServerPlayer player, ResourceLocation itemId) {
        return canPerformAction(player, itemId,
                ProfessionConfig.GLOBAL_BLOCKED_CRAFTS::get,
                ProfessionConfig.PROFESSION_ALLOWED_CRAFTS::get);
    }

    public static boolean canBreakBlock(ServerPlayer player, ResourceLocation blockId) {
        return canPerformAction(player, blockId,
                ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS::get,
                ProfessionConfig.PROFESSION_ALLOWED_BLOCKS::get);
    }

    public static boolean canUseItem(ServerPlayer player, ResourceLocation itemId) {
        return canPerformAction(player, itemId,
                ProfessionConfig.GLOBAL_BLOCKED_ITEMS::get,
                ProfessionConfig.PROFESSION_ALLOWED_ITEMS::get);
    }

    public static boolean canEquip(ServerPlayer player, ResourceLocation itemId) {
        return canPerformAction(player, itemId,
                ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT::get,
                ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT::get);
    }

    /**
     * Vérifie si un joueur peut ouvrir un conteneur (bloc) donné.
     * Format config : block_id;profession1,profession2
     * Exemple       : minecraft:anvil;forgeron
     */
    public static boolean canOpenContainer(ServerPlayer player, ResourceLocation blockId) {
        if (!ensureInitialized()) return true;
        if (isExemptFromProfessionRestrictions(player)) return true;
        try {
            List<? extends String> restrictions = ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS.get();
            if (restrictions.isEmpty()) return true;
            String blockStr = blockId.toString();
            for (String entry : restrictions) {
                if (!entry.contains(";")) continue;
                String[] parts = entry.split(";", 2);
                if (!matchesPattern(blockStr, parts[0].trim())) continue;
                List<String> licenses = LicenseManager.getLicenses(player.getUUID());
                for (String required : parts[1].split(",")) {
                    if (licenses.contains(required.trim())) return true;
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[ProfessionRestrictions] Error checking container open: {}", e.getMessage());
            return true;
        }
    }

    // =========================================================================
    // HELPER ANTI-SPAM POUR LES MIXINS DE CRAFT
    // Appelé par MixinResultSlot, MixinSmithingMenu, MixinAnvilMenu
    // =========================================================================

    /**
     * Envoie le message de craft bloqué avec anti-spam intégré (cooldown 2s).
     */
    public static void sendCraftBlockedMessage(ServerPlayer player, ResourceLocation itemId) {
        long now = System.currentTimeMillis();
        Long last = craftMessageCooldown.get(player.getUUID());
        if (last != null && now - last <= CRAFT_MESSAGE_COOLDOWN_MS) return;
        craftMessageCooldown.put(player.getUUID(), now);
        player.displayClientMessage(Component.literal(getCraftBlockedMessage(itemId)), true);
    }

    // =========================================================================
    // MESSAGES
    // =========================================================================

    public static String getCraftBlockedMessage(ResourceLocation itemId) {
        try {
            return ProfessionConfig.MSG_CRAFT_BLOCKED.get()
                    .replace("{item}", itemId.toString())
                    .replace("{profession}", getRequiredProfessions(itemId,
                            ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get()));
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_CRAFT_BLOCKED_FALLBACK);
        }
    }

    public static String getBlockBreakBlockedMessage(ResourceLocation blockId) {
        try {
            return ProfessionConfig.MSG_BLOCK_BREAK_BLOCKED.get()
                    .replace("{item}", blockId.toString())
                    .replace("{profession}", getRequiredProfessions(blockId,
                            ProfessionConfig.PROFESSION_ALLOWED_BLOCKS.get()));
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_BREAK_BLOCKED_FALLBACK);
        }
    }

    public static String getItemUseBlockedMessage(ResourceLocation itemId) {
        try {
            return ProfessionConfig.MSG_ITEM_USE_BLOCKED.get()
                    .replace("{item}", itemId.toString())
                    .replace("{profession}", getRequiredProfessions(itemId,
                            ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get()));
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_USE_BLOCKED_FALLBACK);
        }
    }

    public static String getEquipmentBlockedMessage(ResourceLocation itemId) {
        try {
            return ProfessionConfig.MSG_EQUIPMENT_BLOCKED.get()
                    .replace("{item}", itemId.toString())
                    .replace("{profession}", getRequiredProfessions(itemId,
                            ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get()));
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_EQUIP_BLOCKED_FALLBACK);
        }
    }

    public static String getContainerOpenBlockedMessage(ResourceLocation blockId) {
        try {
            return ProfessionConfig.MSG_CONTAINER_OPEN_BLOCKED.get()
                    .replace("{block}", blockId.toString())
                    .replace("{profession}", getContainerRequiredProfessions(blockId));
        } catch (Exception e) {
            return "§c✘ Vous ne pouvez pas ouvrir ce bloc. §7Métier requis.";
        }
    }

    private static String getContainerRequiredProfessions(ResourceLocation blockId) {
        try {
            String blockStr = blockId.toString();
            Set<String> result = new HashSet<>();
            for (String entry : ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS.get()) {
                if (!entry.contains(";")) continue;
                String[] parts = entry.split(";", 2);
                if (!matchesPattern(blockStr, parts[0].trim())) continue;
                for (String prof : parts[1].split(",")) {
                    ProfessionData data = getProfessionData(prof.trim());
                    result.add(data != null ? data.getFormattedName() : prof.trim());
                }
            }
            return result.isEmpty()
                    ? MessagesConfig.get(MessagesConfig.PROFESSION_NONE_AVAILABLE)
                    : String.join("§7, ", result);
        } catch (Exception e) {
            return MessagesConfig.get(MessagesConfig.PROFESSION_NONE_AVAILABLE);
        }
    }

    // =========================================================================
    // DONNÉES DES PROFESSIONS
    // =========================================================================

    public static ProfessionData getProfessionData(String professionId) {
        if (!ensureInitialized()) return null;
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_DURATION) reloadCache();
        return professionDataCache.get(professionId.toLowerCase());
    }

    public static Collection<ProfessionData> getAllProfessions() {
        if (!ensureInitialized()) return Collections.emptyList();
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_DURATION) reloadCache();
        return professionDataCache.values();
    }

    // =========================================================================
    // HELPERS INTERNES
    // =========================================================================

    public static boolean isGloballyBlocked(ResourceLocation resourceId, List<? extends String> blockedList) {
        String resourceString = resourceId.toString();
        for (String blocked : blockedList) {
            if (matchesPattern(resourceString, blocked)) return true;
        }
        return false;
    }

    private static boolean hasPermission(List<String> professions, ResourceLocation resourceId,
                                         List<? extends String> allowedList) {
        String resourceString = resourceId.toString();
        for (String profession : professions) {
            for (String allowEntry : allowedList) {
                if (!allowEntry.contains(";")) continue;
                String[] parts = allowEntry.split(";", 2);
                if (!parts[0].toLowerCase().trim().equals(profession.toLowerCase())) continue;
                for (String allowedItem : parts[1].split(",")) {
                    if (matchesPattern(resourceString, allowedItem.trim())) return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesPattern(String resourceId, String pattern) {
        pattern = pattern.trim();
        if (resourceId.equals(pattern)) return true;
        if (pattern.contains("*")) {
            Pattern compiled = compiledPatterns.computeIfAbsent(pattern, p ->
                    Pattern.compile(p.replace(".", "\\.").replace("*", ".*")));
            return compiled.matcher(resourceId).matches();
        }
        return false;
    }

    public static String getRequiredProfessions(ResourceLocation resourceId, List<? extends String> allowedList) {
        if (!ensureInitialized()) return MessagesConfig.get(MessagesConfig.PROFESSION_SYSTEM_NOT_INIT);
        String resourceString = resourceId.toString();
        Set<String> required = new HashSet<>();
        for (String allowEntry : allowedList) {
            if (!allowEntry.contains(";")) continue;
            String[] parts = allowEntry.split(";", 2);
            String professionId = parts[0].toLowerCase().trim();
            for (String allowedItem : parts[1].split(",")) {
                if (matchesPattern(resourceString, allowedItem.trim())) {
                    ProfessionData data = getProfessionData(professionId);
                    if (data != null) required.add(data.getFormattedName());
                }
            }
        }
        return required.isEmpty()
                ? MessagesConfig.get(MessagesConfig.PROFESSION_NONE_AVAILABLE)
                : String.join("§7, ", required);
    }

    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================

    public static void invalidatePlayerCache(UUID playerUUID) {
        playerProfessionsCache.remove(playerUUID);
        craftMessageCooldown.remove(playerUUID);
    }

    public static void clearCache() {
        playerProfessionsCache.clear();
        professionDataCache.clear();
        compiledPatterns.clear();
        craftMessageCooldown.clear();
        lastCacheUpdate = 0;
        isInitialized = false;
    }
}