package net.rp.rpessentials.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(AnvilMenu.class)
public abstract class MixinAnvilMenu extends MixinItemCombinerMenuBase {

    private static final Map<UUID, Long> lastAnvilMessage = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(
            method = "mayPickup(Lnet/minecraft/world/entity/player/Player;Z)Z",
            at = @At("HEAD"),
            //#if forge
            //? cancellable = true
            //#else
            cancellable = true,
            remap = false
            //#endif
    )
    private void rpessentials$blockAnvil(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.isCreative()) return;

        ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());

        if (!ProfessionRestrictionManager.canCraft(serverPlayer, itemId)) {
            ProfessionRestrictionManager.notifyBlocked(serverPlayer, "craft",
                    () -> ProfessionRestrictionManager.getCraftBlockedMessage(itemId));
            cir.setReturnValue(false);
        }
    }
}