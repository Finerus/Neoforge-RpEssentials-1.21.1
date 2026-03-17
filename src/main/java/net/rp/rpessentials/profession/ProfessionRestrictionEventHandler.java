package net.rp.rpessentials.profession;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
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
 * Gestionnaire d'événements pour les restrictions de métiers
 * Version refactorisée sans spam et avec action bar
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public class ProfessionRestrictionEventHandler {

    // Cache pour éviter le spam de messages (cooldown de 3 secondes)
    private static final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN = 3000; // 3 secondes

    // Cache pour l'action bar d'équipement (cooldown de 1 seconde)
    private static final Map<UUID, Long> lastEquipmentWarning = new ConcurrentHashMap<>();
    private static final long EQUIPMENT_WARNING_COOLDOWN = 1000; // 1 seconde

    // Cache pour les attaques (cooldown de 2 secondes)
    private static final Map<UUID, Long> lastAttackWarning = new ConcurrentHashMap<>();
    private static final long ATTACK_WARNING_COOLDOWN = 2000; // 2 secondes

    /**
     * Helper pour vérifier si on peut envoyer un message (anti-spam)
     */
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
    // CRAFT RESTRICTIONS - Géré par MixinResultSlot
    // =========================================================================
    // Le craft est maintenant géré par le Mixin pour éviter la consommation de ressources

    // =========================================================================
    // BLOCK BREAK RESTRICTIONS
    // =========================================================================

    /**
     * Bloque la casse de blocs non autorisés
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        Block block = event.getState().getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);

        // Vérifier si le joueur peut casser ce bloc
        if (!ProfessionRestrictionManager.canBreakBlock(player, blockId)) {
            // Annuler la casse
            event.setCanceled(true);

            // Envoyer le message dans l'action bar (anti-spam 3s)
            if (canSendMessage(player.getUUID(), lastMessageTime, MESSAGE_COOLDOWN)) {
                String message = ProfessionRestrictionManager.getBlockBreakBlockedMessage(blockId);
                player.displayClientMessage(
                        Component.literal(message),
                        true // Action bar
                );
            }

            RpEssentials.LOGGER.debug("[ProfessionRestrictions] Blocked block break for {}: {}",
                    player.getName().getString(), blockId);
        }
    }

    // =========================================================================
    // ITEM USE RESTRICTIONS (Clic droit)
    // =========================================================================

    /**
     * Bloque l'utilisation d'items (clic droit)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack itemStack = event.getItemStack();
        if (itemStack.isEmpty()) return;

        Item item = itemStack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        // Vérifier si le joueur peut utiliser cet item
        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            // Annuler l'utilisation
            event.setCanceled(true);

            // Envoyer le message dans l'action bar (anti-spam 3s)
            if (canSendMessage(player.getUUID(), lastMessageTime, MESSAGE_COOLDOWN)) {
                String message = ProfessionRestrictionManager.getItemUseBlockedMessage(itemId);
                player.displayClientMessage(
                        Component.literal(message),
                        true // Action bar
                );
            }

            RpEssentials.LOGGER.debug("[ProfessionRestrictions] Blocked item use for {}: {}",
                    player.getName().getString(), itemId);
        }
    }

    /**
     * Bloque l'utilisation d'items sur des blocs (clic droit sur bloc)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack itemStack = event.getItemStack();
        if (itemStack.isEmpty()) return;

        Item item = itemStack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        // Vérifier si le joueur peut utiliser cet item
        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            // Annuler l'utilisation
            event.setCanceled(true);

            // Envoyer le message dans l'action bar (anti-spam 3s)
            if (canSendMessage(player.getUUID(), lastMessageTime, MESSAGE_COOLDOWN)) {
                String message = ProfessionRestrictionManager.getItemUseBlockedMessage(itemId);
                player.displayClientMessage(
                        Component.literal(message),
                        true // Action bar
                );
            }

            RpEssentials.LOGGER.debug("[ProfessionRestrictions] Blocked item use on block for {}: {}",
                    player.getName().getString(), itemId);
        }
    }

    /**
     * NOUVEAU: Bloque l'interaction gauche (minage avec mauvais outil)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack itemStack = player.getItemInHand(event.getHand());
        if (itemStack.isEmpty()) return;

        Item item = itemStack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        // Vérifier si c'est un outil et si le joueur peut l'utiliser
        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            // Annuler l'action
            event.setCanceled(true);

            // Envoyer le message dans l'action bar (anti-spam 3s)
            if (canSendMessage(player.getUUID(), lastMessageTime, MESSAGE_COOLDOWN)) {
                String message = ProfessionRestrictionManager.getItemUseBlockedMessage(itemId);
                player.displayClientMessage(
                        Component.literal(message),
                        true // Action bar
                );
            }
        }
    }

    // =========================================================================
    // EQUIPMENT RESTRICTIONS (Armures uniquement)
    // =========================================================================

    /**
     * Bloque l'équipement d'armures non autorisées
     * Message dans l'action bar uniquement (pas de spam chat)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack newItem = event.getTo();
        if (newItem.isEmpty()) return; // Retrait d'équipement, toujours autorisé

        EquipmentSlot slot = event.getSlot();

        // Vérifier uniquement pour les slots d'ARMURE (pas main/offhand)
        if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            Item item = newItem.getItem();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

            // Vérifier si le joueur peut équiper cette armure
            if (!ProfessionRestrictionManager.canEquip(player, itemId)) {
                // Retirer l'armure du slot (on ne peut pas cancel l'événement directement)
                // On doit le faire via un schedule pour éviter les conflits
                player.getServer().execute(() -> {
                    player.setItemSlot(slot, ItemStack.EMPTY);

                    // Rendre l'item au joueur dans son inventaire
                    if (!player.getInventory().add(newItem)) {
                        player.drop(newItem, false);
                    }
                });

                // Message dans l'action bar (anti-spam 1s)
                if (canSendMessage(player.getUUID(), lastEquipmentWarning, EQUIPMENT_WARNING_COOLDOWN)) {
                    String message = ProfessionRestrictionManager.getEquipmentBlockedMessage(itemId);
                    player.displayClientMessage(
                            Component.literal(message),
                            true // Action bar
                    );
                }

                RpEssentials.LOGGER.debug("[ProfessionRestrictions] Blocked armor equipment for {}: {}",
                        player.getName().getString(), itemId);
            }
        }
    }

    // =========================================================================
    // WEAPON/TOOL DAMAGE RESTRICTIONS (Main hand)
    // =========================================================================

    /**
     * NOUVEAU: Annule les dégâts des armes non autorisées
     * Message uniquement quand le joueur essaie de frapper
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack weaponStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (weaponStack.isEmpty()) return;

        Item weapon = weaponStack.getItem();
        ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(weapon);

        // Vérifier si le joueur peut utiliser cette arme/outil
        if (!ProfessionRestrictionManager.canEquip(player, weaponId)) {
            // Annuler l'attaque
            event.setCanceled(true);

            // Message dans l'action bar (anti-spam 2s)
            if (canSendMessage(player.getUUID(), lastAttackWarning, ATTACK_WARNING_COOLDOWN)) {
                String message = ProfessionRestrictionManager.getEquipmentBlockedMessage(weaponId);
                player.displayClientMessage(
                        Component.literal(message),
                        true // Action bar
                );
            }

            RpEssentials.LOGGER.debug("[ProfessionRestrictions] Blocked weapon attack for {}: {}",
                    player.getName().getString(), weaponId);
        }
    }

    // =========================================================================
    // TOOLTIP INFORMATION
    // =========================================================================

    /**
     * Ajoute des informations dans le tooltip des items restreints
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack.isEmpty()) return;

        Item item = itemStack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        // Vérifier si l'item est restreint
        boolean craftRestricted = ProfessionRestrictionManager.isGloballyBlocked(
                itemId, ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get());
        boolean useRestricted = ProfessionRestrictionManager.isGloballyBlocked(
                itemId, ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get());
        boolean equipRestricted = ProfessionRestrictionManager.isGloballyBlocked(
                itemId, ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get());

        // Ajouter les informations au tooltip
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

    /**
     * Nettoie les caches périodiquement (appelé par le tick du serveur)
     */
    public static void cleanupCaches() {
        long now = System.currentTimeMillis();

        // Nettoyer les entrées expirées (plus de 10 secondes)
        lastMessageTime.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
        lastEquipmentWarning.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
        lastAttackWarning.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
    }
}