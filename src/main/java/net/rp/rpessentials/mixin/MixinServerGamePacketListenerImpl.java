package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.rp.rpessentials.identity.RpEssentialsChatFormatter;
import net.rp.rpessentials.config.ChatConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @Shadow
    public ServerPlayer player;

    /**
     * Intercepte l'envoi des messages de chat pour appliquer le formatage personnalisé
     */
    @Inject(
            method = "broadcastChatMessage",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onBroadcastChatMessage(PlayerChatMessage chatMessage, CallbackInfo ci) {
        // Si le système de chat custom est désactivé, laisser vanilla gérer
        if (!ChatConfig.ENABLE_CHAT_FORMAT.get()) {
            return;
        }

        // Récupérer le contenu du message
        String message = chatMessage.signedContent();

        // ✅ NOUVEAU : Ignorer les messages qui commencent par / (commandes)
        if (message.startsWith("/")) {
            return; // Laisser vanilla gérer les commandes
        }

        // Annuler l'envoi vanilla
        ci.cancel();

        // Formater le message avec notre système
        Component formattedMessage = RpEssentialsChatFormatter.formatChatMessage(player, message);

        // Envoyer à tous les joueurs
        player.getServer().getPlayerList().broadcastSystemMessage(formattedMessage, false);
    }
}