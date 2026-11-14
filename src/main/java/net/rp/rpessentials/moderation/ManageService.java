package net.rp.rpessentials.moderation;

import net.rp.rpessentials.config.Cfg;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.config.MessagesConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.config.RpConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.profession.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.commands.RpEssentialsCommands;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.network.SyncNametagDataPacket;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ColorHelper;

public final class ManageService {

    public enum Action {
        WHITELIST_ADD, WHITELIST_REMOVE, KICK, BAN, TEMPBAN, PARDON, OP, DEOP,
        CLEAR_INVENTORY, CLEAR_ENDER, ABSENCE_ADD, ABSENCE_REMOVE,
        CLEAR_NICKNAME, CLEAR_ROLE, CLEAR_PROFESSION, RESET_ALL,
        TP_TO_PLAYER, TP_PLAYER_HERE, TP_PLAYER_PLATFORM, TP_PLAYER_AFK,
        VANISH, UNVANISH;

        public boolean sensitive() {
            return this == BAN || this == TEMPBAN || this == PARDON || this == OP || this == DEOP
                    || this == CLEAR_ROLE || this == RESET_ALL;
        }

        public boolean needsOnline() {
            return this == KICK || this == TP_TO_PLAYER || this == TP_PLAYER_HERE
                    || this == TP_PLAYER_PLATFORM || this == TP_PLAYER_AFK
                    || this == VANISH || this == UNVANISH;
        }

        public boolean queueable() {
            return this == CLEAR_INVENTORY || this == CLEAR_ENDER || this == CLEAR_ROLE;
        }

        public boolean selfBlocked() {
            return this == KICK || this == BAN || this == TEMPBAN || this == DEOP || this == RESET_ALL
                    || this == TP_TO_PLAYER || this == TP_PLAYER_HERE;
        }
    }

    private static boolean bulk = false;

    private static final Action[] RESET_STEPS = {
            Action.CLEAR_INVENTORY, Action.CLEAR_ENDER, Action.CLEAR_PROFESSION, Action.CLEAR_ROLE,
            Action.CLEAR_NICKNAME, Action.ABSENCE_REMOVE, Action.DEOP, Action.WHITELIST_REMOVE
    };

    public record Result(boolean ok, String message) {}

    private ManageService() {}

    private static Result ok(ServerPlayer v, ModConfigSpec.ConfigValue<String> cv, String... a) {
        return new Result(true, ServerLang.get(v, cv, a));
    }

    private static Result fail(ServerPlayer v, ModConfigSpec.ConfigValue<String> cv, String... a) {
        return new Result(false, ServerLang.get(v, cv, a));
    }

