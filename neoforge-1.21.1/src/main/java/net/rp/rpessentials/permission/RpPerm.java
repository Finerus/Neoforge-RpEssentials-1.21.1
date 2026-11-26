package net.rp.rpessentials.permission;

import net.rp.rpessentials.moderation.ManageService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every permission node of RpEssentials ({@code rpessentials.<key>}), registered in the NeoForge
 * permission API. Without an explicit value (role of rpessentials-core.toml, or LuckPerms), a node
 * falls back on its default:
 * <ul>
 *   <li>{@code staffDefault}: granted to staff members (node {@code rpessentials.staff});</li>
 *   <li>{@code opLevel}: granted from this OP level ({@link #EVERYONE} = every player,
 *       {@link #SENSITIVE} = the sensitive level of the config, {@link #NEVER} = no OP level).</li>
 * </ul>
 * These defaults reproduce the behavior of the versions before the nodes existed.
 * <p>
 * Nodes only cover RpEssentials commands and features: vanilla commands (/kick, /ban, /op...) keep
 * their own vanilla OP checks.
 */
public final class RpPerm {

    public static final int EVERYONE = 0;
    public static final int SENSITIVE = -1;
    public static final int NEVER = 5;

    /** Groups shown in the role editor, in this order. */
    public enum Category { GENERAL, PLAYER, IDENTITY, BYPASS, MODERATION, MANAGE, STAFF_TOOLS, PROFESSIONS, SCHEDULE, ADMIN, NOTIFY }

    private static final Map<String, RpPerm> BY_KEY = new LinkedHashMap<>();
    private static final List<RpPerm> ALL = new ArrayList<>();

    private final String key;
    private final Category category;
    private final boolean staffDefault;
    private final int opLevel;
    private final String legacy;

    private RpPerm(String key, Category category, boolean staffDefault, int opLevel, String legacy) {
        this.key = key;
        this.category = category;
        this.staffDefault = staffDefault;
        this.opLevel = opLevel;
        this.legacy = legacy;
        BY_KEY.put(key, this);
        ALL.add(this);
    }

    private static RpPerm player(String key) { return new RpPerm(key, Category.PLAYER, false, EVERYONE, null); }
    private static RpPerm staff(String key, Category c) { return new RpPerm(key, c, true, NEVER, null); }
    private static RpPerm op(String key, Category c, int level) { return new RpPerm(key, c, false, level, null); }
    private static RpPerm flag(String key, Category c, boolean staffDefault, String legacy) {
        return new RpPerm(key, c, staffDefault, NEVER, legacy);
    }

    // ── General ──────────────────────────────────────────────────────────────
    /** Staff member: staff commands by default, staff notifications, schedule bypass... */
    public static final RpPerm STAFF = flag("staff", Category.GENERAL, false, "isStaff");

    // ── Player commands (everyone by default) ────────────────────────────────
    public static final RpPerm PLAYER_ROLL = player("player.roll");
    public static final RpPerm PLAYER_REPORT = player("player.report");
    public static final RpPerm PLAYER_MYWARN = player("player.mywarn");
    public static final RpPerm PLAYER_MYPROFESSION = player("player.myprofession");
    public static final RpPerm PLAYER_RP = player("player.rp");
    public static final RpPerm PLAYER_SELFNICK = player("player.selfnick");
    public static final RpPerm PLAYER_SPAWN = player("player.spawn");
    public static final RpPerm PLAYER_MSG = player("player.msg");

    // ── Identity & chat ──────────────────────────────────────────────────────
    public static final RpPerm SEE_NICKNAMES = flag("see.nicknames", Category.IDENTITY, false, "seeNicknames");
    public static final RpPerm SEE_ALL = flag("see.all", Category.IDENTITY, false, "seeAll");
    public static final RpPerm SEE_REALNAMES = staff("see.realnames", Category.IDENTITY);
    public static final RpPerm TAB_ALWAYS_VISIBLE = flag("tab.alwaysvisible", Category.IDENTITY, false, "tabWhitelist");
    public static final RpPerm CHAT_SPY = flag("chat.spy", Category.IDENTITY, false, "spyMessages");
    public static final RpPerm CHAT_CODES = staff("chat.codes", Category.IDENTITY);
    public static final RpPerm CHAT_ANNOUNCE = staff("chat.announce", Category.IDENTITY);
    public static final RpPerm NICK_OTHERS = op("identity.nick", Category.IDENTITY, 2);
    public static final RpPerm WHOIS = op("identity.whois", Category.IDENTITY, 2);
    public static final RpPerm SHEET = staff("identity.sheet", Category.IDENTITY);

    // ── Bypasses ─────────────────────────────────────────────────────────────
    public static final RpPerm BYPASS_SCHEDULE = flag("bypass.schedule", Category.BYPASS, true, "scheduleWhitelist");
    public static final RpPerm BYPASS_PROFESSION = flag("bypass.profession", Category.BYPASS, false, "professionWhitelist");
    public static final RpPerm BYPASS_WORLDBORDER = flag("bypass.worldborder", Category.BYPASS, false, "bypassWorldBorder");
    public static final RpPerm BYPASS_AUTOUNWHITELIST = flag("bypass.autounwhitelist", Category.BYPASS, false, "bypassAutoUnwhitelist");
    public static final RpPerm BYPASS_DEATHRP_WHITELIST = flag("bypass.deathrpwhitelist", Category.BYPASS, false, "bypassDeathRpWhitelist");

    // ── Moderation ───────────────────────────────────────────────────────────
    public static final RpPerm PROFILE = staff("moderation.profile", Category.MODERATION);
    public static final RpPerm WARN = staff("moderation.warn", Category.MODERATION);
    public static final RpPerm MUTE = staff("moderation.mute", Category.MODERATION);
    public static final RpPerm NOTE = staff("moderation.note", Category.MODERATION);
    public static final RpPerm INSPECT = staff("moderation.inspect", Category.MODERATION);
    public static final RpPerm ABSENCE = staff("moderation.absence", Category.MODERATION);
    public static final RpPerm HISTORY = staff("moderation.history", Category.MODERATION);
    public static final RpPerm STATS = staff("moderation.stats", Category.MODERATION);
    public static final RpPerm LASTCONNECTION = staff("moderation.lastconnection", Category.MODERATION);
    public static final RpPerm REPORTS = staff("moderation.reports", Category.MODERATION);
    public static final RpPerm VANISH = staff("moderation.vanish", Category.MODERATION);

    // ── Manage actions (/rpessentials manage and the Manage tab), one per action ─
    private static final Map<ManageService.Action, RpPerm> MANAGE = new EnumMap<>(ManageService.Action.class);
    static {
        for (ManageService.Action a : ManageService.Action.values()) {
            String key = "manage." + a.name().toLowerCase(Locale.ROOT).replace("_", "");
            MANAGE.put(a, a.sensitive() ? op(key, Category.MANAGE, SENSITIVE) : staff(key, Category.MANAGE));
        }
    }

    // ── Staff tools ──────────────────────────────────────────────────────────
    public static final RpPerm STAFF_GAMEMODE = staff("staff.gamemode", Category.STAFF_TOOLS);
    public static final RpPerm STAFF_TP = staff("staff.tp", Category.STAFF_TOOLS);
    public static final RpPerm STAFF_EFFECT = staff("staff.effect", Category.STAFF_TOOLS);
    public static final RpPerm STAFF_PLATFORM = staff("staff.platform", Category.STAFF_TOOLS);
    public static final RpPerm STAFF_SETPLATFORM = op("staff.setplatform", Category.STAFF_TOOLS, 2);
    public static final RpPerm STAFF_BROADCAST = staff("staff.broadcast", Category.STAFF_TOOLS);
    public static final RpPerm STAFF_PROXIMITYSPY = staff("staff.proximityspy", Category.STAFF_TOOLS);
    public static final RpPerm ZONE = staff("world.zone", Category.STAFF_TOOLS);

    // ── Professions & licenses ───────────────────────────────────────────────
    public static final RpPerm PROFESSIONS_EDIT = staff("professions.edit", Category.PROFESSIONS);
    public static final RpPerm LICENSE_GIVE = op("license.give", Category.PROFESSIONS, 2);
    public static final RpPerm LICENSE_TEMP = op("license.temp", Category.PROFESSIONS, 2);
    public static final RpPerm LICENSE_REVOKE = op("license.revoke", Category.PROFESSIONS, 2);
    public static final RpPerm LICENSE_REISSUE = op("license.reissue", Category.PROFESSIONS, 2);
    public static final RpPerm LICENSE_LIST = op("license.list", Category.PROFESSIONS, 2);
    public static final RpPerm LICENSE_AUDIT = op("license.audit", Category.PROFESSIONS, 2);

    // ── Schedule & death RP ──────────────────────────────────────────────────
    public static final RpPerm SCHEDULE_FORCE = op("schedule.force", Category.SCHEDULE, 2);
    public static final RpPerm SCHEDULE_WHITELIST = op("schedule.whitelist", Category.SCHEDULE, 2);
    public static final RpPerm DEATHRP = op("deathrp.manage", Category.SCHEDULE, 2);

    // ── Administration ───────────────────────────────────────────────────────
    public static final RpPerm CONFIG = op("config.manage", Category.ADMIN, 2);
    public static final RpPerm CONFIG_GUI = staff("config.gui", Category.ADMIN);
    public static final RpPerm CONFIG_SENSITIVE = op("config.sensitive", Category.ADMIN, SENSITIVE);
    public static final RpPerm BLURTAB = op("config.blurtab", Category.ADMIN, 2);
    public static final RpPerm ROLES_SET = op("roles.set", Category.ADMIN, 3);
    public static final RpPerm ROLES_MANAGE = op("roles.manage", Category.ADMIN, SENSITIVE);
    public static final RpPerm DISCORD = staff("admin.discord", Category.ADMIN);

    // ── Notifications ────────────────────────────────────────────────────────
    public static final RpPerm NOTIFY = staff("notify.general", Category.NOTIFY);
    public static final RpPerm NOTIFY_REPORTS = staff("notify.reports", Category.NOTIFY);
    public static final RpPerm NOTIFY_DICE = staff("notify.dice", Category.NOTIFY);

    // =========================================================================
    public String key() { return key; }

    /** Full node: rpessentials.key */
    public String node() { return RpPerms.PREFIX + key; }

    public Category category() { return category; }

    public boolean staffDefault() { return staffDefault; }

    public int opLevel() { return opLevel; }

    /** Name of the old role permission (before nodes), or null. */
    public String legacy() { return legacy; }

    public static List<RpPerm> all() { return Collections.unmodifiableList(ALL); }

    public static RpPerm byKey(String key) { return BY_KEY.get(key); }

    public static RpPerm manage(ManageService.Action action) { return MANAGE.get(action); }

    /**
     * Nodes that {@code rpessentials.*} does not give: they change how a player is seen instead of what they may do.
     * An admin must not be shown clearly to every player (name blur) only because they have every permission.
     */
    public static boolean explicitOnly(String node) {
        return TAB_ALWAYS_VISIBLE.node().equals(node);
    }

    /** Old role permission name (isStaff, seeAll...) to its node, or null. */
    public static RpPerm byLegacy(String name) {
        for (RpPerm p : ALL) if (p.legacy != null && p.legacy.equalsIgnoreCase(name)) return p;
        return null;
    }

    @Override
    public String toString() { return node(); }
}
