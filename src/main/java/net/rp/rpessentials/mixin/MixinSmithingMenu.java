package net.rp.rpessentials.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(SmithingMenu.class)
public abstract class MixinSmithingMenu extends MixinItemCombinerMenuBase {

    private static final Map<UUID, Long> lastSmithingMessage = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(
            method = "mayPickup(Lnet/minecraft/world/entity/player/Player;Z)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void rpessentials$blockSmithing(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.isCreative()) return;

        ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());

        if (!ProfessionRestrictionManager.canCraft(serverPlayer, itemId)) {
            long now = System.currentTimeMillis();
            UUID uuid = serverPlayer.getUUID();
            Long last = lastSmithingMessage.get(uuid);
            if (last == null || now - last > COOLDOWN_MS) {
                lastSmithingMessage.put(uuid, now);
                serverPlayer.displayClientMessage(
                        Component.literal(ProfessionRestrictionManager.getCraftBlockedMessage(itemId)), true);
            }
            cir.setReturnValue(false);
        }
    }
}