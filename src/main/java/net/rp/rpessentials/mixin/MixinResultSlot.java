package net.rp.rpessentials.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Couvre TOUS les slots résultats qui héritent de Slot.mayPickup() :
 *  - Table de craft (ResultSlot)
 *  - GrindstoneMenu  (slot anonyme sur ResultContainer)
 *  - StonecutterMenu (slot anonyme sur ResultContainer)
 *  - LoomMenu        (slot anonyme sur ResultContainer/SimpleContainer)
 *  - CartographyTableMenu (slot anonyme sur ResultContainer)
 *
 * SmithingMenu et AnvilMenu sont couverts par MixinItemCombinerMenu.
 *
 * Filtre : on n'intercepte que si le container du slot est un ResultContainer
 * OU si le slot est un ResultSlot — les deux types utilisés par les menus ci-dessus.
 */
@Mixin(Slot.class)
public class MixinResultSlot {

    private static final Map<UUID, Long> lastCraftMessage = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(
            method = "mayPickup(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void rpessentials$blockCraft(Player player, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot)(Object) this;

        // On ne s'intéresse qu'aux slots résultats :
        // - ResultSlot (table de craft vanilla)
        // - Slot anonyme dont le container est un ResultContainer (grindstone, stonecutter, loom, cartography)
        boolean isResultSlot = self instanceof ResultSlot
                || self.container instanceof ResultContainer;

        if (!isResultSlot) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.isCreative()) return;

        ItemStack result = self.getItem();
        if (result.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());

        if (!ProfessionRestrictionManager.canCraft(serverPlayer, itemId)) {
            long now = System.currentTimeMillis();
            UUID uuid = serverPlayer.getUUID();
            Long last = lastCraftMessage.get(uuid);
            if (last == null || now - last > COOLDOWN_MS) {
                lastCraftMessage.put(uuid, now);
                serverPlayer.displayClientMessage(
                        Component.literal(ProfessionRestrictionManager.getCraftBlockedMessage(itemId)),
                        true
                );
            }
            cir.setReturnValue(false);
        }
    }
}