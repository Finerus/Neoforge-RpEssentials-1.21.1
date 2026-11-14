package net.rp.rpessentials.moderation;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.util.TimeHelper;
import net.rp.rpessentials.api.RpPlaceholders;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Extra lines of the Stats tab of the Player Profile GUI, built on the server
 * (where the data lives) in the language of the staff member who looks.
 */
public final class PlayerStatsLines {

    private static final String DATE = "dd/MM/yyyy HH:mm";

    private PlayerStatsLines() {}

    public static List<String> build(MinecraftServer server, ServerPlayer v, UUID id) {
        List<String> out = new ArrayList<>();
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        GameProfile profile = ManageService.resolveProfile(server, id);

        // ── Activity ─────────────────────────────────────────────────────────
        out.add(ServerLang.get(v, MessagesConfig.STATS_SECTION_ACTIVITY));
        LastConnectionManager.ConnectionEntry ce = LastConnectionManager.getEntry(id);
        // Recorded since 5.1.0; for a player not seen since, the save file date is the best guess.
        long first = ce != null && ce.firstLoginMs > 0 ? ce.firstLoginMs : firstJoin(server, id);
        out.add(ServerLang.get(v, MessagesConfig.STATS_FIRST_JOIN, "date", date(v, first), "ago", ago(v, first)));
        long lastLogin = ce != null ? ce.lastLoginMs : 0L;
        out.add(ServerLang.get(v, MessagesConfig.STATS_LAST_LOGIN, "date", date(v, lastLogin), "ago", ago(v, lastLogin)));
        String logout = online != null ? ServerLang.get(v, MessagesConfig.STATS_ONLINE_NOW)
                : ce != null && ce.lastLogout != null ? "§f" + ce.lastLogout : ServerLang.get(v, MessagesConfig.STATS_UNKNOWN);
        out.add(ServerLang.get(v, MessagesConfig.STATS_LAST_LOGOUT, "date", logout));
        if (ce != null) {
            out.add(ServerLang.get(v, MessagesConfig.STATS_LOGINS, "total", String.valueOf(ce.loginCount),
                    "d7", String.valueOf(ce.loginsLastDays(7)), "d30", String.valueOf(ce.loginsLastDays(30))));
            long session = PlaytimeManager.getCurrentSessionMs(id);
            out.add(ServerLang.get(v, MessagesConfig.STATS_PLAYTIME_RECENT,
                    "d7", PlaytimeManager.format(v, ce.playtimeLastDays(7) + session),
                    "d30", PlaytimeManager.format(v, ce.playtimeLastDays(30) + session)));
        }
        out.add("");

        // ── Discipline ───────────────────────────────────────────────────────
        out.add(ServerLang.get(v, MessagesConfig.STATS_SECTION_DISCIPLINE));
        out.add(ServerLang.get(v, MessagesConfig.STATS_WARNS_TOTAL,
                "total", String.valueOf(WarnManager.getWarns(id).size()),
                "active", String.valueOf(WarnManager.countActiveWarns(id))));
        long mutes = HistoryManager.get(id, v).stream().filter(e -> HistoryManager.MUTE.equals(e.type)).count();
        out.add(ServerLang.get(v, MessagesConfig.STATS_MUTES_TOTAL, "count", String.valueOf(mutes)));
        out.add("");

        // ── Roleplay ─────────────────────────────────────────────────────────
        out.add(ServerLang.get(v, MessagesConfig.STATS_SECTION_RP));
        AbsenceManager.AbsenceEntry abs = null;
        for (AbsenceManager.AbsenceEntry a : AbsenceManager.getAll()) {
            if (id.toString().equals(a.uuid) && !a.isExpired()) abs = a;
        }
        out.add(abs == null ? ServerLang.get(v, MessagesConfig.STATS_ABSENCE_NONE)
                : ServerLang.get(v, MessagesConfig.STATS_ABSENCE,
                        "reason", abs.reason == null || abs.reason.isBlank() ? "-" : abs.reason,
                        "date", TimeHelper.format(DATE, abs.endsAt)));
        boolean anyTemp = false;
        for (LicenseManager.TempLicenseEntry t : LicenseManager.getAllTempLicenses()) {
            if (!id.toString().equals(t.targetUUID)) continue;
            anyTemp = true;
            ProfessionRestrictionManager.ProfessionData d = ProfessionRestrictionManager.getProfessionData(t.profession);
            out.add(ServerLang.get(v, MessagesConfig.STATS_TEMP_LICENSE,
                    "profession", d != null ? d.getFormattedName() : t.profession, "date", String.valueOf(t.expiresAt)));
        }
        if (!anyTemp) out.add(ServerLang.get(v, MessagesConfig.STATS_TEMP_NONE));
        List<String> nicks = new ArrayList<>();
        for (HistoryManager.Entry e : HistoryManager.get(id, v)) {
            if (!HistoryManager.NICK.equals(e.type)) continue;
            int arrow = e.detail.indexOf("→ ");
            String n = arrow >= 0 ? e.detail.substring(arrow + 2) : e.detail;
            n = n.replace("§f", "").trim();
            if (!n.isEmpty() && !n.equals("-") && !nicks.contains(n)) nicks.add(n);
            if (nicks.size() >= 6) break;
        }
        out.add(nicks.isEmpty() ? ServerLang.get(v, MessagesConfig.STATS_NICKS_NONE)
                : ServerLang.get(v, MessagesConfig.STATS_NICKS, "list", String.join("§7, §r", nicks)));
        out.add(ServerLang.get(v, MessagesConfig.STATS_DEATHRP,
                "state", ServerLang.get(v, DeathRPManager.isDeathRPEnabled(id) ? MessagesConfig.STATS_ENABLED : MessagesConfig.STATS_DISABLED),
                "count", String.valueOf(DeathRPManager.getHistory(id).size())));
        out.add("");

        // ── Access ───────────────────────────────────────────────────────────
        out.add(ServerLang.get(v, MessagesConfig.STATS_SECTION_ACCESS));
        out.add(ServerLang.get(v, MessagesConfig.STATS_WHITELIST, "value",
                ServerLang.get(v, server.getPlayerList().isWhiteListed(profile) ? MessagesConfig.STATS_YES : MessagesConfig.STATS_NO)));
        out.add(ServerLang.get(v, MessagesConfig.STATS_OP, "level", String.valueOf(server.getProfilePermissions(profile))));
        if (online != null) {
            String role = RpPlaceholders.roleId(online);
            String group = RpPlaceholders.roleGroup(online);
            out.add(ServerLang.get(v, MessagesConfig.STATS_ROLE,
                    "role", role.isEmpty() ? "-" : role, "group", group.isEmpty() ? "-" : group));
        } else {
            out.add(ServerLang.get(v, MessagesConfig.STATS_ROLE_OFFLINE));
        }
        out.add(ServerLang.get(v, MessagesConfig.STATS_VANISH, "value", VanishManager.isVanished(id)
                ? ServerLang.get(v, MessagesConfig.STATS_VANISH_LEVEL, "level", String.valueOf(VanishManager.getLevel(id)))
                : ServerLang.get(v, MessagesConfig.STATS_VISIBLE)));
        return out;
    }

    /** Creation time of the player's save file, 0 when unknown. */
    private static long firstJoin(MinecraftServer server, UUID id) {
        try {
            Path p = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
            if (!Files.exists(p)) return 0L;
            return Files.readAttributes(p, BasicFileAttributes.class).creationTime().toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String date(ServerPlayer v, long ms) {
        return ms <= 0 ? ServerLang.get(v, MessagesConfig.STATS_UNKNOWN) : "§f" + TimeHelper.format(DATE, ms);
    }

    /** "3 days ago", "5 h ago"... in the viewer's language. */
    private static String ago(ServerPlayer v, long ms) {
        if (ms <= 0) return "-";
        long min = Math.max(0, (System.currentTimeMillis() - ms) / 60_000L);
        if (min < 1) return ServerLang.get(v, MessagesConfig.STATS_AGO_NOW);
        if (min < 60) return ServerLang.get(v, MessagesConfig.STATS_AGO_MINUTES, "n", String.valueOf(min));
        if (min < 1440) return ServerLang.get(v, MessagesConfig.STATS_AGO_HOURS, "n", String.valueOf(min / 60));
        return ServerLang.get(v, MessagesConfig.STATS_AGO_DAYS, "n", String.valueOf(min / 1440));
    }
}
