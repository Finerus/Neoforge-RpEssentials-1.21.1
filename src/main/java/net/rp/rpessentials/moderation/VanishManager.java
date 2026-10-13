package net.rp.rpessentials.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsDataPaths;
import net.rp.rpessentials.RpEssentialsIO;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.SyncNametagDataPacket;
import net.rp.rpessentials.api.VanishTrackable;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.mixin.ChunkMapAccessor;
import net.rp.rpessentials.mixin.ClientboundPlayerInfoUpdatePacketAccessor;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishManager {

    public enum Aspect {
        WORLD, LIST, SELECTORS, JOIN_VANILLA, JOIN_CUSTOM, DEATH, ADVANCEMENTS, NOISE,
        GAME_EVENTS, CONTAINERS, NAMETAG, GUI, PING, MOBS, PICKUP, TRIGGERS
    }

    private static final Map<UUID, Integer> vanished = new ConcurrentHashMap<>();
    private static final Set<UUID> silentOpen = ConcurrentHashMap.newKeySet();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile boolean ready = false;
    private static File dataFile = null;

    private VanishManager() {}

    // =========================================================================
    // CONFIG
    // =========================================================================
    private static boolean opt(ModConfigSpec.BooleanValue v) {
        try { return v.get(); }
        catch (IllegalStateException e) { return v.getDefault(); }
    }

    public static boolean enabled() { return opt(ModerationConfig.ENABLE_VANISH); }

    public static boolean hides(Aspect a) {
        if (!enabled()) return false;
        return opt(switch (a) {
            case WORLD -> ModerationConfig.VH_WORLD;
            case LIST -> ModerationConfig.VH_LIST;
            case SELECTORS -> ModerationConfig.VH_SELECTORS;
            case JOIN_VANILLA -> ModerationConfig.VH_JOIN_VANILLA;
            case JOIN_CUSTOM -> ModerationConfig.VH_JOIN_CUSTOM;
            case DEATH -> ModerationConfig.VH_DEATH;
            case ADVANCEMENTS -> ModerationConfig.VH_ADVANCEMENTS;
            case NOISE -> ModerationConfig.VH_NOISE;
            case GAME_EVENTS -> ModerationConfig.VH_GAME_EVENTS;
            case CONTAINERS -> ModerationConfig.VH_CONTAINERS;
            case NAMETAG -> ModerationConfig.VH_NAMETAG;
            case GUI -> ModerationConfig.VH_GUI;
            case PING -> ModerationConfig.VH_PING;
            case MOBS -> ModerationConfig.VH_MOBS;
            case PICKUP -> ModerationConfig.VH_PICKUP;
            case TRIGGERS -> ModerationConfig.VH_TRIGGERS;
        });
    }

    public static boolean invulnerable() { return enabled() && opt(ModerationConfig.VANISH_INVULNERABLE); }
    public static boolean notifyStaff() { return opt(ModerationConfig.VANISH_NOTIFY_STAFF); }
    public static boolean reminderEnabled() { return opt(ModerationConfig.VANISH_LOGIN_REMINDER); }

    public static int defaultLevel() {
        try { return ModerationConfig.VANISH_DEFAULT_LEVEL.get(); }
        catch (IllegalStateException e) { return 4; }
    }

    private static String tabMode() {
        try { return ModerationConfig.VANISH_TAB_MODE.get(); }
        catch (IllegalStateException e) { return "REMOVE"; }
    }

    private static String marker() {
        try { return ColorHelper.translateAlternateColorCodes(ModerationConfig.VANISH_TAB_MARKER.get()); }
        catch (IllegalStateException e) { return "§7[V] "; }
    }

    // =========================================================================
    // PERSISTANCE
    // =========================================================================
    private static void init() {
        if (ready) return;
        synchronized (VanishManager.class) {
            if (ready) return;
            try {
                File dir = RpEssentialsDataPaths.getDataFolder();
                if (!dir.exists()) dir.mkdirs();
                dataFile = new File(dir, "vanish.json");
                if (dataFile.exists() && opt(ModerationConfig.VANISH_PERSIST)) load();
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[VanishManager] Failed to initialize", e);
            }
            ready = true;
        }
    }

    private static void load() {
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            Map<String, Integer> data = GSON.fromJson(reader, type);
            if (data == null) return;
            vanished.clear();
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                try {
                    String k = e.getKey();
                    vanished.put(UUID.fromString(k.contains(" ") ? k.substring(0, k.indexOf(' ')) : k), e.getValue());
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[VanishManager] Failed to load", e);
            RpEssentialsIO.quarantine(dataFile);
        }
    }

    private static void save() {
        init();
        if (dataFile == null || !opt(ModerationConfig.VANISH_PERSIST)) return;
        Map<String, Integer> data = new LinkedHashMap<>();
        vanished.forEach((k, v) -> data.put(k.toString(), v));
        RpEssentialsIO.saveJson(dataFile, data, GSON);
    }

    public static void reload() {
        ready = false;
        vanished.clear();
        silentOpen.clear();
        init();
    }

    // =========================================================================
    // ETAT ET VISIBILITE
    // =========================================================================
    public static int levelOf(ServerPlayer p) {
        for (int l = 4; l > 0; l--) if (p.hasPermissions(l)) return l;
        return 0;
    }

    public static boolean anyVanished() {
        init();
        return enabled() && !vanished.isEmpty();
    }

    public static boolean isVanished(UUID id) {
        init();
        return enabled() && vanished.containsKey(id);
    }

    public static int getLevel(UUID id) {
        init();
        Integer v = vanished.get(id);
        return v == null ? -1 : v;
    }

    public static Map<UUID, Integer> getAll() {
        init();
        return new HashMap<>(vanished);
    }

    public static boolean isHiddenFrom(ServerPlayer viewer, UUID target) {
        init();
        if (!enabled()) return false;
        Integer level = vanished.get(target);
        if (level == null || viewer.getUUID().equals(target)) return false;
        return levelOf(viewer) <= level;
    }

    public static boolean isHiddenFrom(CommandSourceStack src, UUID target) {
        return src.getEntity() instanceof ServerPlayer v && isHiddenFrom(v, target);
    }

    public static boolean canHide(Aspect aspect, ServerPlayer viewer, UUID target) {
        return hides(aspect) && isHiddenFrom(viewer, target);
    }

    public static boolean canHide(Aspect aspect, CommandSourceStack src, UUID target) {
        return hides(aspect) && isHiddenFrom(src, target);
    }

    public static List<ServerPlayer> visibleTo(CommandSourceStack src) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            if (!canHide(Aspect.SELECTORS, src, p.getUUID())) out.add(p);
        }
        return out;
    }

    // =========================================================================
    // ACTIONS
    // =========================================================================
    public static void set(MinecraftServer server, ServerPlayer target, int level) {
        init();
        vanished.put(target.getUUID(), level);
        save();
        refresh(server, target);
    }

    public static void clear(MinecraftServer server, ServerPlayer target) {
        init();
        if (vanished.remove(target.getUUID()) != null) save();
        refresh(server, target);
    }

    public static void refreshAll(MinecraftServer server) {
        init();
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            refresh(server, p);
        }
    }

    private static void refresh(MinecraftServer server, ServerPlayer target) {
        UUID id = target.getUUID();
        boolean removeTab = "REMOVE".equals(tabMode());
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer == target) continue;
            if (removeTab && isHiddenFrom(viewer, id)) {
                viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(id)));
            } else {
                viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
            }
        }
        ServerChunkCache cache = target.serverLevel().getChunkSource();
        Object tracker = ((ChunkMapAccessor) cache.chunkMap).rpessentials$entityMap().get(target.getId());
        if (tracker instanceof VanishTrackable t) t.rpessentials$refresh(target.serverLevel().players());
        SyncNametagDataPacket.broadcastForPlayer(target);
    }

    // =========================================================================
    // TAB LIST
    // =========================================================================
    public static ClientboundPlayerInfoUpdatePacket filterInfo(ServerPlayer receiver, ClientboundPlayerInfoUpdatePacket packet) {
        if (!anyVanished()) return packet;
        String mode = tabMode();
        if (mode.equals("KEEP")) return packet;
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries =
                ((ClientboundPlayerInfoUpdatePacketAccessor) packet).getEntries();
        List<ClientboundPlayerInfoUpdatePacket.Entry> out = new ArrayList<>(entries.size());
        boolean changed = false;
        for (ClientboundPlayerInfoUpdatePacket.Entry e : entries) {
            if (!isHiddenFrom(receiver, e.profileId())) { out.add(e); continue; }
            changed = true;
            if (mode.equals("OBFUSCATE")) {
                out.add(new ClientboundPlayerInfoUpdatePacket.Entry(e.profileId(), e.profile(), e.listed(),
                        e.latency(), e.gameMode(), Component.literal("§k?????"), e.chatSession()));
            }
        }
        if (!changed) return packet;
        ClientboundPlayerInfoUpdatePacket copy = new ClientboundPlayerInfoUpdatePacket(packet.actions(), List.of());
        ((ClientboundPlayerInfoUpdatePacketAccessor) copy).setEntries(out);
        return copy;
    }

    public static ClientboundPlayerInfoUpdatePacket markViewers(ServerPlayer receiver, ClientboundPlayerInfoUpdatePacket packet) {
        if (!anyVanished() || !opt(ModerationConfig.VANISH_TAB_MARKER_ENABLED)) return packet;
        var actions = packet.actions();
        if (!actions.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
                && !actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) return packet;
        String mark = marker();
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries =
                ((ClientboundPlayerInfoUpdatePacketAccessor) packet).getEntries();
        List<ClientboundPlayerInfoUpdatePacket.Entry> out = new ArrayList<>(entries.size());
        boolean changed = false;
        for (ClientboundPlayerInfoUpdatePacket.Entry e : entries) {
            if (vanished.containsKey(e.profileId()) && !isHiddenFrom(receiver, e.profileId())) {
                Component base = e.displayName() != null ? e.displayName() : Component.literal(e.profile().getName());
                out.add(new ClientboundPlayerInfoUpdatePacket.Entry(e.profileId(), e.profile(), e.listed(),
                        e.latency(), e.gameMode(), Component.literal(mark).append(base), e.chatSession()));
                changed = true;
            } else {
                out.add(e);
            }
        }
        if (!changed) return packet;
        ClientboundPlayerInfoUpdatePacket copy = new ClientboundPlayerInfoUpdatePacket(actions, List.of());
        ((ClientboundPlayerInfoUpdatePacketAccessor) copy).setEntries(out);
        return copy;
    }

    // =========================================================================
    // PING, CONTENEURS, MESSAGES
    // =========================================================================
    public static ServerStatus filterStatus(MinecraftServer server, ServerStatus status) {
        if (!anyVanished() || !hides(Aspect.PING) || status.players().isEmpty()) return status;
        ServerStatus.Players p = status.players().get();
        int hiddenOnline = 0;
        for (UUID id : vanished.keySet()) if (server.getPlayerList().getPlayer(id) != null) hiddenOnline++;
        List<GameProfile> sample = new ArrayList<>();
        for (GameProfile g : p.sample()) if (!vanished.containsKey(g.getId())) sample.add(g);
        return new ServerStatus(status.description(),
                Optional.of(new ServerStatus.Players(p.max(), Math.max(0, p.online() - hiddenOnline), sample)),
                status.version(), status.favicon(), status.enforcesSecureChat());
    }

    public static boolean silentOpen(ServerPlayer p) {
        if (isVanished(p.getUUID()) && hides(Aspect.CONTAINERS)) {
            silentOpen.add(p.getUUID());
            return true;
        }
        return false;
    }

    private static final ThreadLocal<UUID> awarding = new ThreadLocal<>();

    public static void beginAward(UUID id) { awarding.set(id); }

    public static void endAward() { awarding.remove(); }

    public static boolean isAdvancementHidden() {
        UUID id = awarding.get();
        return id != null && isVanished(id) && hides(Aspect.ADVANCEMENTS);
    }

    public static boolean silentClose(ServerPlayer p) {
        return silentOpen.remove(p.getUUID());
    }

    public static void notifySilent(MinecraftServer server, ServerPlayer player, boolean joined) {
        if (!notifyStaff()) return;
        Component msg = Component.literal("§7[Vanish] §f" + player.getName().getString()
                + " §7" + (joined ? "joined" : "left") + " silently.");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(player.getUUID())) continue;
            if (RpEssentialsPermissions.isStaff(p) && !isHiddenFrom(p, player.getUUID())) p.sendSystemMessage(msg);
        }
    }
}