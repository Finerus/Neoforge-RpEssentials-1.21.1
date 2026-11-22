package net.rp.rpessentials.gameplay;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message shown once per player at login: a welcome message on the first launch of the mod on a server
 * (every player, until a staff member logs in), or a short list of what changed after an update
 * (staff members, config.gui permission). Texts are translated by the client language (assets lang files).
 * The last version the server ran with is saved in the mod state (see {@link AfkPlatformInitializer.ModState}).
 */
public final class ModNotices {

    private ModNotices() {}

    /**
     * Versions that have a note (lang key {@code rpessentials.notice.version.<version>}), oldest first.
     * Add the new version here with its note in en_us / fr_fr.
     */
    private static final List<String> VERSIONS_WITH_NOTES = List.of("6.1.0");

    public static String currentVersion() {
        return ModList.get().getModContainerById(RpEssentials.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("0");
    }

    /** At server start: compares the saved version with the running one and prepares the notice. */
    public static void onServerStarted(MinecraftServer server) {
        AfkPlatformInitializer.ModState state = AfkPlatformInitializer.state(server);
        String current = currentVersion();
        String last = state.lastModVersion();
        ROLE_HINT_SHOWN.clear();
        if (last.equals(current)) return;
        if (last.isEmpty()) {
            // Before 6.1.0 the version was not saved: a world where the mod already ran gets the update notes.
            String from = state.usedBefore() ? "?" : "";
            state.startNotice(from, current);
            RpEssentials.LOGGER.info(from.isEmpty()
                    ? "[RpEssentials] First launch of RpEssentials {} on this server."
                    : "[RpEssentials] RpEssentials updated to {}.", current);
        } else {
            state.startNotice(last, current);
            RpEssentials.LOGGER.info("[RpEssentials] RpEssentials updated from {} to {}.", last, current);
        }
        state.setLastModVersion(current);
    }

    /**
     * At login: shows the notice once to each player concerned.
     * Welcome (first launch): every player, since nobody is OP yet on a new server; it stops once a staff
     * member (config.gui) has logged in. Update notes: staff members only.
     */
    public static void onLogin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        roleHint(player);
        AfkPlatformInitializer.ModState state = AfkPlatformInitializer.state(server);
        String to = state.noticeTo();
        if (to.isEmpty()) return;
        boolean welcome = state.noticeFrom().isEmpty();
        boolean staff = RpPerms.has(player, RpPerm.CONFIG_GUI);
        if (!welcome && !staff) return;
        if (!state.noticeSeenBy(player.getUUID())) {
            for (Component line : lines(state.noticeFrom(), to)) player.sendSystemMessage(line);
            state.markNoticeSeen(player.getUUID());
        }
        // The server has its staff now: the welcome is not shown to anyone else.
        if (welcome && staff) state.startNotice("", "");
    }

    /** Players who got the role hint since the server started. */
    private static final Set<UUID> ROLE_HINT_SHOWN = ConcurrentHashMap.newKeySet();

    /**
     * An OP can give roles (roles.set) but gets the RpEssentials staff permissions from a role only: on a new
     * server the first OP has none and nothing told how to get them. Shown at login (once per server start)
     * to a player who can give roles but has no staff permission, with the command to click.
     */
    private static void roleHint(ServerPlayer player) {
        if (RpPerms.has(player, RpPerm.CONFIG_GUI) || !RpPerms.has(player, RpPerm.ROLES_SET)) return;
        if (!ROLE_HINT_SHOWN.add(player.getUUID())) return;
        String cmd = "/rpessentials setrole " + player.getGameProfile().getName() + " " + adminRole();
        player.sendSystemMessage(Component.literal("§6[RpEssentials] ")
                .append(Component.translatable("rpessentials.notice.role_hint")));
        player.sendSystemMessage(Component.literal("§e" + cmd).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("rpessentials.notice.role_hint_click")))));
    }

    /** First role that grants every permission (rpessentials.*), "admin" with the default config. */
    private static String adminRole() {
        if (net.rp.rpessentials.config.Cfg.ready(net.rp.rpessentials.config.RpEssentialsConfig.SPEC)) {
            for (String line : net.rp.rpessentials.config.RpEssentialsConfig.ROLES.get()) {
                String[] p = line.split(";", 3);
                if (p.length > 2 && p[2].contains("rpessentials.*")) return p[0].trim();
            }
        }
        return "admin";
    }

    static List<Component> lines(String from, String to) {
        List<Component> out = new ArrayList<>();
        MutableComponent prefix = Component.literal("§6[RpEssentials] ");
        if (from.isEmpty()) {
            out.add(prefix.copy().append(Component.translatable("rpessentials.notice.welcome", to)));
            out.add(Component.translatable("rpessentials.notice.welcome_hint"));
            return out;
        }
        List<String> notes = new ArrayList<>();
        for (String v : VERSIONS_WITH_NOTES) {
            if ((from.equals("?") || compare(v, from) > 0) && compare(v, to) <= 0) notes.add(v);
        }
        out.add(prefix.copy().append(from.equals("?")
                ? Component.translatable("rpessentials.notice.updated_unknown", to)
                : Component.translatable("rpessentials.notice.updated", from, to)));
        for (String v : notes) {
            out.add(Component.literal("§7" + v + " §8» ").append(Component.translatable("rpessentials.notice.version." + v)));
        }
        out.add(Component.translatable("rpessentials.notice.changelog"));
        return out;
    }

    /** Compares two versions number by number (6.1.0 &lt; 6.10.0); text after '-' or '+' is ignored. */
    static int compare(String a, String b) {
        String[] x = a.split("[-+]")[0].split("\\.");
        String[] y = b.split("[-+]")[0].split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int u = i < x.length ? parse(x[i]) : 0;
            int v = i < y.length ? parse(y[i]) : 0;
            if (u != v) return Integer.compare(u, v);
        }
        return 0;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (NumberFormatException e) { return 0; }
    }
}
