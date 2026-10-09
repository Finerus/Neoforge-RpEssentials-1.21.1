package net.rp.rpessentials;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.profession.LicenseManager;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Packet serveur → client.
 * Envoie les données nametag d'UN joueur cible au client récepteur.
 */
public record SyncNametagDataPacket(
        UUID targetUUID,
        String displayName,
        String prefix,
        String suffix,
        String profession,
        boolean isStaff
) implements CustomPacketPayload {

    public static final Type<SyncNametagDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "sync_nametag_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncNametagDataPacket> CODEC =
            new StreamCodec<>() {
                @Override
                public SyncNametagDataPacket decode(FriendlyByteBuf buf) {
                    return new SyncNametagDataPacket(
                            buf.readUUID(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readBoolean()
                    );
                }
                @Override
                public void encode(FriendlyByteBuf buf, SyncNametagDataPacket p) {
                    buf.writeUUID(p.targetUUID());
                    buf.writeUtf(p.displayName());
                    buf.writeUtf(p.prefix());
                    buf.writeUtf(p.suffix());
                    buf.writeUtf(p.profession());
                    buf.writeBoolean(p.isStaff());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // =========================================================================
    // HANDLER (côté CLIENT)
    // =========================================================================
    public static void handle(SyncNametagDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            net.rp.rpessentials.client.ClientNametagCache.update(packet);
        });
    }

    // =========================================================================
    // FACTORY — construit le packet depuis un ServerPlayer
    // =========================================================================
    public static SyncNametagDataPacket from(ServerPlayer target) {
        UUID uuid = target.getUUID();

        String displayName = NicknameManager.hasNickname(uuid)
                ? NicknameManager.getNickname(uuid)
                : target.getGameProfile().getName();

        String prefix  = RpEssentials.getPlayerPrefix(target);
        String suffix  = RpEssentials.getPlayerSuffix(target);

        java.util.List<String> licenses = LicenseManager.getLicenses(uuid);
        String profession = licenses.isEmpty() ? "" : licenses.get(0);

        boolean isStaff = RpEssentialsPermissions.isStaff(target);

        return new SyncNametagDataPacket(uuid, displayName, prefix, suffix, profession, isStaff);
    }

    // =========================================================================
    // BROADCAST — envoie ce packet à tous les joueurs connectés
    // =========================================================================
    public static void broadcastForPlayer(ServerPlayer target) {
        SyncNametagDataPacket packet = from(target);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet);
    }

    public static void refreshNow(ServerPlayer target) {
        broadcastForPlayer(target);
        MinecraftServer server = target.getServer();
        if (server == null) return;
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(target)));
    }

    /** Delai pour laisser LuckPerms appliquer le changement de groupe. */
    public static void broadcastDelayed(ServerPlayer target) {
        MinecraftServer server = target.getServer();
        if (server == null) return;
        CompletableFuture.runAsync(() -> server.execute(() -> {
            if (server.getPlayerList().getPlayer(target.getUUID()) != null) refreshNow(target);
        }), CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS));
    }
}