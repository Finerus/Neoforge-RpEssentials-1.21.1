package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.identity.NicknameManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(
            method = "getCustomName",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onGetCustomName(CallbackInfoReturnable<Component> cir) {
        Entity entity = (Entity) (Object) this;

        // Afficher le nickname uniquement pour les ServerPlayer
        if (entity instanceof ServerPlayer serverPlayer) {
            Component nametagDisplay = NicknameManager.getNametagDisplay(serverPlayer);
            if (nametagDisplay != null) {
                cir.setReturnValue(nametagDisplay);
            }
        }
    }
}