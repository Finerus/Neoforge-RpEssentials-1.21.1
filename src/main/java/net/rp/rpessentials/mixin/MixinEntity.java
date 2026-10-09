package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.rp.rpessentials.identity.NicknameManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Unique
    private static final ThreadLocal<Boolean> rpessentials$saving = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "saveWithoutId",
            at = @At("HEAD"),
            remap = false
    )
    private void rpessentials$beginSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        rpessentials$saving.set(true);
    }

    @Inject(
            method = "saveWithoutId",
            at = @At("RETURN"),
            remap = false
    )
    private void rpessentials$endSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        rpessentials$saving.set(false);
    }

    @Inject(
            method = "getCustomName",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onGetCustomName(CallbackInfoReturnable<Component> cir) {
        if (rpessentials$saving.get()) return;

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