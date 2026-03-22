package net.rp.rpessentials.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public class MixinArmorSlot {

    private static final Map<UUID, Long> lastEquipMessage = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(
            method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void rpessentials$blockEquip(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        // Cast vers l'accessor — fonctionne car Mixin applique l'interface à runtime
        LivingEntity owner = ((ArmorSlotAccessor) (Object) this).getOwner();
        if (!(owner instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.isCreative()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        if (!ProfessionRestrictionManager.canEquip(serverPlayer, itemId)) {
            long now = System.currentTimeMillis();
            UUID uuid = serverPlayer.getUUID();
            Long last = lastEquipMessage.get(uuid);
            if (last == null || now - last > COOLDOWN_MS) {
                lastEquipMessage.put(uuid, now);
                String msg = ProfessionRestrictionManager.getEquipmentBlockedMessage(itemId);
                serverPlayer.displayClientMessage(Component.literal(msg), true);
            }
            cir.setReturnValue(false);
        }
    }
}