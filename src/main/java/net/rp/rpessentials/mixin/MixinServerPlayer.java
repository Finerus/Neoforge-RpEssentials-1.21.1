package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.rp.rpessentials.identity.NicknameManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinServerPlayer {

    /**
     * @reason Remplacement du nom d'affichage par le pseudonyme s'il existe.
     *
     * IMPORTANT: Ce mixin cible Player.class qui est commun client/serveur.
     * On garde le guard ServerPlayer pour ne jamais appeler NicknameManager
     * côté client — il lirait le fichier local (solo) au lieu des données
     * reçues du serveur. Le nametag côté client est géré par MixinEntityRenderer
     * + ClientNametagCache (données synchronisées via SyncNametagDataPacket).
     */
    @Inject(
            method = "getDisplayName",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void onGetDisplayName(CallbackInfoReturnable<Component> cir) {
        Player player = (Player) (Object) this;

        // Guard strict : ne s'exécuter que côté serveur.
        // Côté client, Player est une AbstractClientPlayer — laisser passer.
        if (!(player instanceof ServerPlayer)) return;

        if (NicknameManager.hasNickname(player.getUUID())) {
            String nickname = NicknameManager.getNickname(player.getUUID());
            if (nickname != null && !nickname.isEmpty()) {
                cir.setReturnValue(Component.literal(nickname));
            }
        }
    }
}