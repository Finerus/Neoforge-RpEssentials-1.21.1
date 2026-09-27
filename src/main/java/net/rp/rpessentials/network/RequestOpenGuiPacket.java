package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.ConfigInspector;
import net.rp.rpessentials.config.ProfessionConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.profession.LicenseManager;

import java.util.*;

/**
 * Packet CLIENT → SERVEUR : demande d'ouverture d'un GUI admin.
 */
public record RequestOpenGuiPacket(GuiType guiType) implements CustomPacketPayload {

    public enum GuiType { PROFESSION, PLAYER_PROFILE, CONFIG_MANAGER, ROLES }

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
            if (!RpEssentialsPermissions.isStaff(player)) return;

            switch (packet.guiType()) {
                case PROFESSION      -> handleProfessionGui(player);
                case PLAYER_PROFILE  -> handlePlayerProfileGui(player);
                case CONFIG_MANAGER  -> handleConfigManagerGui(player);
                case ROLES -> handleRolesGui(player);
            }
        });
    }

    // ── GUI Profession ─────────────────────────────────────────────────────────
    private static void handleProfessionGui(ServerPlayer player) {
        List<OpenProfessionGuiPacket.ProfessionEntry> profEntries = new ArrayList<>();
        List<OpenRolesGuiPacket.RoleEntry> roleEntries = new ArrayList<>();

        try {
            for (String line : ProfessionConfig.PROFESSIONS.get()) {
                String[] parts = line.split(";", 3);
                if (parts.length == 3) {
                    String id    = parts[0].trim();
                    String name  = parts[1].trim();
                    String color = parts[2].trim();
                    profEntries.add(new OpenProfessionGuiPacket.ProfessionEntry(
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

        try {
            for (String line : RpEssentialsConfig.ROLES.get()) {
                String[] parts = line.split(";", 3);
                if (parts.length < 1) continue;
                String id  = parts[0].trim();
                String lp  = parts.length >= 2 ? parts[1].trim() : id;
                List<String> perms = new ArrayList<>();
                if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                    for (String p : parts[2].split(",")) {
                        String t = p.trim();
                        if (!t.isEmpty()) perms.add(t);
                    }
                }
                roleEntries.add(new OpenRolesGuiPacket.RoleEntry(id, lp, perms));
            }
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.warn("[GUI] RpEssentialsConfig not loaded yet for roles");
        }

        PacketDistributor.sendToPlayer(player, new OpenProfessionGuiPacket(profEntries));
        PacketDistributor.sendToPlayer(player, new OpenRolesGuiPacket(roleEntries));

        List<String> gCrafts = new ArrayList<>(), gBlocks = new ArrayList<>(),
                gItems  = new ArrayList<>(), gEquip  = new ArrayList<>(),
                gContainers = new ArrayList<>();
        try {
            ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get().forEach(s -> gCrafts.add((String) s));
            ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS.get().forEach(s -> gBlocks.add((String) s));
            ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get().forEach(s -> gItems.add((String) s));
            ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get().forEach(s -> gEquip.add((String) s));
            ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS.get().forEach(s -> gContainers.add((String) s));
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.warn("[GUI] ProfessionConfig not loaded for global restrictions");
        }
        PacketDistributor.sendToPlayer(player,
                new OpenGlobalRestrictionsPacket(gCrafts, gBlocks, gItems, gEquip, gContainers));
    }

    private static List<String> collectForProfession(String profId, List<? extends String> source) {
        for (String line : source) {
            String[] parts = line.split(";", 2);
            if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(profId)) {
                List<String> result = new ArrayList<>();
                for (String item : parts[1].split(",")) {
                    String t = item.trim();
                    if (!t.isEmpty()) result.add(t);
                }
                return result;
            }
        }
        return new ArrayList<>();
    }

    // ── GUI Profil Joueur ─────────────────────────────────────
    static void handlePlayerProfileGui(ServerPlayer admin) {
        MinecraftServer server = admin.getServer();
        List<OpenPlayerProfileGuiPacket.PlayerData> players = new ArrayList<>();
        Set<UUID> knownIds = new HashSet<>();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            players.add(buildPlayerData(p));
            knownIds.add(p.getUUID());
        }

        // Joueurs hors ligne déjà connectés au moins une fois sur le serveur
        for (var entry : LastConnectionManager.getAllSortedByLogin()) {
            UUID uuid = entry.getKey();
            if (knownIds.contains(uuid)) continue;
            players.add(buildOfflineKnownPlayerData(uuid));
            knownIds.add(uuid);
        }

        // Joueurs préparés à l'avance par le staff, jamais connectés
        for (net.rp.rpessentials.profession.PendingProfileManager.PendingEntry entry :
                net.rp.rpessentials.profession.PendingProfileManager.getAllPending()) {
            UUID uuid;
            try { uuid = UUID.fromString(entry.uuid); }
            catch (IllegalArgumentException e) { continue; }
            if (knownIds.contains(uuid)) continue;
            players.add(new OpenPlayerProfileGuiPacket.PlayerData(
                    uuid, entry.mcName, entry.nickname, entry.role,
                    new ArrayList<>(entry.licenses),
                    0, false, "", 0L, 0L, 0, false, List.of(), true));
            knownIds.add(uuid);
        }

        // Professions disponibles
        List<String> professionIds = new ArrayList<>();
        try {
            for (String line : ProfessionConfig.PROFESSIONS.get()) {
                String[] parts = line.split(";", 2);
                if (parts.length >= 1 && !parts[0].trim().isEmpty())
                    professionIds.add(parts[0].trim());
            }
        } catch (IllegalStateException ignored) {}

        // Rôles disponibles
        List<String> roleIds = new ArrayList<>();
        try {
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String[] parts = entry.split(";", 2);
                if (parts.length >= 1 && !parts[0].trim().isEmpty())
                    roleIds.add(parts[0].trim());
            }
        } catch (IllegalStateException ignored) {}

        PacketDistributor.sendToPlayer(admin,
                new OpenPlayerProfileGuiPacket(players, professionIds, roleIds));
    }

    /**
     * Construit les données d'un joueur hors ligne mais déjà connu (déjà connecté au moins une fois).
     * Le rôle n'est pas détectable hors ligne (les tags scoreboard nécessitent une entité chargée).
     */
    private static OpenPlayerProfileGuiPacket.PlayerData buildOfflineKnownPlayerData(UUID uuid) {
        LastConnectionManager.ConnectionEntry conn = LastConnectionManager.getEntry(uuid);
        String mcName = conn != null && conn.mcName != null ? conn.mcName : uuid.toString();
        String nick   = NicknameManager.hasNickname(uuid) ? NicknameManager.getNickname(uuid) : "";
        List<String> licenses = LicenseManager.getLicenses(uuid);

        int warnCount = 0;
        try { warnCount = WarnManager.getActiveWarns(uuid).size(); }
        catch (Exception ignored) {}

        boolean isMuted    = false;
        String  muteExpiry = "";
        try {
            isMuted = MuteManager.isMuted(uuid);
            if (isMuted) {
                MuteManager.MuteEntry muteEntry = MuteManager.getEntry(uuid);
                muteExpiry = muteEntry != null ? muteEntry.getFormattedExpiry() : "";
            }
        } catch (Exception ignored) {}

        long playtimeMs = PlaytimeManager.getTotalPlaytimeMs(uuid);

        List<NoteManager.NoteEntry> rawNotes = new ArrayList<>();
        try { rawNotes = NoteManager.getNotes(uuid); } catch (Exception ignored) {}
        int noteCount = rawNotes.size();
        List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> noteEntries = rawNotes.stream()
                .map(n -> new OpenPlayerProfileGuiPacket.PlayerData.NoteEntry(n.id, n.text, n.authorName, n.timestamp))
                .collect(java.util.stream.Collectors.toList());

        return new OpenPlayerProfileGuiPacket.PlayerData(
                uuid, mcName, nick, "", licenses,
                warnCount, isMuted, muteExpiry,
                playtimeMs, 0L, noteCount, false, noteEntries, false
        );
    }

    /**
     * Construit le snapshot complet d'un joueur en ligne pour le GUI.
     * Collecte warns, mute, playtime, notes.
     */
    private static OpenPlayerProfileGuiPacket.PlayerData buildPlayerData(ServerPlayer p) {
        UUID uuid     = p.getUUID();
        String mcName = p.getGameProfile().getName();
        String nick   = NicknameManager.hasNickname(uuid) ? NicknameManager.getNickname(uuid) : "";
        String role   = detectCurrentRole(p);
        List<String> licenses = LicenseManager.getLicenses(uuid);

        // Warns actifs
        int warnCount = 0;
        try { warnCount = WarnManager.getActiveWarns(uuid).size(); }
        catch (Exception ignored) {}

        // Mute
        boolean isMuted    = false;
        String  muteExpiry = "";
        try {
            isMuted = MuteManager.isMuted(uuid);
            if (isMuted) {
                MuteManager.MuteEntry muteEntry = MuteManager.getEntry(uuid);
                muteExpiry = muteEntry != null ? muteEntry.getFormattedExpiry() : "";
            }
        } catch (Exception ignored) {}

        // Playtime
        long playtimeMs = PlaytimeManager.getTotalPlaytimeMs(uuid);
        long sessionMs  = PlaytimeManager.getCurrentSessionMs(uuid);

        List<NoteManager.NoteEntry> rawNotes = new ArrayList<>();
        try { rawNotes = NoteManager.getNotes(uuid); } catch (Exception ignored) {}
        int noteCount = rawNotes.size();

        List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> noteEntries = rawNotes.stream()
                .map(n -> new OpenPlayerProfileGuiPacket.PlayerData.NoteEntry(n.id, n.text, n.authorName, n.timestamp))
                .collect(java.util.stream.Collectors.toList());

        return new OpenPlayerProfileGuiPacket.PlayerData(
                uuid, mcName, nick, role, licenses,
                warnCount, isMuted, muteExpiry,
                playtimeMs, sessionMs, noteCount, true, noteEntries, false
        );
    }

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

    // ── GUI Config Manager ─────────────────────────────────────────────────────
    private static void handleConfigManagerGui(ServerPlayer player) {
        List<ConfigInspector.FileInfo> fileInfos = ConfigInspector.getFileInfos();
        List<ConfigGuiFilesPacket.FileEntry> entries = fileInfos.stream()
                .map(fi -> new ConfigGuiFilesPacket.FileEntry(fi.id(), fi.displayName()))
                .toList();
        PacketDistributor.sendToPlayer(player, new ConfigGuiFilesPacket(entries));
        RpEssentials.LOGGER.debug("[ConfigGUI] Sent {} config files to {}",
                entries.size(), player.getName().getString());
    }

    // ── GUI Role ─────────────────────────────────────────────────────
    private static void handleRolesGui(ServerPlayer player) {
        List<OpenRolesGuiPacket.RoleEntry> entries = new ArrayList<>();
        try {
            for (String line : RpEssentialsConfig.ROLES.get()) {
                String[] parts = line.split(";", 3);
                if (parts.length < 1) continue;
                String id    = parts[0].trim();
                String lp    = parts.length >= 2 ? parts[1].trim() : id;
                List<String> perms = new ArrayList<>();
                if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                    for (String p : parts[2].split(",")) {
                        String t = p.trim();
                        if (!t.isEmpty()) perms.add(t);
                    }
                }
                entries.add(new OpenRolesGuiPacket.RoleEntry(id, lp, perms));
            }
        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.warn("[GUI] RpEssentialsConfig not loaded yet");
        }
        PacketDistributor.sendToPlayer(player, new OpenRolesGuiPacket(entries));
    }
}