package net.rp.rpessentials.network;

//#if forge
//? import net.minecraft.resources.ResourceLocation;
//? import net.rp.rpessentials.network.compat.DirectionalPayloadHandler;
//? import net.rp.rpessentials.network.compat.PayloadRegistrar;
//#else
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
//#endif
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.network.SyncNametagDataPacket;
import net.rp.rpessentials.client.ClientNametagConfig;
import net.rp.rpessentials.client.ClientProfessionRestrictions;

//#if forge
//#else
@EventBusSubscriber(modid = RpEssentials.MODID)
//#endif
public class NetworkHandler {

    //#if forge
    //? /** Called from the mod constructor: Forge 1.20.1 channels must exist before the game connects. */
    //? public static void register() {
        //? PayloadRegistrar registrar = PayloadRegistrar.create(new ResourceLocation(RpEssentials.MODID, "main"), "1");
    //#else
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
    //#endif

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

        // ── Systems turned on / off on the server ────────────────────────────
        registrar.playToClient(
                SyncFeaturesPacket.TYPE,
                SyncFeaturesPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (packet, ctx) -> ctx.enqueueWork(() ->
                                net.rp.rpessentials.client.ClientFeatures.set(packet.professionsEnabled())),
                        null
                )
        );

        // ── Permission nodes of the local player (GUI buttons) ───────────────
        registrar.playToClient(
                SyncPermissionsPacket.TYPE,
                SyncPermissionsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (packet, ctx) -> ctx.enqueueWork(() -> {
                            net.rp.rpessentials.client.ClientPerms.set(packet.granted());
                            ClientGuiOpener.closeRefused();
                        }),
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
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(RequestOpenGuiPacket::handleOnServer))
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
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(RequestPlayerStatsPacket::handleOnServer))
        );

        registrar.playToClient(
                PlayerStatsPacket.TYPE,
                PlayerStatsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(PlayerStatsPacket::handleOnClient, null)
        );

        registrar.playToServer(
                RequestPlayerHistoryPacket.TYPE,
                RequestPlayerHistoryPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(RequestPlayerHistoryPacket::handleOnServer))
        );

        registrar.playToClient(
                PlayerHistoryPacket.TYPE,
                PlayerHistoryPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(PlayerHistoryPacket::handleOnClient, null)
        );

        registrar.playToServer(
                SaveProfessionPacket.TYPE,
                SaveProfessionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(SaveProfessionPacket::handleOnServer))
        );

        registrar.playToServer(
                SetPlayerProfilePacket.TYPE,
                SetPlayerProfilePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(SetPlayerProfilePacket::handleOnServer))
        );

        registrar.playToServer(
                PlayerNoteActionPacket.TYPE,
                PlayerNoteActionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(PlayerNoteActionPacket::handleOnServer))
        );

        registrar.playToServer(
                RequestAddPlayerPacket.TYPE,
                RequestAddPlayerPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(RequestAddPlayerPacket::handleOnServer))
        );

        registrar.playToServer(
                DeletePendingProfilePacket.TYPE,
                DeletePendingProfilePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(DeletePendingProfilePacket::handleOnServer))
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
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(RequestConfigFilePacket::handleOnServer))
        );

        registrar.playToServer(
                DiceRollPacket.TYPE,
                DiceRollPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(DiceRollPacket::handleOnServer))
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
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(SaveConfigEntriesPacket::handleOnServer))
        );

        registrar.playToClient(
                OpenRolesGuiPacket.TYPE,
                OpenRolesGuiPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(OpenRolesGuiPacket::handleOnClient, null)
        );

        registrar.playToServer(
                SaveRolePacket.TYPE,
                SaveRolePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(SaveRolePacket::handleOnServer))
        );

        registrar.playToServer(
                DeleteProfessionPacket.TYPE,
                DeleteProfessionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(DeleteProfessionPacket::handleOnServer))
        );

        registrar.playToServer(
                DeleteRolePacket.TYPE,
                DeleteRolePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(DeleteRolePacket::handleOnServer))
        );

        registrar.playToClient(
                OpenGlobalRestrictionsPacket.TYPE,
                OpenGlobalRestrictionsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(OpenGlobalRestrictionsPacket::handleOnClient, null)
        );

        registrar.playToServer(
                SaveGlobalRestrictionsPacket.TYPE,
                SaveGlobalRestrictionsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(SaveGlobalRestrictionsPacket::handleOnServer))
        );

        registrar.playToServer(
                ManagePlayerPacket.TYPE,
                ManagePlayerPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(ManagePlayerPacket::handleOnServer))
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

        // ── Notes (profile GUI): answer to PlayerNoteActionPacket ─────────────
        registrar.playToClient(
                PlayerNotesPacket.TYPE,
                PlayerNotesPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(PlayerNotesPacket::handleOnClient, null)
        );

        // ── Reports (profile GUI) ─────────────────────────────────────────────
        registrar.playToServer(
                RequestPlayerReportsPacket.TYPE,
                RequestPlayerReportsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(RequestPlayerReportsPacket::handleOnServer))
        );
        registrar.playToClient(
                PlayerReportsPacket.TYPE,
                PlayerReportsPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(PlayerReportsPacket::handleOnClient, null)
        );
        registrar.playToServer(
                ReportActionPacket.TYPE,
                ReportActionPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(ReportActionPacket::handleOnServer))
        );

        // ── Character sheets (profile GUI) ────────────────────────────────────
        registrar.playToServer(
                RequestCharacterSheetPacket.TYPE,
                RequestCharacterSheetPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(RequestCharacterSheetPacket::handleOnServer))
        );
        registrar.playToClient(
                CharacterSheetPacket.TYPE,
                CharacterSheetPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(CharacterSheetPacket::handleOnClient, null)
        );
        registrar.playToServer(
                SaveCharacterSheetPacket.TYPE,
                SaveCharacterSheetPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(null, PacketGuard.limit(SaveCharacterSheetPacket::handleOnServer))
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
