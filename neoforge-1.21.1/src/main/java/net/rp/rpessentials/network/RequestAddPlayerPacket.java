package net.rp.rpessentials.network;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.profession.PendingProfileManager;

/**
 * Packet CLIENT -> SERVEUR
 * Demande d'ajout d'un joueur jamais connecté à la liste de préparation des profils.
 */
public record RequestAddPlayerPacket(String playerName) implements CustomPacketPayload {

    public static final Type<RequestAddPlayerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "request_add_player"));

    public static final StreamCodec<ByteBuf, RequestAddPlayerPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestAddPlayerPacket::playerName,
            RequestAddPlayerPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(RequestAddPlayerPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer admin)) return;
            if (!RpPerms.has(admin, RpPerm.PROFILE)) return;

            String name = packet.playerName().trim();
            if (name.isEmpty() || name.length() > 32) return;
            if (!PacketGuard.every(admin, "add_player", 500L)) return;
            MinecraftServer server = admin.getServer();

            // Résolution en tâche de fond : évite un appel réseau bloquant sur le thread serveur
            // (not on the IO thread of the mod: a slow answer from Mojang would delay the saves).
            net.minecraft.Util.ioPool().execute(() -> {
                java.util.Optional<GameProfile> result = server.getProfileCache() != null
                        ? server.getProfileCache().get(name)
                        : java.util.Optional.empty();

                server.execute(() -> {
                    if (result.isEmpty()) {
                        admin.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(admin, MessagesConfig.GUI_ADD_NOT_FOUND, "player", name)));
                        return;
                    }
                    GameProfile profile = result.get();

                    if (server.getPlayerList().getPlayer(profile.getId()) != null
                            || PendingProfileManager.hasPending(profile.getId())) {
                        admin.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(admin, MessagesConfig.GUI_ADD_KNOWN, "player", profile.getName())));
                        return;
                    }
                    if (net.rp.rpessentials.moderation.LastConnectionManager.getEntry(profile.getId()) != null) {
                        admin.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(admin, MessagesConfig.GUI_ADD_CONNECTED, "player", profile.getName())));
                        return;
                    }

                    PendingProfileManager.addPending(profile.getId(), profile.getName());
                    admin.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(admin, MessagesConfig.GUI_ADD_DONE, "player", profile.getName())));
                    RequestOpenGuiPacket.handlePlayerProfileGui(admin);
                });
            });
        });
    }
}