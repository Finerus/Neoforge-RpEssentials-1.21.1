package net.rp.rpessentials.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.SyncNametagDataPacket;
import net.rp.rpessentials.client.ClientNametagConfig;
import net.rp.rpessentials.client.ClientProfessionRestrictions;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class NetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ── Packet legacy : cacher/afficher tous les nametags (compatibilité) ──────
        registrar.playToClient(
                HideNametagsPacket.TYPE,
                HideNametagsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        NetworkHandler::handleHideNametags,
                        null
                )
        );

        // ── Sync complète config + données joueurs ────────────────────────────────
        registrar.playToClient(
                NametagSyncPacket.TYPE,
                NametagSyncPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        NetworkHandler::handleNametagSync,
                        null
                )
        );

        // ── Restrictions métiers ──────────────────────────────────────────────────
        registrar.playToClient(
                SyncProfessionRestrictionsPacket.TYPE,
                SyncProfessionRestrictionsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        NetworkHandler::handleSyncProfessionRestrictions,
                        null
                )
        );

        // ── Sync nametag data par joueur ─────────────────────────────────────────
        registrar.playToClient(
                SyncNametagDataPacket.TYPE,
                SyncNametagDataPacket.CODEC,
                new DirectionalPayloadHandler<>(
                        SyncNametagDataPacket::handle,
                        null
                )
        );

        // =====================================================================
        // GUI ADMIN — 4 nouveaux packets
        // =====================================================================

        // Client → Serveur : demande d'ouverture d'un GUI
        registrar.playToServer(
                RequestOpenGuiPacket.TYPE,
                RequestOpenGuiPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        null,
                        RequestOpenGuiPacket::handleOnServer
                )
        );

        // Serveur → Client : données GUI profession
        registrar.playToClient(
                OpenProfessionGuiPacket.TYPE,
                OpenProfessionGuiPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        OpenProfessionGuiPacket::handleOnClient,
                        null
                )
        );

        // Serveur → Client : données GUI profil joueur
        registrar.playToClient(
                OpenPlayerProfileGuiPacket.TYPE,
                OpenPlayerProfileGuiPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        OpenPlayerProfileGuiPacket::handleOnClient,
                        null
                )
        );

        // Client → Serveur : sauvegarder une profession
        registrar.playToServer(
                SaveProfessionPacket.TYPE,
                SaveProfessionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        null,
                        SaveProfessionPacket::handleOnServer
                )
        );

        // Client → Serveur : appliquer un profil RP
        registrar.playToServer(
                SetPlayerProfilePacket.TYPE,
                SetPlayerProfilePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        null,
                        SetPlayerProfilePacket::handleOnServer
                )
        );
    }

    // ── Handlers existants ────────────────────────────────────────────────────

    private static void handleHideNametags(HideNametagsPacket packet,
                                           net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientNametagConfig.setHideNametags(packet.hideNametags());
            RpEssentials.LOGGER.info("[Nametag] Legacy config received — hide: {}", packet.hideNametags());
        });
    }

    private static void handleNametagSync(NametagSyncPacket packet,
                                          net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientNametagConfig.applySync(packet);
            RpEssentials.LOGGER.debug("[Nametag] Sync received — advanced: {}, players: {}",
                    packet.advancedEnabled(), packet.players().size());
        });
    }

    private static void handleSyncProfessionRestrictions(SyncProfessionRestrictionsPacket packet,
                                                          net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientProfessionRestrictions.updateRestrictions(packet.blockedCrafts(), packet.blockedEquipment());
            RpEssentials.LOGGER.info("[Profession] Synced restrictions — {} crafts, {} equipment blocked",
                    packet.blockedCrafts().size(), packet.blockedEquipment().size());
        });
    }
}
