package net.rp.rpessentials.profession;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.util.RpEssentialsPatternUtils;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.api.event.ProfessionCheckEvent;
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

    private static final Map<String, ProfessionData> professionDataCache = new ConcurrentHashMap<>();

    // Anti-spam pour les messages de craft bloqué (utilisé par les mixins)
    private static final Map<UUID, Long> craftMessageCooldown = new ConcurrentHashMap<>();
    private static final long CRAFT_MESSAGE_COOLDOWN_MS = 2000L;

    private static long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 30000L;
    private static boolean isInitialized = false;
    private enum Kind { CRAFT, BREAK, USE, EQUIP }

    private static final class IdMatcher {
        private record Scoped(IdMatcher base, String dim, String zone) {}

        private final Set<String> exact = new HashSet<>();
        private final List<Pattern> wild = new ArrayList<>();
        private final Set<String> tags = new HashSet<>();
        private final List<Scoped> scoped = new ArrayList<>();

        boolean hasScoped() { return !scoped.isEmpty(); }

        void add(String raw) {
            String p = raw.trim();
            if (p.isEmpty()) return;
            int bar = p.indexOf('|');
            if (bar >= 0) {
                String dim = null;
                String zone = null;
                for (String mod : p.substring(bar + 1).split("\\|")) {
                    int eq = mod.indexOf('=');
                    if (eq < 0) continue;
                    String k = mod.substring(0, eq).trim().toLowerCase();
                    String v = mod.substring(eq + 1).trim();
                    if (k.equals("dim")) dim = v;
                    else if (k.equals("zone")) zone = v;
                }
                if (dim == null && zone == null) {
                    add(p.substring(0, bar));
                    return;
                }
                IdMatcher base = new IdMatcher();
                base.add(p.substring(0, bar));
                scoped.add(new Scoped(base, dim, zone));
                return;
            }
            if (p.startsWith("#")) {
                tags.add(p.substring(1));
                return;
            }
            if (p.indexOf('*') < 0) {
                exact.add(p);
                return;
            }
            String[] parts = p.split("\\*", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(".*");
                if (!parts[i].isEmpty()) sb.append(Pattern.quote(parts[i]));
            }
            wild.add(Pattern.compile(sb.toString()));
        }

        boolean matches(String id) {
            if (exact.contains(id)) return true;
            for (Pattern p : wild) if (p.matcher(id).matches()) return true;
            for (String t : tags) if (RpEssentialsPatternUtils.inTag(id, t)) return true;
            return false;
        }

        int scopedState(String id, ServerPlayer player) {
            int state = 0;
            String dim = null;
            for (Scoped s : scoped) {
                if (!s.base().matches(id)) continue;
                state = 1;
                if (dim == null) dim = player.level().dimension().location().toString();
                if (s.dim() != null && !s.dim().equals(dim)) continue;
                if (s.zone() != null && !net.rp.rpessentials.gameplay.WorldBorderManager.isInZone(player, s.zone())) continue;
                return 2;
            }
            return state;
        }
    }

    private record Category(IdMatcher blocked, Map<String, IdMatcher> allowed) {}
    private record ContainerRule(IdMatcher block, Set<String> professions) {}
    private record Snapshot(Category[] categories, List<ContainerRule> containers) {}

    private static final class Decisions {
        final Map<ResourceLocation, Boolean> craft = new ConcurrentHashMap<>();
        final Map<ResourceLocation, Boolean> breaking = new ConcurrentHashMap<>();
        final Map<ResourceLocation, Boolean> use = new ConcurrentHashMap<>();
        final Map<ResourceLocation, Boolean> equip = new ConcurrentHashMap<>();
        final Map<ResourceLocation, Boolean> containers = new ConcurrentHashMap<>();

        Map<ResourceLocation, Boolean> of(Kind k) {
            return switch (k) {
                case CRAFT -> craft;
                case BREAK -> breaking;
                case USE -> use;
                case EQUIP -> equip;
            };
        }
    }

    private static volatile Snapshot snapshot = null;
    private static final Map<UUID, Decisions> decisions = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> blockedCooldowns = new ConcurrentHashMap<>();

    private static Category buildCategory(List<? extends String> blocked, List<? extends String> allowed) {
        IdMatcher b = new IdMatcher();
        for (String s : blocked) b.add(s);
        Map<String, IdMatcher> a = new HashMap<>();
        for (String entry : allowed) {
            if (!entry.contains(";")) continue;
            String[] parts = entry.split(";", 2);
            IdMatcher m = a.computeIfAbsent(parts[0].trim().toLowerCase(), k -> new IdMatcher());
            for (String item : parts[1].split(",")) m.add(item);
        }
        return new Category(b, a);
    }

    private static Snapshot buildSnapshot() {
        Category[] cats = {
                buildCategory(ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get(), ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get()),
                buildCategory(ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS.get(), ProfessionConfig.PROFESSION_ALLOWED_BLOCKS.get()),
                buildCategory(ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get(), ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get()),
                buildCategory(ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get(), ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get())
        };
        List<ContainerRule> rules = new ArrayList<>();
        for (String entry : ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS.get()) {
            if (!entry.contains(";")) continue;
            String[] parts = entry.split(";", 2);
            IdMatcher m = new IdMatcher();
            m.add(parts[0]);
            Set<String> profs = new HashSet<>();
            for (String p : parts[1].split(",")) profs.add(p.trim());
            rules.add(new ContainerRule(m, profs));
        }
        return new Snapshot(cats, rules);
    }

    public static void notifyBlocked(ServerPlayer player, String channel, Supplier<String> message) {
        long now = System.currentTimeMillis();
        Map<String, Long> m = blockedCooldowns.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
        Long last = m.get(channel);
        if (last != null && now - last <= CRAFT_MESSAGE_COOLDOWN_MS) return;
        m.put(channel, now);
        player.displayClientMessage(Component.literal(message.get()), true);
    }

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
        decisions.clear();
        ProfessionSyncHelper.clearCache();
        ProfessionRestrictionEventHandler.clearTooltipCache();
        professionDataCache.clear();
        RpEssentialsPatternUtils.clearCache();
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
                    if (id.isEmpty()) {
                        RpEssentials.LOGGER.warn("[ProfessionRestrictions] Empty profession id in entry: '{}'", entry);
                        continue;
                    }
                    professionDataCache.put(id, new ProfessionData(id, parts[1].trim(), parts[2].trim()));
                } else {
                    RpEssentials.LOGGER.warn(
                            "[ProfessionRestrictions] Malformed entry (expected id;name;color): '{}'", entry);
                }
            }
            snapshot = buildSnapshot();
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
    public static boolean isExemptFromProfessionRestrictions(ServerPlayer player) {
        if (RpEssentialsRoleManager.has(player, RpEssentialsRoleManager.Permission.PROFESSION_WHITELIST))
            return true;
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
    // MÉTHODE GÉNÉRIQUE
    // =========================================================================
    private static boolean canPerformAction(ServerPlayer player, ResourceLocation id, Kind kind) {
        if (!ensureInitialized()) return true;
        Snapshot snap = snapshot;
        if (snap == null) return true;
        if (isExemptFromProfessionRestrictions(player)) return true;

        UUID uuid = player.getUUID();
        Map<ResourceLocation, Boolean> map = decisions.computeIfAbsent(uuid, k -> new Decisions()).of(kind);
        Boolean cached = map.get(id);
        if (cached != null) return cached;

        try {
            Category cat = snap.categories()[kind.ordinal()];
            String s = id.toString();
            boolean blocked = cat.blocked().matches(s);
            boolean cacheable = true;
            if (!blocked && cat.blocked().hasScoped()) {
                int st = cat.blocked().scopedState(s, player);
                blocked = st == 2;
                cacheable = st == 0;
            }
            boolean result = true;
            if (blocked) {
                result = false;
                for (String lic : LicenseManager.getLicenses(uuid)) {
                    IdMatcher m = cat.allowed().get(lic.toLowerCase());
                    if (m != null && m.matches(s)) {
                        result = true;
                        break;
                    }
                }
            }
            ProfessionCheckEvent ev = new ProfessionCheckEvent(player, id,
                    ProfessionCheckEvent.Type.valueOf(kind.name()), result);
            NeoForge.EVENT_BUS.post(ev);
            result = ev.isAllowed();
            if (cacheable) map.put(id, result);
            return result;
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[ProfessionRestrictions] Error checking action: {}", e.getMessage());
            return true;
        }
    }

    // =========================================================================
    // API PUBLIQUE — VÉRIFICATIONS
    // =========================================================================

    public static boolean canCraft(ServerPlayer player, ResourceLocation itemId) {
        return canPerformAction(player, itemId, Kind.CRAFT);
    }

    public static boolean canBreakBlock(ServerPlayer player, ResourceLocation blockId) {
        return canPerformAction(player, blockId, Kind.BREAK);
    }

    public static boolean canUseItem(ServerPlayer player, ResourceLocation itemId) {
        return canPerformAction(player, itemId, Kind.USE);
    }

    public static boolean canEquip(ServerPlayer player, ResourceLocation itemId) {
        return canPerformAction(player, itemId, Kind.EQUIP);
    }

    public static boolean isReservedTag(String id) {
        try {
            for (String e : RpEssentialsConfig.ROLES.get()) {
                if (e.split(";", 2)[0].trim().equalsIgnoreCase(id)) return true;
            }
            for (String t : RpEssentialsConfig.STAFF_TAGS.get()) {
                if (t.equalsIgnoreCase(id)) return true;
            }
        } catch (IllegalStateException ignored) {}
        return false;
    }

    /**
     * Vérifie si un joueur peut ouvrir un conteneur (bloc) donné.
     * Format config : block_id;profession1,profession2
     * Exemple       : minecraft:anvil;forgeron
     */
    public static boolean canOpenContainer(ServerPlayer player, ResourceLocation blockId) {
        if (!ensureInitialized()) return true;
        Snapshot snap = snapshot;
        if (snap == null || snap.containers().isEmpty()) return true;
        if (isExemptFromProfessionRestrictions(player)) return true;

        UUID uuid = player.getUUID();
        Map<ResourceLocation, Boolean> map = decisions.computeIfAbsent(uuid, k -> new Decisions()).containers;
        Boolean cached = map.get(blockId);
        if (cached != null) return cached;

        String s = blockId.toString();
        boolean result = true;
        for (ContainerRule r : snap.containers()) {
            if (!r.block().matches(s)) continue;
            result = false;
            for (String lic : LicenseManager.getLicenses(uuid)) {
                if (r.professions().contains(lic)) {
                    result = true;
                    break;
                }
            }
            break;
        }
        map.put(blockId, result);
        return result;
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
        player.displayClientMessage(Component.literal(getCraftBlockedMessage(player, itemId)), true);
    }

    // =========================================================================
    // MESSAGES
    // =========================================================================
    public static String getCraftBlockedMessage(ServerPlayer p, ResourceLocation itemId) {
        try {
            return ServerLang.get(p, ProfessionConfig.MSG_CRAFT_BLOCKED, "item", itemId.toString(),
                    "profession", getRequiredProfessions(p, itemId, ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get()));
        } catch (Exception e) {
            return ServerLang.get(p, MessagesConfig.PROFESSION_CRAFT_BLOCKED_FALLBACK);
        }
    }

    public static String getBlockBreakBlockedMessage(ServerPlayer p, ResourceLocation blockId) {
        try {
            return ServerLang.get(p, ProfessionConfig.MSG_BLOCK_BREAK_BLOCKED, "item", blockId.toString(),
                    "profession", getRequiredProfessions(p, blockId, ProfessionConfig.PROFESSION_ALLOWED_BLOCKS.get()));
        } catch (Exception e) {
            return ServerLang.get(p, MessagesConfig.PROFESSION_BREAK_BLOCKED_FALLBACK);
        }
    }

    public static String getItemUseBlockedMessage(ServerPlayer p, ResourceLocation itemId) {
        try {
            return ServerLang.get(p, ProfessionConfig.MSG_ITEM_USE_BLOCKED, "item", itemId.toString(),
                    "profession", getRequiredProfessions(p, itemId, ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get()));
        } catch (Exception e) {
            return ServerLang.get(p, MessagesConfig.PROFESSION_USE_BLOCKED_FALLBACK);
        }
    }

    public static String getEquipmentBlockedMessage(ServerPlayer p, ResourceLocation itemId) {
        try {
            return ServerLang.get(p, ProfessionConfig.MSG_EQUIPMENT_BLOCKED, "item", itemId.toString(),
                    "profession", getRequiredProfessions(p, itemId, ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get()));
        } catch (Exception e) {
            return ServerLang.get(p, MessagesConfig.PROFESSION_EQUIP_BLOCKED_FALLBACK);
        }
    }

    public static String getContainerOpenBlockedMessage(ServerPlayer p, ResourceLocation blockId) {
        try {
            return ServerLang.get(p, ProfessionConfig.MSG_CONTAINER_OPEN_BLOCKED, "block", blockId.toString(),
                    "profession", getContainerRequiredProfessions(p, blockId));
        } catch (Exception e) {
            return ServerLang.get(p, MessagesConfig.CONTAINER_FALLBACK);
        }
    }

    public static String getCraftBlockedMessage(ResourceLocation id) { return getCraftBlockedMessage(null, id); }

    public static String getBlockBreakBlockedMessage(ResourceLocation id) { return getBlockBreakBlockedMessage(null, id); }

    public static String getItemUseBlockedMessage(ResourceLocation id) { return getItemUseBlockedMessage(null, id); }

    public static String getEquipmentBlockedMessage(ResourceLocation id) { return getEquipmentBlockedMessage(null, id); }

    public static String getContainerOpenBlockedMessage(ResourceLocation id) { return getContainerOpenBlockedMessage(null, id); }

    private static String getContainerRequiredProfessions(ServerPlayer p, ResourceLocation blockId) {
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
                    ? ServerLang.get(p, MessagesConfig.PROFESSION_NONE_AVAILABLE)
                    : String.join("§7, ", result);
        } catch (Exception e) {
            return ServerLang.get(p, MessagesConfig.PROFESSION_NONE_AVAILABLE);
        }
    }

    // =========================================================================
    // DONNÉES DES PROFESSIONS
    // =========================================================================
    public static ProfessionData getProfessionData(String professionId) {
        if (!ensureInitialized()) return null;
        return professionDataCache.get(professionId.toLowerCase());
    }

    public static Collection<ProfessionData> getAllProfessions() {
        if (!ensureInitialized()) return Collections.emptyList();
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
        return RpEssentialsPatternUtils.matchesPattern(resourceId, pattern);
    }

    public static String getRequiredProfessions(ResourceLocation resourceId, List<? extends String> allowedList) {
        return getRequiredProfessions(null, resourceId, allowedList);
    }

    /** Names of the professions allowed for a resource, in the viewer's language for the "none" case. */
    public static String getRequiredProfessions(ServerPlayer p, ResourceLocation resourceId, List<? extends String> allowedList) {
        if (!ensureInitialized()) return ServerLang.get(p, MessagesConfig.PROFESSION_SYSTEM_NOT_INIT);
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
                ? ServerLang.get(p, MessagesConfig.PROFESSION_NONE_AVAILABLE)
                : String.join("§7, ", required);
    }

    public static void enforceEquipment(ServerPlayer player) {
        if (player.isCreative()) return;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (canEquip(player, id)) continue;
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            player.displayClientMessage(Component.literal(getEquipmentBlockedMessage(player, id)), true);
        }
    }

    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================
    public static void invalidatePlayerCache(UUID playerUUID) {
        craftMessageCooldown.remove(playerUUID);
        decisions.remove(playerUUID);
        blockedCooldowns.remove(playerUUID);
    }

    public static void clearCache() {
        professionDataCache.clear();
        RpEssentialsPatternUtils.clearCache();
        craftMessageCooldown.clear();
        blockedCooldowns.clear();
        decisions.clear();
        snapshot = null;
        lastCacheUpdate = 0;
        isInitialized = false;
    }
}