package net.rp.rpessentials.profession;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.rp.rpessentials.RpEssentials;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère UNIQUEMENT les restrictions de craft.
 * L'armure est gérée par ProfessionRestrictionEventHandler (event-based)
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public class CraftingAndArmorRestrictionEventHandler {

    private static final Map<UUID, Long> lastCraftMessage = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN = 2000;

    // Garde uniquement les UUIDs des joueurs qui ont un CraftingMenu ouvert
    private static final Map<UUID, Boolean> hasCraftingOpen = new ConcurrentHashMap<>();

    /**
     * Quand un joueur ouvre un menu de craft, on le note
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getContainer() instanceof CraftingMenu) {
            hasCraftingOpen.put(player.getUUID(), true);
        }
    }

    /**
     * Quand un joueur ferme son menu, on nettoie
     */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        hasCraftingOpen.remove(uuid);
        lastCraftMessage.remove(uuid);
    }

    /**
     * Tick de vérification du craft.
     * Grâce au filtre hasCraftingOpen, on ignore immédiatement tous les joueurs
     * qui n'ont pas de menu de craft ouvert — pas de calcul inutile.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Skip immédiat si pas de craft ouvert ou en créatif
        if (!hasCraftingOpen.containsKey(player.getUUID())) return;
        if (player.isCreative()) return;

        // Toutes les 5 ticks au lieu de 2 — le craft n'est pas instantané
        if (player.tickCount % 5 != 0) return;

        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) return;

        checkAndClearCraftResult(player, craftingMenu);
    }

    private static void checkAndClearCraftResult(ServerPlayer player, CraftingMenu menu) {
        try {
            ItemStack result = menu.getSlot(0).getItem();
            if (result.isEmpty()) return;

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());

            if (!ProfessionRestrictionManager.canCraft(player, itemId)) {
                menu.getSlot(0).set(ItemStack.EMPTY);

                if (canSendMessage(player.getUUID())) {
                    String message = ProfessionRestrictionManager.getCraftBlockedMessage(itemId);
                    player.displayClientMessage(Component.literal(message), true);
                }

                player.containerMenu.broadcastFullState();
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[CraftRestriction] Error checking craft for {}: {}",
                    player.getName().getString(), e.getMessage());
        }
    }

    private static boolean canSendMessage(UUID playerUUID) {
        long now = System.currentTimeMillis();
        Long lastTime = lastCraftMessage.get(playerUUID);
        if (lastTime == null || now - lastTime > MESSAGE_COOLDOWN) {
            lastCraftMessage.put(playerUUID, now);
            return true;
        }
        return false;
    }

    public static void cleanupCaches() {
        long now = System.currentTimeMillis();
        lastCraftMessage.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
    }
}