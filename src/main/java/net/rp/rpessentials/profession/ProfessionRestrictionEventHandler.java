package net.rp.rpessentials.profession;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.ProfessionConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire d'événements pour les restrictions de métiers.
 *
 * Craft    → MixinResultSlot      (mayPickup)
 * Armure   → MixinArmorSlot       (mayPlace) + MixinArmorItemUse (use)
 * Le reste → events NeoForge ci-dessous
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public class ProfessionRestrictionEventHandler {

    // Anti-spam messages (cooldown 3 secondes)
    private static final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN = 3000L;

    // Anti-spam attaques (cooldown 2 secondes)
    private static final Map<UUID, Long> lastAttackWarning = new ConcurrentHashMap<>();
    private static final long ATTACK_WARNING_COOLDOWN = 2000L;

    // =========================================================================
    // UTILITAIRE ANTI-SPAM
    // =========================================================================

    private static boolean canSendMessage(UUID playerId, Map<UUID, Long> cache, long cooldown) {
        long now = System.currentTimeMillis();
        Long lastTime = cache.get(playerId);
        if (lastTime == null || now - lastTime > cooldown) {
            cache.put(playerId, now);
            return true;
        }
        return false;
    }

    // =========================================================================
    // BLOCK BREAK RESTRICTIONS
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        Block block = event.getState().getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);

        if (!ProfessionRestrictionManager.canBreakBlock(player, blockId)) {
            event.setCanceled(true);
            if (canSendMessage(player.getUUID(), lastMessageTime, MESSAGE_COOLDOWN)) {
                player.displayClientMessage(
                        Component.literal(ProfessionRestrictionManager.getBlockBreakBlockedMessage(blockId)),
                        true
                );
            }
            RpEssentials.LOGGER.debug("[ProfessionRestrictions] Blocked block break for {}: {}",
                    player.getName().getString(), blockId);
        }
    }

    // =========================================================================
    // ITEM USE RESTRICTIONS (clic droit item + clic droit sur bloc)
    // Les deux passent par le même check — helper factorisé.
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().isCreative()) return;
        checkItemUse(event.getEntity(), event.getItemStack(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isCreative()) return;
        checkItemUse(event.getEntity(), event.getItemStack(), event);
    }

    private static void checkItemUse(net.minecraft.world.entity.Entity entity,
                                     ItemStack itemStack,
                                     net.neoforged.bus.api.ICancellableEvent event) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (itemStack.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());

        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            event.setCanceled(true);
            if (canSendMessage(player.getUUID(), lastMessageTime, MESSAGE_COOLDOWN)) {
                player.displayClientMessage(
                        Component.literal(ProfessionRestrictionManager.getItemUseBlockedMessage(itemId)),
                        true
                );
            }
        }
    }

    // =========================================================================
    // LEFT-CLICK BLOCK (outil non autorisé)
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack itemStack = player.getItemInHand(event.getHand());
        if (itemStack.isEmpty()) return;

        Item item = itemStack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            event.setCanceled(true);
            if (canSendMessage(player.getUUID(), lastMessageTime, MESSAGE_COOLDOWN)) {
                player.displayClientMessage(
                        Component.literal(ProfessionRestrictionManager.getItemUseBlockedMessage(itemId)),
                        true
                );
            }
        }
    }

    // =========================================================================
    // WEAPON/TOOL ATTACK RESTRICTIONS
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack weaponStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (weaponStack.isEmpty()) return;

        Item weapon = weaponStack.getItem();
        ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(weapon);

        if (!ProfessionRestrictionManager.canEquip(player, weaponId)) {
            event.setCanceled(true);
            if (canSendMessage(player.getUUID(), lastAttackWarning, ATTACK_WARNING_COOLDOWN)) {
                player.displayClientMessage(
                        Component.literal(ProfessionRestrictionManager.getEquipmentBlockedMessage(weaponId)),
                        true
                );
            }
            RpEssentials.LOGGER.debug("[ProfessionRestrictions] Blocked weapon attack for {}: {}",
                    player.getName().getString(), weaponId);
        }
    }

    // =========================================================================
    // TOOLTIP INFORMATION
    // =========================================================================

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack.isEmpty()) return;

        Item item = itemStack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        boolean craftRestricted = ProfessionRestrictionManager.isGloballyBlocked(
                itemId, ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get());
        boolean useRestricted = ProfessionRestrictionManager.isGloballyBlocked(
                itemId, ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get());
        boolean equipRestricted = ProfessionRestrictionManager.isGloballyBlocked(
                itemId, ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get());

        if (craftRestricted) {
            String professions = ProfessionRestrictionManager.getRequiredProfessions(
                    itemId, ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get());
            event.getToolTip().add(Component.literal("§7Craft: §c✘ §7Métier: " + professions));
        }
        if (useRestricted) {
            String professions = ProfessionRestrictionManager.getRequiredProfessions(
                    itemId, ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get());
            event.getToolTip().add(Component.literal("§7Utilisation: §c✘ §7Métier: " + professions));
        }
        if (equipRestricted) {
            String professions = ProfessionRestrictionManager.getRequiredProfessions(
                    itemId, ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get());
            event.getToolTip().add(Component.literal("§7Équipement: §c✘ §7Métier: " + professions));
        }
    }

    // =========================================================================
    // NETTOYAGE DES CACHES (appelé depuis RpEssentials.onServerTick toutes les 400 ticks)
    // =========================================================================

    public static void cleanupCaches() {
        long now = System.currentTimeMillis();
        lastMessageTime.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
        lastAttackWarning.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
    }
}