package net.rp.rpessentials.network;

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
import net.rp.rpessentials.RpEssentialsPermissions;
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
            if (!RpEssentialsPermissions.isStaff(admin)) return;

            String name = packet.playerName().trim();
            if (name.isEmpty()) return;
            MinecraftServer server = admin.getServer();

            // Résolution en tâche de fond : évite un appel réseau bloquant sur le thread serveur
            net.rp.rpessentials.RpEssentialsIO.submit(() -> {
                java.util.Optional<GameProfile> result = server.getProfileCache() != null
                        ? server.getProfileCache().get(name)
                        : java.util.Optional.empty();

                server.execute(() -> {
                    if (result.isEmpty()) {
                        admin.sendSystemMessage(Component.literal("§c[RpEssentials] Player not found: §f" + name));
                        return;
                    }
                    GameProfile profile = result.get();

                    if (server.getPlayerList().getPlayer(profile.getId()) != null
                            || PendingProfileManager.hasPending(profile.getId())) {
                        admin.sendSystemMessage(Component.literal("§e[RpEssentials] " + profile.getName() + " is already known."));
                        return;
                    }
                    if (net.rp.rpessentials.moderation.LastConnectionManager.getEntry(profile.getId()) != null) {
                        admin.sendSystemMessage(Component.literal(
                                "§e[RpEssentials] " + profile.getName() + " has already connected to this server before. No need to prepare a profile, edit them directly from the offline list."));
                        return;
                    }

                    PendingProfileManager.addPending(profile.getId(), profile.getName());
                    admin.sendSystemMessage(Component.literal("§a[RpEssentials] " + profile.getName() + " added to prepared profiles."));
                    RequestOpenGuiPacket.handlePlayerProfileGui(admin);
                });
            });
        });
    }
}