    /**
     * Parses a duration such as "3d", "12h", "30m", "2w" or "1d12h" into minutes.
     * A plain number is read as days. Returns -1 when the text is not a duration.
     */
    public static int parseDurationMinutes(String s) {
        if (s == null || s.isBlank()) return -1;
        s = s.trim().toLowerCase();
        if (s.matches("\\d+")) {
            long d = Long.parseLong(s) * 1440L;
            return d > 0 && d <= Integer.MAX_VALUE ? (int) d : -1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([wdhm])").matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "w" -> n * 10080L;
                case "d" -> n * 1440L;
                case "h" -> n * 60L;
                default -> n;
            };
            end = m.end();
        }
        if (end != s.length() || total <= 0 || total > Integer.MAX_VALUE) return -1;
        return (int) total;
    }

    public static GameProfile resolveProfile(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile();
        String name = PendingProfileManager.hasPending(uuid)
                ? PendingProfileManager.getMcName(uuid)
                : LastConnectionManager.resolveName(server, uuid);
        return new GameProfile(uuid, name);
    }

    public static GameProfile resolveByName(MinecraftServer server, String name) {
        UUID id = RpEssentialsCommands.findUUIDByName(server, name);
        return id != null ? resolveProfile(server, id) : null;
    }

    public static void setOp(MinecraftServer server, GameProfile profile, int level) {
        PlayerList pl = server.getPlayerList();
        pl.getOps().add(new ServerOpListEntry(profile, level, pl.getOps().canBypassPlayerLimit(profile)));
        ServerPlayer online = pl.getPlayer(profile.getId());
        if (online != null) pl.sendPlayerPermissionLevel(online);
    }

    public static Result execute(MinecraftServer server, ServerPlayer admin, UUID uuid,
                                 Action action, String text, int number) {
        String adminName = admin != null ? admin.getName().getString() : ServerLang.get(null, MessagesConfig.UI_CONSOLE);
        if (action.sensitive() && admin != null && !RpEssentialsPermissions.hasSensitiveLevel(admin)) {
            return fail(admin, MessagesConfig.MANAGE_NEED_SENSITIVE);
        }

        PlayerList pl = server.getPlayerList();
        GameProfile profile = resolveProfile(server, uuid);
        String name = profile.getName();
        ServerPlayer online = pl.getPlayer(uuid);
        String reason = text == null ? "" : text.trim();
        if (reason.length() > 256) reason = reason.substring(0, 256);
        boolean self = admin != null && admin.getUUID().equals(uuid);
        int adminLevel = admin != null ? server.getProfilePermissions(admin.getGameProfile()) : 4;
        final String finalReason = reason;
        int vanishBefore = VanishManager.isVanished(uuid) ? VanishManager.getLevel(uuid) : 0;

        Result result = switch (action) {
            case WHITELIST_ADD -> {
                pl.getWhiteList().add(new UserWhiteListEntry(profile));
                yield ok(admin, MessagesConfig.MANAGE_WL_ADDED, "player", name);
            }
            case WHITELIST_REMOVE -> {
                pl.getWhiteList().remove(new UserWhiteListEntry(profile));
                if (online != null && pl.isUsingWhitelist() && !pl.isWhiteListed(profile)) {
                    online.connection.disconnect(Component.translatable("multiplayer.disconnect.not_whitelisted"));
                }
                suggest(server, uuid, name, ManageSuggestions.Kind.UNWHITELISTED);
                yield ok(admin, MessagesConfig.MANAGE_WL_REMOVED, "player", name);
            }
            case KICK -> {
                if (self) yield fail(admin, MessagesConfig.MANAGE_KICK_SELF);
                if (online == null) yield fail(admin, MessagesConfig.MANAGE_NOT_ONLINE, "player", name);
                online.connection.disconnect(finalReason.isEmpty()
                        ? Component.translatable("multiplayer.disconnect.kicked")
                        : Component.literal(finalReason));
                yield ok(admin, MessagesConfig.MANAGE_KICKED, "player", name);
            }
            case BAN -> {
                if (self) yield fail(admin, MessagesConfig.MANAGE_BAN_SELF);
                if (pl.getBans().isBanned(profile)) yield fail(admin, MessagesConfig.MANAGE_ALREADY_BANNED, "player", name);
                pl.getBans().add(new UserBanListEntry(profile, null, adminName, null,
                        finalReason.isEmpty() ? null : finalReason));
                if (online != null) online.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
                suggest(server, uuid, name, ManageSuggestions.Kind.BANNED);
                yield ok(admin, MessagesConfig.MANAGE_BANNED, "player", name);
            }
            case TEMPBAN -> {
                // number = duration in minutes. Vanilla removes the entry by itself when it expires
                // and shows the end date to the player on the login screen.
                if (self) yield fail(admin, MessagesConfig.MANAGE_BAN_SELF);
                if (number <= 0) yield fail(admin, MessagesConfig.MANAGE_DURATION_EXPECTED);
                if (pl.getBans().isBanned(profile)) yield fail(admin, MessagesConfig.MANAGE_ALREADY_BANNED, "player", name);
                java.util.Date until = new java.util.Date(System.currentTimeMillis() + number * 60_000L);
                pl.getBans().add(new UserBanListEntry(profile, null, adminName, until,
                        finalReason.isEmpty() ? null : finalReason));
                if (online != null) online.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
                suggest(server, uuid, name, ManageSuggestions.Kind.BANNED);
                yield ok(admin, MessagesConfig.MANAGE_TEMPBANNED, "player", name,
                        "date", net.rp.rpessentials.util.TimeHelper.format("dd/MM/yyyy HH:mm", until.getTime()));
            }
            case PARDON -> {
                if (!pl.getBans().isBanned(profile)) yield fail(admin, MessagesConfig.MANAGE_NOT_BANNED, "player", name);
                pl.getBans().remove(profile);
                yield ok(admin, MessagesConfig.MANAGE_PARDONED, "player", name);
            }
            case OP -> {
                int level = Math.max(1, Math.min(4, number));
                if (level > adminLevel) yield fail(admin, MessagesConfig.MANAGE_OP_TOO_HIGH);
                if (self && level < adminLevel) yield fail(admin, MessagesConfig.MANAGE_OP_LOWER_SELF);
                setOp(server, profile, level);
                yield ok(admin, MessagesConfig.MANAGE_OP_SET, "player", name, "level", String.valueOf(level));
            }
            case DEOP -> {
                if (self) yield fail(admin, MessagesConfig.MANAGE_DEOP_SELF);
                if (!pl.isOp(profile)) yield fail(admin, MessagesConfig.MANAGE_NOT_OP, "player", name);
                if (server.getProfilePermissions(profile) > adminLevel) yield fail(admin, MessagesConfig.MANAGE_DEOP_HIGHER);
                pl.deop(profile);
                suggest(server, uuid, name, ManageSuggestions.Kind.DEOPPED);
                yield ok(admin, MessagesConfig.MANAGE_DEOPPED, "player", name);
            }
            case CLEAR_INVENTORY -> {
                if (online == null) yield queue(admin, uuid, name, action, adminName);
                online.getInventory().clearContent();
                online.containerMenu.broadcastChanges();
                yield ok(admin, MessagesConfig.MANAGE_INV_CLEARED, "player", name);
            }
            case CLEAR_ENDER -> {
                if (online == null) yield queue(admin, uuid, name, action, adminName);
                online.getEnderChestInventory().clearContent();
                yield ok(admin, MessagesConfig.MANAGE_ENDER_CLEARED, "player", name);
            }
            case ABSENCE_ADD -> {
                int days = Math.max(1, Math.min(365, number));
                AbsenceManager.add(uuid, name, days, finalReason, adminName);
                yield ok(admin, MessagesConfig.MANAGE_ABSENCE_SET, "player", name, "days", String.valueOf(days));
            }
            case ABSENCE_REMOVE -> AbsenceManager.remove(uuid)
                    ? ok(admin, MessagesConfig.MANAGE_ABSENCE_REMOVED, "player", name)
                    : fail(admin, MessagesConfig.MANAGE_NO_ABSENCE, "player", name);
            case CLEAR_NICKNAME -> {
                boolean had = NicknameManager.hasNickname(uuid);
                if (had) NicknameManager.removeNickname(uuid);
                if (PendingProfileManager.hasPending(uuid)) {
                    PendingProfileManager.setNickname(uuid, "");
                    had = true;
                }
                yield had ? ok(admin, MessagesConfig.MANAGE_NICK_CLEARED, "player", name)
                        : fail(admin, MessagesConfig.MANAGE_NO_NICK, "player", name);
            }
            case CLEAR_ROLE -> {
                if (online == null) {
                    if (PendingProfileManager.hasPending(uuid)) {
                        PendingProfileManager.setRole(uuid, "");
                        yield ok(admin, MessagesConfig.MANAGE_PREP_ROLE_CLEARED, "player", name);
                    }
                    yield queue(admin, uuid, name, action, adminName);
                }
                yield clearRole(server, online)
                        ? ok(admin, MessagesConfig.MANAGE_ROLE_CLEARED, "player", name)
                        : fail(admin, MessagesConfig.MANAGE_ROLES_UNAVAILABLE);
            }
            case CLEAR_PROFESSION -> {
                int n = clearProfessions(server, uuid, online, name, adminName);
                yield n > 0 ? ok(admin, MessagesConfig.MANAGE_PROF_CLEARED, "count", String.valueOf(n), "player", name)
                        : fail(admin, MessagesConfig.MANAGE_NO_PROF, "player", name);
            }
            case RESET_ALL -> {
                if (self) yield fail(admin, MessagesConfig.MANAGE_RESET_SELF);
                if (pl.isOp(profile) && server.getProfilePermissions(profile) > adminLevel) {
                    yield fail(admin, MessagesConfig.MANAGE_RESET_HIGHER);
                }
                int done = 0;
                bulk = true;
                try {
                    for (Action step : RESET_STEPS) {
                        if (execute(server, admin, uuid, step, "", 0).ok()) done++;
                    }
                } finally {
                    bulk = false;
                }
                yield ok(admin, MessagesConfig.MANAGE_RESET_DONE, "player", name, "count", String.valueOf(done));
            }
            case TP_TO_PLAYER -> {
                if (admin == null) yield fail(null, MessagesConfig.MANAGE_TP_ONLY_PLAYER);
                if (self) yield fail(admin, MessagesConfig.MANAGE_TP_SELF);
                if (online == null) yield fail(admin, MessagesConfig.MANAGE_NOT_ONLINE, "player", name);
                admin.teleportTo(online.serverLevel(), online.getX(), online.getY(), online.getZ(),
                        online.getYRot(), online.getXRot());
                yield ok(admin, MessagesConfig.MANAGE_TP_DONE, "player", name);
            }
            case TP_PLAYER_HERE -> {
                if (admin == null) yield fail(null, MessagesConfig.MANAGE_BRING_ONLY_PLAYER);
                if (self) yield fail(admin, MessagesConfig.MANAGE_BRING_SELF);
                if (online == null) yield fail(admin, MessagesConfig.MANAGE_NOT_ONLINE, "player", name);
                online.teleportTo(admin.serverLevel(), admin.getX(), admin.getY(), admin.getZ(),
                        admin.getYRot(), admin.getXRot());
                yield ok(admin, MessagesConfig.MANAGE_BRING_DONE, "player", name);
            }
            case TP_PLAYER_PLATFORM -> {
                if (online == null) yield fail(admin, MessagesConfig.MANAGE_NOT_ONLINE, "player", name);
                Spot spot = platformSpot(server, finalReason);
                if (spot == null) yield fail(admin, MessagesConfig.MANAGE_PLATFORM_UNKNOWN, "platform", finalReason);
                online.teleportTo(spot.level(), spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch());
                yield ok(admin, MessagesConfig.MANAGE_PLATFORM_SENT, "player", name, "platform", spot.name());
            }
            case TP_PLAYER_AFK -> {
                if (online == null) yield fail(admin, MessagesConfig.MANAGE_NOT_ONLINE, "player", name);
                Spot spot = afkSpot(server);
                if (spot == null) yield fail(admin, MessagesConfig.MANAGE_AFK_MISSING);
                online.teleportTo(spot.level(), spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch());
                yield ok(admin, MessagesConfig.MANAGE_AFK_SENT, "player", name);
            }
            case VANISH -> {
                if (!VanishManager.enabled()) yield fail(admin, MessagesConfig.MANAGE_VANISH_OFF);
                if (online == null) yield fail(admin, MessagesConfig.MANAGE_NOT_ONLINE, "player", name);
                int own = admin != null ? VanishManager.levelOf(admin) : 4;
                int level = Math.max(0, Math.min(4, number));
                if (level > own) yield fail(admin, MessagesConfig.MANAGE_VANISH_ABOVE_OWN);
                if (VanishManager.isVanished(uuid) && VanishManager.getLevel(uuid) > own) {
                    yield fail(admin, MessagesConfig.MANAGE_HIDDEN_ABOVE, "player", name);
                }
                VanishManager.set(server, online, level);
                yield ok(admin, MessagesConfig.MANAGE_VANISHED, "player", name, "level", String.valueOf(level));
            }
            case UNVANISH -> {
                if (online == null) yield fail(admin, MessagesConfig.MANAGE_NOT_ONLINE, "player", name);
                if (!VanishManager.isVanished(uuid)) yield fail(admin, MessagesConfig.MANAGE_NOT_VANISHED, "player", name);
                int own = admin != null ? VanishManager.levelOf(admin) : 4;
                if (VanishManager.getLevel(uuid) > own) yield fail(admin, MessagesConfig.MANAGE_HIDDEN_ABOVE, "player", name);
                VanishManager.clear(server, online);
                yield ok(admin, MessagesConfig.MANAGE_VISIBLE, "player", name);
            }
        };

        if (result.ok() && action != Action.TP_TO_PLAYER) {
            // A vanish action is only visible to staff who can see that vanish level.
            int hiddenLevel = action == Action.VANISH ? Math.max(1, Math.min(4, number))
                    : action == Action.UNVANISH ? vanishBefore : 0;
            HistoryManager.record(uuid, name, HistoryManager.MANAGE, adminName,
                    action.name() + (finalReason.isEmpty() ? "" : " - " + finalReason), hiddenLevel);
        }
        boolean silent = action == Action.TP_TO_PLAYER || action == Action.VANISH || action == Action.UNVANISH;
        if (result.ok() && !bulk && !silent) alertStaff(server, admin, adminName, action.name(), name, finalReason);
        return result;
    }

    private record Spot(ServerLevel level, double x, double y, double z, float yaw, float pitch, String name) {}

    private static Result queue(ServerPlayer v, UUID uuid, String name, Action action, String issuer) {
        PendingActionManager.add(uuid, name, action, issuer);
        return ok(v, MessagesConfig.MANAGE_QUEUED, "player", name, "action", action.name());
    }

    private static Spot platformSpot(MinecraftServer server, String id) {
        try {
            for (String p : ModerationConfig.PLATFORMS.get()) {
                String[] parts = p.split(";");
                if (parts.length < 6 || !parts[0].equals(id)) continue;
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(parts[2])));
                if (level == null) return null;
                return new Spot(level, Double.parseDouble(parts[3]), Double.parseDouble(parts[4]),
                        Double.parseDouble(parts[5]), 0f, 0f, parts[1]);
            }
        } catch (RuntimeException ignored) {}
        return null;
    }

    private static Spot afkSpot(MinecraftServer server) {
        try {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(RpConfig.AFK_DIMENSION.get())));
            if (level == null) return null;
            return new Spot(level, RpConfig.AFK_X.get(), RpConfig.AFK_Y.get(), RpConfig.AFK_Z.get(),
                    RpConfig.AFK_YAW.get().floatValue(), RpConfig.AFK_PITCH.get().floatValue(), "AFK");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void suggest(MinecraftServer server, UUID uuid, String name, ManageSuggestions.Kind kind) {
        if (!bulk) ManageSuggestions.propose(server, uuid, name, kind);
    }

    private static boolean clearRole(MinecraftServer server, ServerPlayer target) {
        var silent = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        String name = target.getName().getString();
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                server.getCommands().performPrefixedCommand(silent,
                        "tag " + name + " remove " + entry.split(";", 2)[0].trim());
            }
        } else {
            return false;
        }
        server.getCommands().performPrefixedCommand(silent, "lp user " + name + " parent set default");
        RpEssentialsRoleManager.invalidate(target.getUUID());
        RpEssentialsPermissions.invalidateCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);
        SyncNametagDataPacket.broadcastDelayed(target);
        RpEssentialsScheduleManager.enforceOnlineDelayed(server);
        return true;
    }

    private static int clearProfessions(MinecraftServer server, UUID uuid, ServerPlayer online,
                                        String name, String adminName) {
        var silent = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        List<String> licenses = LicenseManager.getLicenses(uuid);
        for (String prof : licenses) {
            LicenseManager.removeLicense(uuid, prof);
            LicenseManager.logActionSystem("REVOKE", name, uuid.toString(), prof, net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.AUDIT_VIA_MANAGE, "staff", adminName));
            if (online != null && !ProfessionRestrictionManager.isReservedTag(prof)) {
                server.getCommands().performPrefixedCommand(silent, "tag " + name + " remove " + prof);
            }
        }
        int count = licenses.size();

        if (PendingProfileManager.hasPending(uuid)) {
            for (PendingProfileManager.PendingEntry e : PendingProfileManager.getAllPending()) {
                if (!uuid.toString().equals(e.uuid)) continue;
                for (String l : new ArrayList<>(e.licenses)) {
                    PendingProfileManager.removeLicense(uuid, l);
                    count++;
                }
            }
        }

        ProfessionRestrictionManager.invalidatePlayerCache(uuid);
        if (online != null) {
            ProfessionSyncHelper.syncToPlayer(online);
            TempLicenseExpirationManager.markRevokedLicenseItems(online);
        }
        return count;
    }

    private static void alertStaff(MinecraftServer server, ServerPlayer admin, String adminName,
                                   String actionName, String targetName, String reason) {
        String suffix = reason.isEmpty() ? "" : " (" + reason + ")";
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpEssentialsPermissions.isStaff(p) && (admin == null || !p.getUUID().equals(admin.getUUID()))) {
                p.sendSystemMessage(ColorHelper.parseSection(ServerLang.get(p, MessagesConfig.MANAGE_ALERT,
                        "admin", adminName, "action", actionName, "player", targetName, "reason", suffix)));
            }
        }
        RpEssentials.LOGGER.info("[Manage] {}", ColorHelper.stripAll(ServerLang.get(null, MessagesConfig.MANAGE_ALERT,
                "admin", adminName, "action", actionName, "player", targetName, "reason", suffix)));
    }
}