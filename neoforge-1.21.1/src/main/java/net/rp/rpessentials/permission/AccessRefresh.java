package net.rp.rpessentials.permission;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.gameplay.TabBlur;
import net.rp.rpessentials.moderation.VanishManager;
import net.rp.rpessentials.network.SyncNametagDataPacket;
import net.rp.rpessentials.network.SyncPermissionsPacket;
import net.rp.rpessentials.profession.ProfessionSyncHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a player can do, updated as soon as it may have changed, whoever changed it: role tag (any command, function or
 * mod), OP level, LuckPerms. A mark forgets the cached roles at once (the next check is right); what the player was
 * sent is compared once, at the end of the tick, and only sent again when it is different.
 */
public final class AccessRefresh {

    /** What the player was last told: their nodes and their OP level. */
    private record State(Set<String> nodes, int opLevel) {}

    private static final Set<UUID> waiting = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, State> known = new ConcurrentHashMap<>();

    private AccessRefresh() {}

    /** Any thread. */
    public static void mark(UUID id) {
        RpEssentialsRoleManager.invalidate(id);
        RpEssentials.invalidateMeta(id);
        waiting.add(id);
    }

    public static void markAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) mark(p.getUUID());
    }

    /** A tag was added to or removed from a player. */
    public static void onTag(ServerPlayer player, String tag) {
        if (RpEssentialsRoleManager.isRoleTag(tag)) mark(player.getUUID());
    }

    public static void onLogin(ServerPlayer player) {
        RpEssentialsRoleManager.invalidate(player.getUUID());
        List<String> granted = RpPerms.granted(player);
        known.put(player.getUUID(), new State(new HashSet<>(granted), VanishManager.levelOf(player)));
        PacketDistributor.sendToPlayer(player, new SyncPermissionsPacket(granted));
    }

    public static void onLogout(UUID id) {
        waiting.remove(id);
        known.remove(id);
    }

    public static void clear() {
        waiting.clear();
        known.clear();
    }

    /** End of each tick. */
    public static void tick(MinecraftServer server) {
        if (waiting.isEmpty()) return;
        for (UUID id : new ArrayList<>(waiting)) {
            waiting.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            // Not told anything yet: still logging in.
            if (player == null || !known.containsKey(id)) continue;
            // Again: a check may have kept the old roles while LuckPerms was changing them.
            RpEssentialsRoleManager.invalidate(id);
            apply(server, player, false);
        }
    }

    /** {@code force}: the command tree is sent even when the nodes are the same. */
    static void apply(MinecraftServer server, ServerPlayer player, boolean force) {
        UUID id = player.getUUID();
        List<String> granted = RpPerms.granted(player);
        State now = new State(new HashSet<>(granted), VanishManager.levelOf(player));
        State old = known.put(id, now);
        boolean changed = !now.equals(old);
        if (force || changed) server.getCommands().sendCommands(player);
        if (!changed) return;
        PacketDistributor.sendToPlayer(player, new SyncPermissionsPacket(granted));
        // First refresh during the login: the rest is sent by the login itself.
        if (old == null) return;
        ProfessionSyncHelper.syncToPlayer(player);
        TabBlur.resendTo(id);
        SyncNametagDataPacket.broadcastForPlayer(player);
        if (old.opLevel() != now.opLevel() && VanishManager.anyVanished()) VanishManager.refreshAll(server);
        // A role that allowed them in while the server is closed may be gone.
        if (!now.nodes().containsAll(old.nodes())) RpEssentialsScheduleManager.enforceOnlineDelayed(server);
    }
}
