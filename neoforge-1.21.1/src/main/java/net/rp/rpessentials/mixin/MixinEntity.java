package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.VanishManager;
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
            //#if forge
            //? at = @At("HEAD")
            //#else
            at = @At("HEAD"),
            remap = false
            //#endif
    )
    private void rpessentials$beginSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        rpessentials$saving.set(true);
    }

    @Inject(
            method = "saveWithoutId",
            //#if forge
            //? at = @At("RETURN")
            //#else
            at = @At("RETURN"),
            remap = false
            //#endif
    )
    private void rpessentials$endSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        rpessentials$saving.set(false);
    }

    /** Every way to change a tag (/tag, functions, other mods): a role takes effect at once. */
    @Inject(method = {"addTag", "removeTag"}, at = @At("RETURN"), remap = false)
    private void rpessentials$roleTag(String tag, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (Object) this instanceof ServerPlayer p) {
            net.rp.rpessentials.permission.AccessRefresh.onTag(p, tag);
        }
    }

    @Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void rpessentials$vanishTriggers(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer p && VanishManager.isVanished(p.getUUID())
                && VanishManager.hides(VanishManager.Aspect.TRIGGERS)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "getCustomName",
            at = @At("HEAD"),
            //#if forge
            //? cancellable = true
            //#else
            cancellable = true,
            remap = false
            //#endif
    )
    private void onGetCustomName(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof ServerPlayer serverPlayer)) return;
        if (rpessentials$saving.get()) return;

        Component nametagDisplay = NicknameManager.getNametagDisplay(serverPlayer);
        if (nametagDisplay != null) {
            cir.setReturnValue(nametagDisplay);
        }
    }
}