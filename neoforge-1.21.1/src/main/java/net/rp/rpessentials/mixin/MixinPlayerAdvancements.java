package net.rp.rpessentials.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.moderation.VanishManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class MixinPlayerAdvancements {

    @Shadow private ServerPlayer player;

    @Inject(method = "award", at = @At("HEAD"), remap = false, require = 0)
    private void rpessentials$beginAward(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        VanishManager.beginAward(player.getUUID());
    }

    @Inject(method = "award", at = @At("RETURN"), remap = false, require = 0)
    private void rpessentials$endAward(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
        VanishManager.endAward();
    }
}