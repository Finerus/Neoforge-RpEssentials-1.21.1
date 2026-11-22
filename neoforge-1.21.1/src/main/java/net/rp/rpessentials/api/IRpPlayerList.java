package net.rp.rpessentials.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Interface exposée par MixinPlayerList via @Implements.
 * Permet d'appeler les méthodes @Unique de MixinPlayerList depuis
 * RpEssentialsEventHandler sans cast direct sur MixinPlayerList
 * (qui est une classe de mixin, non instanciable normalement).
 *
 * Usage :
 *   PlayerList pl = server.getPlayerList();
 *   if (pl instanceof IRpPlayerList rpl) {
 *       rpl.rpe$sendCustomJoinLeaveMessage(player, true);
 *   }
 */
public interface IRpPlayerList {

    /**
     * Envoie le message join ou leave personnalisé en résolvant toutes
     * les variables ({player}, {nickname}, {nick}, {real}, {nick_real}).
     * @param player  le joueur concerné
     * @param isJoin  true = message join, false = message leave
     */
    void sendCustomJoinLeaveMessage(ServerPlayer player, boolean isJoin);

    /**
     * Envoie un Component brut sans déclencher le filtre vanilla.
     */
    void broadcastCustomMessage(Component message);
}
