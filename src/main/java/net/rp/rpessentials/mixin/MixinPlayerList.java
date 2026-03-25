package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import net.rp.rpessentials.config.ChatConfig;
import net.rp.rpessentials.RpEssentials;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    /**
     * Flag interne pour laisser passer nos propres messages custom.
     * Toujours false sauf pendant broadcastCustomMessage().
     */
    @Unique
    private boolean rpessentials$isSendingCustomMessage = false;

    /**
     * Intercepte les messages vanilla join/leave et les annule si le
     * système custom est activé. L'envoi du message custom est délégué
     * à RpEssentialsEventHandler via PlayerLoggedInEvent / PlayerLoggedOutEvent,
     * où l'on dispose directement de l'objet ServerPlayer (Bug 13 fix).
     */
    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    public void onBroadcastSystemMessage(Component component, boolean bl, CallbackInfo ci) {
        // Laisser passer nos propres messages custom (évite la récursion)
        if (rpessentials$isSendingCustomMessage) return;

        try {
            if (ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE == null
                    || !ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE.get()) {
                return; // Système désactivé → comportement vanilla
            }
        } catch (Exception e) {
            return; // Config pas encore chargée → comportement vanilla
        }

        String text = component.getString();

        // Bug 10 fix — on ne parse plus le texte pour extraire le nom.
        // On annule juste le message vanilla ; le custom est envoyé
        // depuis PlayerLoggedInEvent / PlayerLoggedOutEvent.
        boolean isJoin  = text.contains("joined the game")
                || text.contains("a rejoint la partie");
        boolean isLeave = text.contains("left the game")
                || text.contains("a quitté la partie");

        if (isJoin) {
            ci.cancel();
            String joinMsg;
            try { joinMsg = ChatConfig.JOIN_MESSAGE.get(); }
            catch (Exception e) { return; }
            // "none" = désactivé explicitement
            if ("none".equalsIgnoreCase(joinMsg)) return;
            // Le message est envoyé par RpEssentialsEventHandler,
            // ici on se contente d'annuler le vanilla.
            RpEssentials.LOGGER.debug("[JoinLeave] Vanilla join message cancelled.");
        } else if (isLeave) {
            ci.cancel();
            String leaveMsg;
            try { leaveMsg = ChatConfig.LEAVE_MESSAGE.get(); }
            catch (Exception e) { return; }
            if ("none".equalsIgnoreCase(leaveMsg)) return;
            RpEssentials.LOGGER.debug("[JoinLeave] Vanilla leave message cancelled.");
        }
    }

    /**
     * Méthode utilitaire appelée depuis RpEssentialsEventHandler pour
     * broadcaster un message custom sans déclencher notre propre filtre.
     */
    @Unique
    public void rpessentials$broadcastCustomMessage(Component message) {
        rpessentials$isSendingCustomMessage = true;
        try {
            ((PlayerList)(Object)this).broadcastSystemMessage(message, false);
        } finally {
            rpessentials$isSendingCustomMessage = false;
        }
    }
}