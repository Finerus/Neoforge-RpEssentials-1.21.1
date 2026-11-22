package net.rp.rpessentials.compat;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.group.GroupCreateEvent;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class LuckPermsSyncImpl {

    /**
     * Role entries are permission nodes ("rpessentials.x", "rpessentials.x.*", "-rpessentials.x"): each one
     * is mirrored on the LuckPerms group of the role (a refusal as a node set to false), and the
     * rpessentials.* nodes of a group come back as the entries of its role.
     */
    private static final String PREFIX = net.rp.rpessentials.permission.RpPerms.PREFIX;

    private static final List<EventSubscription<?>> subs = new ArrayList<>();
    private static final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> recent = new ConcurrentHashMap<>();
    private static final AtomicBoolean scheduled = new AtomicBoolean(false);

    private LuckPermsSyncImpl() {}

    private static boolean enabled() {
        return Cfg.get(RpEssentialsConfig.LP_SYNC_ENABLED, false);
    }

    // =========================================================================
    // CYCLE DE VIE
    // =========================================================================
    static void start(MinecraftServer server) {
        stop();
        if (!enabled()) return;
        ModContainer container = ModList.get().getModContainerById(RpEssentials.MODID).orElse(null);
        if (container == null) return;
        LuckPerms lp = LuckPermsProvider.get();
        EventBus bus = lp.getEventBus();
        subs.add(bus.subscribe(container, NodeMutateEvent.class, e -> onNodeMutate(server, e)));
        subs.add(bus.subscribe(container, GroupCreateEvent.class, e -> mark(server, e.getGroup().getName())));
        lp.getGroupManager().loadAllGroups().thenRun(() -> server.execute(() -> reconcile(server)));
    }

    static void stop() {
        for (EventSubscription<?> s : subs) {
            try { s.close(); } catch (Exception ignored) {}
        }
        subs.clear();
        dirty.clear();
        recent.clear();
        scheduled.set(false);
    }

    private static void reconcile(MinecraftServer server) {
        if (!enabled()) return;
        LuckPerms lp = LuckPermsProvider.get();
        List<String> names = new ArrayList<>();
        for (Group g : lp.getGroupManager().getLoadedGroups()) names.add(g.getName().toLowerCase());
        importGroups(server, names, false);
        pushAll(true);
    }

    // =========================================================================
    // LUCKPERMS VERS MC
    // =========================================================================
    private static void onNodeMutate(MinecraftServer server, NodeMutateEvent e) {
        if (e.isUser()) {
            UUID id = ((User) e.getTarget()).getUniqueId();
            RpEssentials.invalidateMeta(id);
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online != null) net.rp.rpessentials.permission.RpPerms.refresh(online);
                else RpEssentialsRoleManager.invalidate(id);
            });
            return;
        }
        if (!e.isGroup()) return;
        RpEssentials.clearMetaCache();
        // Any group change can change the nodes or the roles of the players (inheritance, parents...).
        server.execute(() -> net.rp.rpessentials.permission.RpPerms.refreshAll(server));
        if (!touchesRp(e.getDataBefore()) && !touchesRp(e.getDataAfter())) return;
        mark(server, ((Group) e.getTarget()).getName());
    }

    private static void mark(MinecraftServer server, String group) {
        String name = group.toLowerCase();
        recent.put(name, System.currentTimeMillis());
        dirty.add(name);
        if (!scheduled.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> server.execute(() -> {
            scheduled.set(false);
            List<String> names = new ArrayList<>(dirty);
            dirty.clear();
            importGroups(server, names, true);
        }), CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }

    private static void importGroups(MinecraftServer server, Collection<String> names, boolean eventDriven) {
        if (!enabled()) return;
        LuckPerms lp = LuckPermsProvider.get();
        List<String> roles;
        boolean importAll;
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            roles = new ArrayList<>(RpEssentialsConfig.ROLES.get());
            importAll = RpEssentialsConfig.LP_SYNC_IMPORT_ALL_GROUPS.get();
        } else { return; }

        List<String> touched = new ArrayList<>();
        for (String name : names) {
            Group g = lp.getGroupManager().getGroup(name);
            if (g != null && importOne(g, roles, importAll, eventDriven)) touched.add(name);
        }
        if (touched.isEmpty()) return;

        RpEssentialsConfig.ROLES.set(roles);
        RpEssentialsConfig.SPEC.save();
        RpEssentialsRoleManager.clearAll();
        RpEssentialsRoleManager.reload();
        RpEssentialsPermissions.clearCache();
        RpEssentialsScheduleManager.enforceOnlineDelayed(server);

        String roleList = String.join("§7, §f", touched);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpPerms.has(p, RpPerm.NOTIFY)) p.sendSystemMessage(Component.literal(
                    net.rp.rpessentials.lang.ServerLang.get(p, net.rp.rpessentials.config.MessagesConfig.LP_ROLES_SYNCED, "roles", roleList)));
        }
        RpEssentials.LOGGER.info("[LuckPerms] Roles synced from LuckPerms: {}", touched);
    }

    private static boolean importOne(Group g, List<String> roles, boolean importAll, boolean eventDriven) {
        String name = g.getName().toLowerCase();
        Set<String> perms = granted(g);
        int i = findRole(roles, name);

        if (i < 0) {
            if (!importAll && !hasRp(g)) return false;
            if (!name.matches("[a-z0-9_]+")) {
                RpEssentials.LOGGER.warn("[LuckPerms] Group '{}' skipped: invalid role id.", name);
                return false;
            }
            if (ProfessionRestrictionManager.getProfessionData(name) != null) {
                RpEssentials.LOGGER.warn("[LuckPerms] Group '{}' skipped: id already used by a profession.", name);
                return false;
            }
            roles.add(name + ";" + name + ";" + join(perms));
            return true;
        }

        if (!eventDriven && !hasRp(g)) return false;
        String[] parts = roles.get(i).split(";", 3);
        if (new HashSet<>(parse(parts.length > 2 ? parts[2] : "")).equals(new HashSet<>(perms))) return false;
        String id = parts[0].trim();
        String lpGroup = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : id;
        roles.set(i, id + ";" + lpGroup + ";" + join(perms));
        return true;
    }

    // =========================================================================
    // MC VERS LUCKPERMS
    // =========================================================================
    static void pushAll(boolean onlyMissing) {
        if (!enabled()) return;
        LuckPerms lp = LuckPermsProvider.get();
        List<? extends String> roles;
        if (Cfg.ready(RpEssentialsConfig.SPEC)) { roles = RpEssentialsConfig.ROLES.get(); } else { return; }

        long now = System.currentTimeMillis();
        for (String entry : roles) {
            String[] parts = entry.split(";", 3);
            String id = parts[0].trim().toLowerCase();
            if (id.isEmpty()) continue;
            String group = (parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : id).toLowerCase();

            Long last = recent.get(group);
            if (last != null && now - last < 5000L) continue;
            if (onlyMissing) {
                Group g = lp.getGroupManager().getGroup(group);
                if (g != null && hasRp(g)) continue;
            }
            push(group, parse(parts.length > 2 ? parts[2] : ""));
        }
    }

    static void pushRole(String lpGroup, Collection<String> perms) {
        if (!enabled() || lpGroup == null || lpGroup.isBlank()) return;
        push(lpGroup.trim().toLowerCase(), parse(String.join(",", perms)));
    }

    private static void push(String group, Set<String> desired) {
        LuckPerms lp = LuckPermsProvider.get();
        GroupManager gm = lp.getGroupManager();
        Group existing = gm.getGroup(group);
        if (existing != null) {
            if (new HashSet<>(granted(existing)).equals(new HashSet<>(desired))) return;
            gm.modifyGroup(group, g -> write(g, desired)).exceptionally(ex -> fail(group, ex));
            return;
        }
        gm.createAndLoadGroup(group)
                .thenCompose(g -> gm.modifyGroup(group, x -> write(x, desired)))
                .exceptionally(ex -> fail(group, ex));
    }

    private static Void fail(String group, Throwable ex) {
        RpEssentials.LOGGER.warn("[LuckPerms] Could not sync group '{}': {}", group, ex.getMessage());
        return null;
    }

    /** Replaces the rpessentials.* nodes (without context) of the group by the role entries. */
    private static void write(Group g, Set<String> desired) {
        Map<String, Boolean> want = new HashMap<>();
        for (String e : desired) {
            boolean neg = e.startsWith("-");
            want.put((neg ? e.substring(1) : e).toLowerCase(Locale.ROOT), !neg);
        }
        Set<String> kept = new HashSet<>();
        for (PermissionNode n : new ArrayList<>(g.getNodes(NodeType.PERMISSION))) {
            String key = n.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith(PREFIX) || !n.getContexts().isEmpty()) continue;
            Boolean value = want.get(key);
            if (value != null && value == n.getValue() && !n.hasExpiry() && kept.add(key)) continue;
            g.data().remove(n);
        }
        for (Map.Entry<String, Boolean> w : want.entrySet()) {
            if (!kept.contains(w.getKey())) g.data().add(Node.builder(w.getKey()).value(w.getValue()).build());
        }
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================
    /** rpessentials.* nodes of the group (without context) as role entries; unknown nodes are ignored. */
    private static Set<String> granted(Group g) {
        Set<String> out = new LinkedHashSet<>();
        for (PermissionNode n : g.getNodes(NodeType.PERMISSION)) {
            if (!n.getContexts().isEmpty() || !n.getKey().toLowerCase(Locale.ROOT).startsWith(PREFIX)) continue;
            String e = RpEssentialsRoleManager.normalize((n.getValue() ? "" : "-") + n.getKey());
            if (e != null) out.add(e);
        }
        return out;
    }

    private static boolean hasRp(Group g) {
        return touchesRp(g.getNodes(NodeType.PERMISSION));
    }

    private static boolean touchesRp(Collection<? extends Node> nodes) {
        for (Node n : nodes) {
            if (n.getKey().toLowerCase().startsWith(PREFIX)) return true;
        }
        return false;
    }

    private static int findRole(List<String> roles, String group) {
        for (int i = 0; i < roles.size(); i++) {
            String[] parts = roles.get(i).split(";", 3);
            String lp = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : parts[0].trim();
            if (lp.equalsIgnoreCase(group)) return i;
        }
        return -1;
    }

    private static Set<String> parse(String csv) {
        return RpEssentialsRoleManager.parseEntries(csv);
    }

    private static String join(Set<String> perms) {
        return String.join(",", perms);
    }
}