package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.rp.rpessentials.identity.NicknameManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinServerPlayer {

    /**
     * @author Oneria
     * @reason Remplacement du nom d'affichage par le pseudonyme s'il existe.
     * Note: On utilise remap = true mais on peut utiliser un alias si l'AP échoue.
     */
    @Inject(
            method = "getDisplayName",
            at = @At("RETURN"),
            cancellable = true,
            remap = false  // Add this line
    )
    private void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        Player player = (Player) (Object) this;

        if (NicknameManager.hasNickname(player.getUUID())) {
            String nickname = NicknameManager.getNickname(player.getUUID());
            if (nickname != null && !nickname.isEmpty()) {
                cir.setReturnValue(Component.literal(nickname));
            }
        }
    }
}
