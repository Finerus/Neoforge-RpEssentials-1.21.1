package net.rp.rpessentials.api;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.MuteManager;
import net.rp.rpessentials.moderation.PlaytimeManager;
import net.rp.rpessentials.moderation.VanishManager;
import net.rp.rpessentials.moderation.WarnManager;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text variables of RpEssentials, usable by any mod (scoreboards, TAB lists, chat, HUDs):
 * {@code %rpessentials:<name>%}. Other mods can add their own with {@link #register}.
 * <p>
 * Built in: nick, name, display (nick or name), role, role_group (LuckPerms group of the role), vanished, vanish_level,
 * professions, professions_ids, profession_count, warns, muted, playtime.
 */
public final class RpPlaceholders {

    private static final Pattern TOKEN = Pattern.compile("%rpessentials:([a-z0-9_]+)%");
    private static final Map<String, Function<ServerPlayer, String>> PLACEHOLDERS = new LinkedHashMap<>();

    static {
        register("nick", p -> {
            String n = NicknameManager.getNickname(p.getUUID());
            return n != null ? n : "";
        });
        register("name", p -> p.getGameProfile().getName());
        register("display", NicknameManager::getDisplayName);
        register("role", RpPlaceholders::roleId);
        register("role_group", RpPlaceholders::roleGroup);
        register("vanished", p -> String.valueOf(VanishManager.isVanished(p.getUUID())));
        register("vanish_level", p -> VanishManager.isVanished(p.getUUID()) ? String.valueOf(VanishManager.getLevel(p.getUUID())) : "0");
        register("professions", p -> String.join(", ", professionNames(p)));
        register("professions_ids", p -> String.join(", ", LicenseManager.getLicenses(p.getUUID())));
        register("profession_count", p -> String.valueOf(LicenseManager.getLicenses(p.getUUID()).size()));
        register("warns", p -> String.valueOf(WarnManager.countActiveWarns(p.getUUID())));
        register("muted", p -> String.valueOf(MuteManager.isMuted(p.getUUID())));
        register("playtime", p -> PlaytimeManager.format(p, PlaytimeManager.getTotalPlaytimeMs(p.getUUID())));
    }

    private RpPlaceholders() {}

    /** Adds (or replaces) a variable {@code %rpessentials:<name>%}. Name: lowercase letters, digits and _. */
    public static synchronized void register(String name, Function<ServerPlayer, String> value) {
        PLACEHOLDERS.put(name.toLowerCase(Locale.ROOT), value);
    }

    /** Names of every registered variable. */
    public static synchronized Set<String> names() {
        return Set.copyOf(PLACEHOLDERS.keySet());
    }

    /** Value of one variable for a player, or null when the variable does not exist. */
    public static String get(ServerPlayer player, String name) {
        Function<ServerPlayer, String> f;
        synchronized (RpPlaceholders.class) {
            f = PLACEHOLDERS.get(name.toLowerCase(Locale.ROOT));
        }
        if (f == null || player == null) return null;
        try {
            String v = f.apply(player);
            return v != null ? v : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Replaces every {@code %rpessentials:<name>%} of a text. Unknown variables are left as they are. */
    public static String resolve(ServerPlayer player, String text) {
        if (text == null || text.indexOf('%') < 0) return text;
        Matcher m = TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = get(player, m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v != null ? v : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Id of the role of a player (the first role of the config the player has), "" if none. */
    public static String roleId(ServerPlayer player) {
        String[] line = roleLine(player);
        return line != null ? line[0].trim() : "";
    }

    /** LuckPerms group linked to the role of a player, "" if none. */
    public static String roleGroup(ServerPlayer player) {
        String[] line = roleLine(player);
        return line != null && line.length > 1 ? line[1].trim() : "";
    }

    private static String[] roleLine(ServerPlayer player) {
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String[] parts = entry.split(";", 3);
                if (!parts[0].isBlank() && player.getTags().contains(parts[0].trim())) return parts;
            }
        }
        return null;
    }

    /** Display names of the professions of a player. */
    public static List<String> professionNames(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (String id : LicenseManager.getLicenses(player.getUUID())) {
            ProfessionRestrictionManager.ProfessionData d = ProfessionRestrictionManager.getProfessionData(id);
            out.add(d != null ? d.getFormattedName() : id);
        }
        return out;
    }
}
