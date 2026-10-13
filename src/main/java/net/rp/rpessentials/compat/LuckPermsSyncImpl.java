package net.rp.rpessentials.compat;

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
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.RpEssentialsRoleManager;
import net.rp.rpessentials.RpEssentialsScheduleManager;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class LuckPermsSyncImpl {

    private static final String PREFIX = "rpessentials.";
    private static final String[] PERMS = {
            "isStaff", "tabWhitelist", "scheduleWhitelist", "professionWhitelist", "seeNicknames",
            "seeAll", "bypassWorldBorder", "spyMessages", "bypassAutoUnwhitelist", "bypassDeathRpWhitelist"
    };
    private static final Map<String, String> BY_KEY = new HashMap<>();
    static { for (String p : PERMS) BY_KEY.put(PREFIX + p.toLowerCase(), p); }

    private static final List<EventSubscription<?>> subs = new ArrayList<>();
    private static final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> recent = new ConcurrentHashMap<>();
    private static final AtomicBoolean scheduled = new AtomicBoolean(false);

    private LuckPermsSyncImpl() {}

    private static boolean enabled() {
        try { return RpEssentialsConfig.LP_SYNC_ENABLED.get(); }
        catch (IllegalStateException e) { return false; }
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
            server.execute(() -> {
                RpEssentialsRoleManager.invalidate(id);
                RpEssentialsPermissions.invalidateCache(id);
            });
            return;
        }
        if (!e.isGroup()) return;
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
        try {
            roles = new ArrayList<>(RpEssentialsConfig.ROLES.get());
            importAll = RpEssentialsConfig.LP_SYNC_IMPORT_ALL_GROUPS.get();
        } catch (IllegalStateException e) { return; }

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

        Component msg = Component.literal("§6[LuckPerms] §7Roles synced from LuckPerms: §f"
                + String.join("§7, §f", touched));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpEssentialsPermissions.isStaff(p)) p.sendSystemMessage(msg);
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
        if (parse(parts.length > 2 ? parts[2] : "").equals(perms)) return false;
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
        try { roles = RpEssentialsConfig.ROLES.get(); }
        catch (IllegalStateException e) { return; }

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
            if (granted(existing).equals(desired)) return;
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

    private static void write(Group g, Set<String> desired) {
        List<PermissionNode> current = new ArrayList<>(g.getNodes(NodeType.PERMISSION));
        for (String perm : PERMS) {
            String key = PREFIX + perm.toLowerCase();
            boolean want = desired.contains(perm);
            boolean has = false;
            for (PermissionNode n : current) {
                if (!n.getKey().equalsIgnoreCase(key) || !n.getContexts().isEmpty()) continue;
                if (want) {
                    if (n.getValue() && !n.hasExpiry()) has = true;
                    else g.data().remove(n);
                } else if (n.getValue()) {
                    g.data().remove(n);
                }
            }
            if (want && !has) g.data().add(Node.builder(key).value(true).build());
        }
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================
    private static Set<String> granted(Group g) {
        Set<String> out = new LinkedHashSet<>();
        for (PermissionNode n : g.getNodes(NodeType.PERMISSION)) {
            if (!n.getValue() || !n.getContexts().isEmpty()) continue;
            String perm = BY_KEY.get(n.getKey().toLowerCase());
            if (perm != null) out.add(perm);
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
        Set<String> out = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            String perm = BY_KEY.get(PREFIX + token.trim().toLowerCase());
            if (perm != null) out.add(perm);
        }
        return out;
    }

    private static String join(Set<String> perms) {
        StringBuilder sb = new StringBuilder();
        for (String p : PERMS) {
            if (!perms.contains(p)) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(p);
        }
        return sb.toString();
    }
}