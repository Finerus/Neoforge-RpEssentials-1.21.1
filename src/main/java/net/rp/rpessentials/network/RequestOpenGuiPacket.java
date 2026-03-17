package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.config.ProfessionConfig;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.RpEssentialsPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Packet CLIENT → SERVEUR
 * Envoyé quand l'admin appuie sur une touche de raccourci GUI.
 * Le serveur vérifie isStaff() avant de répondre.
 */
public record RequestOpenGuiPacket(GuiType guiType) implements CustomPacketPayload {

    public enum GuiType { PROFESSION, PLAYER_PROFILE }

    public static final Type<RequestOpenGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "request_open_gui"));

    public static final StreamCodec<FriendlyByteBuf, RequestOpenGuiPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestOpenGuiPacket decode(FriendlyByteBuf buf) {
                    return new RequestOpenGuiPacket(GuiType.values()[buf.readByte()]);
                }
                @Override
                public void encode(FriendlyByteBuf buf, RequestOpenGuiPacket packet) {
                    buf.writeByte(packet.guiType().ordinal());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — côté SERVEUR
    // =========================================================================

    public static void handleOnServer(RequestOpenGuiPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // Vérification des droits — si pas staff, on ignore silencieusement
            if (!RpEssentialsPermissions.isStaff(player)) return;

            switch (packet.guiType()) {
                case PROFESSION     -> handleProfessionGui(player);
                case PLAYER_PROFILE -> handlePlayerProfileGui(player);
            }
        });
    }

    // ── GUI Profession ─────────────────────────────────────────────────────────

    private static void handleProfessionGui(ServerPlayer player) {
        List<OpenProfessionGuiPacket.ProfessionEntry> entries = new ArrayList<>();

        try {
            List<? extends String> raw = ProfessionConfig.PROFESSIONS.get();
            for (String line : raw) {
                String[] parts = line.split(";", 3);
                if (parts.length == 3) {
                    String id    = parts[0].trim();
                    String name  = parts[1].trim();
                    String color = parts[2].trim();

                    entries.add(new OpenProfessionGuiPacket.ProfessionEntry(
                            id, name, color,
                            collectForProfession(id, ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get()),
                            collectForProfession(id, ProfessionConfig.PROFESSION_ALLOWED_BLOCKS.get()),
                            collectForProfession(id, ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get()),
                            collectForProfession(id, ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get())
                    ));
                }
            }
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.warn("[GUI] ProfessionConfig not loaded yet");
        }

        PacketDistributor.sendToPlayer(player, new OpenProfessionGuiPacket(entries));
    }

    /** Extrait les items autorisés pour une profession depuis une liste "profession;item1,item2" */
    private static List<String> collectForProfession(String profId, List<? extends String> source) {
        for (String line : source) {
            String[] parts = line.split(";", 2);
            if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(profId)) {
                List<String> result = new ArrayList<>();
                for (String item : parts[1].split(",")) {
                    String trimmed = item.trim();
                    if (!trimmed.isEmpty()) result.add(trimmed);
                }
                return result;
            }
        }
        return new ArrayList<>();
    }

    // ── GUI Profil Joueur ──────────────────────────────────────────────────────

    private static void handlePlayerProfileGui(ServerPlayer admin) {
        MinecraftServer server = admin.getServer();

        // ── Liste des joueurs connectés ────────────────────────────────────────
        List<OpenPlayerProfileGuiPacket.PlayerData> players = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID uuid     = p.getUUID();
            String mcName = p.getGameProfile().getName();
            String nick   = NicknameManager.hasNickname(uuid) ? NicknameManager.getNickname(uuid) : "";
            String role   = detectCurrentRole(p);
            String license = LicenseManager.getLicenses(uuid).stream().findFirst().orElse("");
            players.add(new OpenPlayerProfileGuiPacket.PlayerData(uuid, mcName, nick, role, license));
        }

        // ── IDs de professions disponibles ────────────────────────────────────
        List<String> professionIds = new ArrayList<>();
        try {
            for (String line : ProfessionConfig.PROFESSIONS.get()) {
                String[] parts = line.split(";", 2);
                if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
                    professionIds.add(parts[0].trim());
                }
            }
        } catch (IllegalStateException ignored) {}

        // ── Rôles configurés (envoyés depuis le serveur, le client n'a pas accès à la config) ──
        List<String> roleIds = new ArrayList<>();
        try {
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String[] parts = entry.split(";", 2);
                if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
                    roleIds.add(parts[0].trim());
                }
            }
        } catch (IllegalStateException ignored) {}

        PacketDistributor.sendToPlayer(admin,
                new OpenPlayerProfileGuiPacket(players, professionIds, roleIds));
    }

    /** Détecte le rôle actuel d'un joueur via ses tags vanilla */
    private static String detectCurrentRole(ServerPlayer player) {
        try {
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String[] parts = entry.split(";", 2);
                if (parts.length >= 1) {
                    String roleTag = parts[0].trim();
                    if (player.getTags().contains(roleTag)) return roleTag;
                }
            }
        } catch (IllegalStateException ignored) {}
        return "";
    }
}