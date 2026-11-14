package net.rp.rpessentials.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.network.SyncNametagDataPacket;
import net.rp.rpessentials.client.ClientNametagConfig;
import net.rp.rpessentials.client.ClientProfessionRestrictions;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class NetworkHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ── Nametag sync ──────────────────────────────────────────────────────
        registrar.playToClient(
                SyncNametagDataPacket.TYPE,
                SyncNametagDataPacket.CODEC,
                new DirectionalPayloadHandler<>(SyncNametagDataPacket::handle, null)
        );

        registrar.playToClient(
                HideNametagsPacket.TYPE,
                HideNametagsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (packet, ctx) -> ctx.enqueueWork(() ->
                                ClientNametagConfig.setHideNametags(packet.hideNametags())),
                        null
                )
        );

        // ── Profession restrictions ───────────────────────────────────────────
        registrar.playToClient(
                SyncProfessionRestrictionsPacket.TYPE,
                SyncProfessionRestrictionsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        NetworkHandler::handleSyncProfessionRestrictions, null
                )
        );

        // ── Admin GUI — Profession editor & Player profile ────────────────────
        registrar.playToServer(
                RequestOpenGuiPacket.TYPE,
                RequestOpenGuiPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, RequestOpenGuiPacket::handleOnServer)
        );

        registrar.playToClient(
                OpenProfessionGuiPacket.TYPE,
                OpenProfessionGuiPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(OpenProfessionGuiPacket::handleOnClient, null)
        );

        registrar.playToClient(
                OpenPlayerProfileGuiPacket.TYPE,
                OpenPlayerProfileGuiPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(OpenPlayerProfileGuiPacket::handleOnClient, null)
        );

        registrar.playToServer(
                RequestPlayerStatsPacket.TYPE,
                RequestPlayerStatsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, RequestPlayerStatsPacket::handleOnServer)
        );

        registrar.playToClient(
                PlayerStatsPacket.TYPE,
                PlayerStatsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(PlayerStatsPacket::handleOnClient, null)
        );

        registrar.playToServer(
                RequestPlayerHistoryPacket.TYPE,
                RequestPlayerHistoryPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, RequestPlayerHistoryPacket::handleOnServer)
        );

        registrar.playToClient(
                PlayerHistoryPacket.TYPE,
                PlayerHistoryPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(PlayerHistoryPacket::handleOnClient, null)
        );

        registrar.playToServer(
                SaveProfessionPacket.TYPE,
                SaveProfessionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, SaveProfessionPacket::handleOnServer)
        );

        registrar.playToServer(
                SetPlayerProfilePacket.TYPE,
                SetPlayerProfilePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, SetPlayerProfilePacket::handleOnServer)
        );

        registrar.playToServer(
                PlayerNoteActionPacket.TYPE,
                PlayerNoteActionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PlayerNoteActionPacket::handleOnServer)
        );

        registrar.playToServer(
                RequestAddPlayerPacket.TYPE,
                RequestAddPlayerPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, RequestAddPlayerPacket::handleOnServer)
        );

        registrar.playToServer(
                DeletePendingProfilePacket.TYPE,
                DeletePendingProfilePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, DeletePendingProfilePacket::handleOnServer)
        );

        // ── Config Manager GUI ────────────────────────────────────────────────
        // S→C: initial file list
        registrar.playToClient(
                ConfigGuiFilesPacket.TYPE,
                ConfigGuiFilesPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(ConfigGuiFilesPacket::handleOnClient, null)
        );

        // C→S: request entries for one file
        registrar.playToServer(
                RequestConfigFilePacket.TYPE,
                RequestConfigFilePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, RequestConfigFilePacket::handleOnServer)
        );

        registrar.playToServer(
                DiceRollPacket.TYPE,
                DiceRollPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, DiceRollPacket::handleOnServer)
        );

        // S→C: entries for one file
        registrar.playToClient(
                ConfigFileEntriesPacket.TYPE,
                ConfigFileEntriesPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(ConfigFileEntriesPacket::handleOnClient, null)
        );

        // C→S: apply changes
        registrar.playToServer(
                SaveConfigEntriesPacket.TYPE,
                SaveConfigEntriesPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, SaveConfigEntriesPacket::handleOnServer)
        );

        registrar.playToClient(
                OpenRolesGuiPacket.TYPE,
                OpenRolesGuiPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(OpenRolesGuiPacket::handleOnClient, null)
        );

        registrar.playToServer(
                SaveRolePacket.TYPE,
                SaveRolePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, SaveRolePacket::handleOnServer)
        );

        registrar.playToServer(
                DeleteProfessionPacket.TYPE,
                DeleteProfessionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, DeleteProfessionPacket::handleOnServer)
        );

        registrar.playToServer(
                DeleteRolePacket.TYPE,
                DeleteRolePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, DeleteRolePacket::handleOnServer)
        );

        registrar.playToClient(
                OpenGlobalRestrictionsPacket.TYPE,
                OpenGlobalRestrictionsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(OpenGlobalRestrictionsPacket::handleOnClient, null)
        );

        registrar.playToServer(
                SaveGlobalRestrictionsPacket.TYPE,
                SaveGlobalRestrictionsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, SaveGlobalRestrictionsPacket::handleOnServer)
        );

        registrar.playToServer(
                ManagePlayerPacket.TYPE,
                ManagePlayerPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, ManagePlayerPacket::handleOnServer)
        );
        registrar.playToClient(
                net.rp.rpessentials.network.SyncNametagBatchPacket.TYPE,
                net.rp.rpessentials.network.SyncNametagBatchPacket.CODEC,
                new DirectionalPayloadHandler<>(net.rp.rpessentials.network.SyncNametagBatchPacket::handle, null)
        );
        registrar.playToClient(
                SyncTooltipPacket.TYPE,
                SyncTooltipPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SyncTooltipPacket::handle, null)
        );

        // ── Reports (profile GUI) ─────────────────────────────────────────────
        registrar.playToServer(
                RequestPlayerReportsPacket.TYPE,
                RequestPlayerReportsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, RequestPlayerReportsPacket::handleOnServer)
        );
        registrar.playToClient(
                PlayerReportsPacket.TYPE,
                PlayerReportsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(PlayerReportsPacket::handleOnClient, null)
        );
        registrar.playToServer(
                ReportActionPacket.TYPE,
                ReportActionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, ReportActionPacket::handleOnServer)
        );
    }

    private static void handleSyncProfessionRestrictions(
            SyncProfessionRestrictionsPacket packet,
            net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientProfessionRestrictions.updateRestrictions(
                    packet.blockedCrafts(), packet.blockedEquipment());
            RpEssentials.LOGGER.info("[Profession] Synced restrictions — {} crafts, {} equipment blocked",
                    packet.blockedCrafts().size(), packet.blockedEquipment().size());
        });
    }
}
