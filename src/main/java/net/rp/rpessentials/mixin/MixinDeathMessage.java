package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.rp.rpessentials.moderation.DeathRPManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepte l'appel à {@code PlayerList.broadcastSystemMessage()} dans
 * {@code ServerPlayer.die()} pour supprimer le message de mort vanilla
 * et le remplacer par le message RP configuré.
 * <p>
 * Fonctionne en solo et en multijoueur car {@code ServerPlayer.die()} est
 * toujours exécuté côté serveur (intégré ou dédié).
 */
@Mixin(ServerPlayer.class)
public abstract class MixinDeathMessage {

    /**
     * Redirige l'unique appel à {@code broadcastSystemMessage} dans {@code die()}.
     * <ul>
     *   <li>Si le joueur est soumis à la mort RP : supprime le message vanilla
     *       et délègue à {@link DeathRPManager#onPlayerDeathRP}.</li>
     *   <li>Sinon : laisse passer le message vanilla normalement.</li>
     * </ul>
     */
    @Redirect(
        method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
        ),
        remap = false
    )
    private void onDeathMessageBroadcast(PlayerList playerList, Component message, boolean overlay) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (DeathRPManager.isDeathRPEnabled(self.getUUID())) {
            // Supprime le message vanilla et gère le comportement RP complet
            DeathRPManager.onPlayerDeathRP(self);
        } else {
            // Comportement vanilla inchangé
            playerList.broadcastSystemMessage(message, overlay);
        }
    }
}
