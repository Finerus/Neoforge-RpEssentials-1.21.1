package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.profession.PendingProfileManager;

import java.util.UUID;

/**
 * Packet CLIENT -> SERVEUR
 * Supprime un profil préparé à l'avance (joueur jamais connecté).
 */
public record DeletePendingProfilePacket(UUID targetUuid) implements CustomPacketPayload {

    public static final Type<DeletePendingProfilePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "delete_pending_profile"));

    public static final StreamCodec<FriendlyByteBuf, DeletePendingProfilePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public DeletePendingProfilePacket decode(FriendlyByteBuf buf) {
                    return new DeletePendingProfilePacket(buf.readUUID());
                }
                @Override
                public void encode(FriendlyByteBuf buf, DeletePendingProfilePacket packet) {
                    buf.writeUUID(packet.targetUuid());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(DeletePendingProfilePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer admin)) return;
            if (!RpEssentialsPermissions.isStaff(admin)) return;

            if (!PendingProfileManager.hasPending(packet.targetUuid())) return;
            String name = PendingProfileManager.getMcName(packet.targetUuid());
            PendingProfileManager.consumePending(packet.targetUuid());

            admin.sendSystemMessage(Component.literal("§a[RpEssentials] Prepared profile for " + name + " deleted."));
            RpEssentials.LOGGER.info("[RpEssentials] Prepared profile for {} deleted by {}",
                    name, admin.getGameProfile().getName());

            RequestOpenGuiPacket.handlePlayerProfileGui(admin);
        });
    }
}