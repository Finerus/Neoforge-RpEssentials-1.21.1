package net.rp.rpessentials.profession;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.BlockItem;
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
import net.rp.rpessentials.util.RpEssentialsPatternUtils;
import net.rp.rpessentials.client.ClientTooltipRestrictions;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ProfessionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestion événementielle des restrictions de métiers.
 *
 * Restrictions couvertes :
 *   Craft             → MixinResultSlot, MixinSmithingMenu, MixinAnvilMenu
 *   Armure drag       → MixinArmorSlot
 *   Armure right-click→ MixinArmorItemUse
 *   Cassage de blocs  → onBlockBreak
 *   Usage item (RC)   → onRightClickItem + onRightClickBlock
 *   Outil (LC)        → onLeftClickBlock
 *   Attaque arme      → onAttackEntity
 *   Ouverture container → MixinServerPlayerGameMode
 *   Placement de bloc → onBlockPlace  ← NOUVEAU
 *   Interaction entité→ onEntityInteract ← NOUVEAU
 *   Tooltips complets → onItemTooltip (fix item/bloc ID mismatch)
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public class ProfessionRestrictionEventHandler {

    private static final Map<UUID, Long> lastMessageTime   = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastAttackWarning = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN       = 3000L;
    private static final long ATTACK_WARNING_COOLDOWN = 2000L;

    // =========================================================================
    // UTILITAIRE ANTI-SPAM
    // =========================================================================
    private static boolean canSendMessage(UUID id, Map<UUID, Long> cache, long cooldown) {
        long now = System.currentTimeMillis();
        Long last = cache.get(id);
        if (last == null || now - last > cooldown) {
            cache.put(id, now);
            return true;
        }
        return false;
    }

    private static void sendBlocked(ServerPlayer player, String message) {
        if (canSendMessage(player.getUUID(), lastMessageTime, MESSAGE_COOLDOWN)) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    // =========================================================================
    // BLOCK BREAK
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());

        if (!ProfessionRestrictionManager.canBreakBlock(player, blockId)) {
            event.setCanceled(true);
            sendBlocked(player, ProfessionRestrictionManager.getBlockBreakBlockedMessage(player, blockId));
        }
    }

    // =========================================================================
    // BLOCK PLACEMENT  (NOUVEAU)
    // =========================================================================

    /**
     * Bloque le placement d'un bloc si l'item utilisé est dans globalBlockedItems.
     * Exemple : empêcher de poser un TNT, un cauldron, etc.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getPlacedBlock().getBlock().asItem());
        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            event.setCanceled(true);
            sendBlocked(player, ProfessionRestrictionManager.getItemUseBlockedMessage(player, itemId));
        }
    }

    // =========================================================================
    // ITEM USE (RIGHT CLICK)
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().isCreative()) return;
        checkItemUse(event.getEntity(), event.getItemStack(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
            sendBlocked(player, ProfessionRestrictionManager.getItemUseBlockedMessage(player, itemId));
        }
    }

    private static void checkItemUse(net.minecraft.world.entity.Entity entity,
                                     ItemStack itemStack,
                                     net.neoforged.bus.api.ICancellableEvent event) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (itemStack.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());

        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            event.setCanceled(true);
            sendBlocked(player, ProfessionRestrictionManager.getItemUseBlockedMessage(player, itemId));
        }
    }

    // =========================================================================
    // LEFT CLICK BLOCK (outil)
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            event.setCanceled(true);
            sendBlocked(player, ProfessionRestrictionManager.getItemUseBlockedMessage(player, itemId));
        }
    }

    // =========================================================================
    // ENTITY INTERACTION
    // =========================================================================
    /**
     * Bloque l'interaction avec des entités si l'item tenu est restreint.
     * Exemples : selle sur cheval, laisse, seau à lait sur vache, etc.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());

        // Vérification usage item
        if (!ProfessionRestrictionManager.canUseItem(player, itemId)) {
            event.setCanceled(true);
            sendBlocked(player, ProfessionRestrictionManager.getItemUseBlockedMessage(player, itemId));
            return;
        }

        // Cas spécial : monter un cheval/entité si l'item principal est une arme bloquée
        Entity target = event.getTarget();
        if (target instanceof AbstractHorse) {
            ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!mainHand.isEmpty()) {
                ResourceLocation mainId = BuiltInRegistries.ITEM.getKey(mainHand.getItem());
                if (!ProfessionRestrictionManager.canEquip(player, mainId)) {
                    // On ne bloque pas le montage lui-même, mais on prévient
                    // (comportement intentionnel : pas trop restrictif)
                }
            }
        }
    }

    // =========================================================================
    // ATTACK
    // =========================================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack weapon = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (weapon.isEmpty()) return;

        ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(weapon.getItem());

        if (!ProfessionRestrictionManager.canEquip(player, weaponId)) {
            event.setCanceled(true);
            if (canSendMessage(player.getUUID(), lastAttackWarning, ATTACK_WARNING_COOLDOWN)) {
                player.displayClientMessage(
                        Component.literal(ProfessionRestrictionManager.getEquipmentBlockedMessage(player, weaponId)), true);
            }
        }
    }

    // =========================================================================
    // TOOLTIP
    // =========================================================================

    /**
     * Affiche les restrictions dans le tooltip des items.
     */
    public static void clearTooltipCache() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) ClientTooltipRestrictions.clearCache();
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        event.getToolTip().addAll(ClientTooltipRestrictions.lines(stack.getItem()));
    }

    @SubscribeEvent
    public static void onEquipmentChange(net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;
        EquipmentSlot slot = event.getSlot();
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;

        ItemStack stack = event.getTo();
        if (stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ProfessionRestrictionManager.canEquip(player, id)) return;

        player.getServer().execute(() -> {
            ItemStack current = player.getItemBySlot(slot);
            if (current.isEmpty() || current.getItem() != stack.getItem()) return;
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(current)) player.drop(current, false);
            player.containerMenu.sendAllDataToRemote();
            sendBlocked(player, ProfessionRestrictionManager.getEquipmentBlockedMessage(player, id));
        });
    }

    // =========================================================================
    // NETTOYAGE DES CACHES
    // =========================================================================
    public static void cleanupCaches() {
        long now = System.currentTimeMillis();
        lastMessageTime.entrySet().removeIf(e -> now - e.getValue() > 10000);
        lastAttackWarning.entrySet().removeIf(e -> now - e.getValue() > 10000);
    }

    public static void onLogout(UUID id) {
        lastMessageTime.remove(id);
        lastAttackWarning.remove(id);
    }
}