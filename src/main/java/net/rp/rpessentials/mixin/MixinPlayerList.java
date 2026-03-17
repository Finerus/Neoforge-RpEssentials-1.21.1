package net.rp.rpessentials.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.config.ChatConfig;
import net.rp.rpessentials.RpEssentials;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    @Unique
    private boolean oneria$isSendingCustomMessage = false;

    /**
     * Intercepte TOUS les messages système pour filtrer join/leave vanilla
     */
    @Inject(
            method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    public void onBroadcastSystemMessage(Component component, boolean bl, CallbackInfo ci) {
        // Si on envoie notre propre message, laisser passer
        if (oneria$isSendingCustomMessage) {
            return;
        }

        String messageText = component.getString();

        try {
            if (ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE == null ||
                    !ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE.get()) {
                return; // Système désactivé, laisser vanilla
            }

            // Détecter message de connexion
            if (messageText.contains("joined the game") ||
                    messageText.contains("a rejoint la partie")) {

                ci.cancel(); // Cancel le message vanilla

                String joinMsg = ChatConfig.JOIN_MESSAGE.get();
                if (!joinMsg.equalsIgnoreCase("none")) {
                    // Extraire le nom du joueur du message vanilla
                    String playerName = extractPlayerName(messageText, true);
                    sendCustomJoinMessage(playerName);
                }

                RpEssentials.LOGGER.debug("[Join] Cancelled vanilla, sent custom for {}",
                        extractPlayerName(messageText, true));
            }
            // Détecter message de déconnexion
            else if (messageText.contains("left the game") ||
                    messageText.contains("a quitté la partie")) {

                ci.cancel(); // Cancel le message vanilla

                String leaveMsg = ChatConfig.LEAVE_MESSAGE.get();
                if (!leaveMsg.equalsIgnoreCase("none")) {
                    // Extraire le nom du joueur du message vanilla
                    String playerName = extractPlayerName(messageText, false);
                    sendCustomLeaveMessage(playerName);
                }

                RpEssentials.LOGGER.debug("[Leave] Cancelled vanilla, sent custom for {}",
                        extractPlayerName(messageText, false));
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[JoinLeave] Error handling message: {}", e.getMessage());
        }
    }

    /**
     * Extrait le nom du joueur depuis le message vanilla
     */
    @Unique
    private String extractPlayerName(String message, boolean isJoin) {
        // Format: "PlayerName joined the game" ou "PlayerName left the game"
        if (isJoin) {
            if (message.contains("joined the game")) {
                return message.replace(" joined the game", "").trim();
            } else if (message.contains("a rejoint la partie")) {
                return message.replace(" a rejoint la partie", "").trim();
            }
        } else {
            if (message.contains("left the game")) {
                return message.replace(" left the game", "").trim();
            } else if (message.contains("a quitté la partie")) {
                return message.replace(" a quitté la partie", "").trim();
            }
        }
        return "Unknown";
    }

    /**
     * Envoie le message de connexion custom
     */
    @Unique
    private void sendCustomJoinMessage(String playerName) {
        try {
            String joinMsg = ChatConfig.JOIN_MESSAGE.get();

            // Trouver le joueur pour obtenir son nickname
            ServerPlayer player = findPlayerByName(playerName);
            String nickname = player != null ? NicknameManager.getDisplayName(player) : playerName;

            String formatted = joinMsg
                    .replace("{player}", playerName)
                    .replace("{nickname}", nickname);

            Component message = ColorHelper.parseColors(formatted);

            // Envoyer le message custom
            oneria$isSendingCustomMessage = true;
            try {
                PlayerList playerList = (PlayerList)(Object)this;
                playerList.broadcastSystemMessage(message, false);
            } finally {
                oneria$isSendingCustomMessage = false;
            }

            RpEssentials.LOGGER.debug("[Join] Sent custom message for {}", playerName);
        } catch (Exception e) {
            oneria$isSendingCustomMessage = false;
            RpEssentials.LOGGER.error("[Join] Error sending custom message", e);
        }
    }

    /**
     * Envoie le message de déconnexion custom
     */
    @Unique
    private void sendCustomLeaveMessage(String playerName) {
        try {
            String leaveMsg = ChatConfig.LEAVE_MESSAGE.get();

            // Trouver le joueur pour obtenir son nickname
            ServerPlayer player = findPlayerByName(playerName);
            String nickname = player != null ? NicknameManager.getDisplayName(player) : playerName;

            String formatted = leaveMsg
                    .replace("{player}", playerName)
                    .replace("{nickname}", nickname);

            Component message = ColorHelper.parseColors(formatted);

            // Envoyer le message custom
            oneria$isSendingCustomMessage = true;
            try {
                PlayerList playerList = (PlayerList)(Object)this;
                playerList.broadcastSystemMessage(message, false);
            } finally {
                oneria$isSendingCustomMessage = false;
            }

            RpEssentials.LOGGER.debug("[Leave] Sent custom message for {}", playerName);
        } catch (Exception e) {
            oneria$isSendingCustomMessage = false;
            RpEssentials.LOGGER.error("[Leave] Error sending custom message", e);
        }
    }

    /**
     * Trouve un joueur par son nom
     */
    @Unique
    private ServerPlayer findPlayerByName(String name) {
        try {
            PlayerList playerList = (PlayerList)(Object)this;
            for (ServerPlayer player : playerList.getPlayers()) {
                if (player.getName().getString().equals(name)) {
                    return player;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}