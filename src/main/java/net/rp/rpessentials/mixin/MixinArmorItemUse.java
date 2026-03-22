package net.rp.rpessentials.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ArmorItem.class)
public class MixinArmorItemUse {

    private static final Map<UUID, Long> lastEquipMessage = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(
            method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void rpessentials$blockArmorUse(Level level, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (player.isCreative()) return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemIdStr = itemId.toString();

        // Côté client — utilise le cache synchronisé depuis le serveur
        if (level.isClientSide()) {
            if (net.rp.rpessentials.client.ClientProfessionRestrictions.isEquipmentBlocked(itemIdStr)) {
                cir.setReturnValue(InteractionResultHolder.fail(stack));
            }
            return;
        }

        // Côté serveur
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (!ProfessionRestrictionManager.canEquip(serverPlayer, itemId)) {
            long now = System.currentTimeMillis();
            UUID uuid = serverPlayer.getUUID();
            Long last = lastEquipMessage.get(uuid);
            if (last == null || now - last > COOLDOWN_MS) {
                lastEquipMessage.put(uuid, now);
                String msg = ProfessionRestrictionManager.getEquipmentBlockedMessage(itemId);
                serverPlayer.displayClientMessage(Component.literal(msg), true);
            }
            cir.setReturnValue(InteractionResultHolder.fail(stack));
        }
    }
}