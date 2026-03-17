package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Packet SERVEUR → CLIENT
 * Envoie la liste des joueurs connectés, leurs données RP,
 * la liste des professions disponibles ET la liste des rôles configurés.
 * Toutes les données viennent du serveur — le client n'accède à aucune config serveur.
 */
public record OpenPlayerProfileGuiPacket(
        List<PlayerData> players,
        List<String> availableProfessionIds,
        List<String> availableRoles
) implements CustomPacketPayload {

    // =========================================================================
    // DATA — snapshot des données RP d'un joueur
    // =========================================================================

    public record PlayerData(
            UUID uuid,
            String mcName,
            String currentNick,     // "" si pas de nickname
            String currentRole,     // "" si aucun rôle détecté
            String currentLicense   // première licence, "" si aucune
    ) {}

    // =========================================================================
    // PACKET INFRA
    // =========================================================================

    public static final Type<OpenPlayerProfileGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "open_player_profile_gui"));

    public static final StreamCodec<FriendlyByteBuf, OpenPlayerProfileGuiPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenPlayerProfileGuiPacket decode(FriendlyByteBuf buf) {
                    // Joueurs
                    int playerCount = buf.readVarInt();
                    List<PlayerData> players = new ArrayList<>(playerCount);
                    for (int i = 0; i < playerCount; i++) {
                        players.add(new PlayerData(
                                buf.readUUID(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf()
                        ));
                    }
                    // Professions
                    int profCount = buf.readVarInt();
                    List<String> profIds = new ArrayList<>(profCount);
                    for (int i = 0; i < profCount; i++) profIds.add(buf.readUtf());
                    // Rôles
                    int roleCount = buf.readVarInt();
                    List<String> roles = new ArrayList<>(roleCount);
                    for (int i = 0; i < roleCount; i++) roles.add(buf.readUtf());

                    return new OpenPlayerProfileGuiPacket(players, profIds, roles);
                }

                @Override
                public void encode(FriendlyByteBuf buf, OpenPlayerProfileGuiPacket packet) {
                    // Joueurs
                    buf.writeVarInt(packet.players().size());
                    for (PlayerData p : packet.players()) {
                        buf.writeUUID(p.uuid());
                        buf.writeUtf(p.mcName());
                        buf.writeUtf(p.currentNick());
                        buf.writeUtf(p.currentRole());
                        buf.writeUtf(p.currentLicense());
                    }
                    // Professions
                    buf.writeVarInt(packet.availableProfessionIds().size());
                    for (String id : packet.availableProfessionIds()) buf.writeUtf(id);
                    // Rôles
                    buf.writeVarInt(packet.availableRoles().size());
                    for (String r : packet.availableRoles()) buf.writeUtf(r);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — côté CLIENT
    // =========================================================================

    public static void handleOnClient(OpenPlayerProfileGuiPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.openPlayerProfileGui(
                    packet.players(),
                    packet.availableProfessionIds(),
                    packet.availableRoles()
            );
        });
    }
}