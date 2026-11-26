package net.rp.rpessentials.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//#if forge
//? /** 1.20.1 has no ArmorSlot class: the armor slots are the first anonymous Slot of InventoryMenu. */
//? @Mixin(targets = "net.minecraft.world.inventory.InventoryMenu$1")
//#else
@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
//#endif
public class MixinArmorSlot {

    private static final Map<UUID, Long> lastEquipMessage = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(
            method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            //#if forge
            //? cancellable = true
            //#else
            cancellable = true,
            remap = false
            //#endif
    )
    private void rpessentials$blockEquip(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        //#if forge
        //? // The owner of an armor slot is the player of its inventory (1.20.1 has no ArmorSlot.owner).
        //? if (!(((net.minecraft.world.inventory.Slot) (Object) this).container
                //? instanceof net.minecraft.world.entity.player.Inventory inv)) return;
        //? LivingEntity owner = inv.player;
        //#else
        // Cast vers l'accessor — fonctionne car Mixin applique l'interface à runtime
        LivingEntity owner = ((ArmorSlotAccessor) (Object) this).getOwner();
        //#endif
        if (!(owner instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.isCreative()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        if (!ProfessionRestrictionManager.canEquip(serverPlayer, itemId)) {
            ProfessionRestrictionManager.notifyBlocked(serverPlayer, "equip",
                    () -> ProfessionRestrictionManager.getEquipmentBlockedMessage(serverPlayer, itemId));
            cir.setReturnValue(false);
        }
    }
}