package net.rp.rpessentials.moderation;

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

public final class ManageService {

    public enum Action {
        WHITELIST_ADD, WHITELIST_REMOVE, KICK, BAN, PARDON, OP, DEOP,
        CLEAR_INVENTORY, CLEAR_ENDER, ABSENCE_ADD, ABSENCE_REMOVE,
        CLEAR_NICKNAME, CLEAR_ROLE, CLEAR_PROFESSION, RESET_ALL,
        TP_TO_PLAYER, TP_PLAYER_HERE, TP_PLAYER_PLATFORM, TP_PLAYER_AFK,
        VANISH, UNVANISH;

        public boolean sensitive() {
            return this == BAN || this == PARDON || this == OP || this == DEOP
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
            return this == KICK || this == BAN || this == DEOP || this == RESET_ALL
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

    private static Result ok(String msg) { return new Result(true, "§a[Manage] " + msg); }
    private static Result fail(String msg) { return new Result(false, "§c[Manage] " + msg); }

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
        String adminName = admin != null ? admin.getName().getString() : "Console";
        if (action.sensitive() && admin != null && !RpEssentialsPermissions.hasSensitiveLevel(admin)) {
            return fail("This action requires the OP level set in opLevelBypass.");
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

        Result result = switch (action) {
            case WHITELIST_ADD -> {
                pl.getWhiteList().add(new UserWhiteListEntry(profile));
                yield ok(name + " added to the whitelist.");
            }
            case WHITELIST_REMOVE -> {
                pl.getWhiteList().remove(new UserWhiteListEntry(profile));
                if (online != null && pl.isUsingWhitelist() && !pl.isWhiteListed(profile)) {
                    online.connection.disconnect(Component.translatable("multiplayer.disconnect.not_whitelisted"));
                }
                suggest(server, uuid, name, ManageSuggestions.Kind.UNWHITELISTED);
                yield ok(name + " removed from the whitelist.");
            }
            case KICK -> {
                if (self) yield fail("You cannot kick yourself.");
                if (online == null) yield fail(name + " is not online.");
                online.connection.disconnect(finalReason.isEmpty()
                        ? Component.translatable("multiplayer.disconnect.kicked")
                        : Component.literal(finalReason));
                yield ok(name + " kicked.");
            }
            case BAN -> {
                if (self) yield fail("You cannot ban yourself.");
                if (pl.getBans().isBanned(profile)) yield fail(name + " is already banned.");
                pl.getBans().add(new UserBanListEntry(profile, null, adminName, null,
                        finalReason.isEmpty() ? null : finalReason));
                if (online != null) online.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
                suggest(server, uuid, name, ManageSuggestions.Kind.BANNED);
                yield ok(name + " banned.");
            }
            case PARDON -> {
                if (!pl.getBans().isBanned(profile)) yield fail(name + " is not banned.");
                pl.getBans().remove(profile);
                yield ok(name + " pardoned.");
            }
            case OP -> {
                int level = Math.max(1, Math.min(4, number));
                if (level > adminLevel) yield fail("You cannot grant a level higher than your own.");
                if (self && level < adminLevel) yield fail("You cannot lower your own OP level.");
                setOp(server, profile, level);
                yield ok(name + " is now OP level " + level + ".");
            }
            case DEOP -> {
                if (self) yield fail("You cannot deop yourself.");
                if (!pl.isOp(profile)) yield fail(name + " is not an operator.");
                if (server.getProfilePermissions(profile) > adminLevel) yield fail("You cannot deop a higher level operator.");
                pl.deop(profile);
                suggest(server, uuid, name, ManageSuggestions.Kind.DEOPPED);
                yield ok(name + " is no longer an operator.");
            }
            case CLEAR_INVENTORY -> {
                if (online == null) yield queue(uuid, name, action, adminName);
                online.getInventory().clearContent();
                online.containerMenu.broadcastChanges();
                yield ok("Inventory of " + name + " cleared.");
            }
            case CLEAR_ENDER -> {
                if (online == null) yield queue(uuid, name, action, adminName);
                online.getEnderChestInventory().clearContent();
                yield ok("Ender chest of " + name + " cleared.");
            }
            case ABSENCE_ADD -> {
                int days = Math.max(1, Math.min(365, number));
                AbsenceManager.add(uuid, name, days, finalReason, adminName);
                yield ok("Absence set for " + name + " (" + days + " days).");
            }
            case ABSENCE_REMOVE -> AbsenceManager.remove(uuid)
                    ? ok("Absence of " + name + " removed.")
                    : fail(name + " has no absence.");
            case CLEAR_NICKNAME -> {
                boolean had = NicknameManager.hasNickname(uuid);
                if (had) NicknameManager.removeNickname(uuid);
                if (PendingProfileManager.hasPending(uuid)) {
                    PendingProfileManager.setNickname(uuid, "");
                    had = true;
                }
                yield had ? ok("Nickname of " + name + " cleared.") : fail(name + " has no nickname.");
            }
            case CLEAR_ROLE -> {
                if (online == null) {
                    if (PendingProfileManager.hasPending(uuid)) {
                        PendingProfileManager.setRole(uuid, "");
                        yield ok("Prepared role of " + name + " cleared.");
                    }
                    yield queue(uuid, name, action, adminName);
                }
                yield clearRole(server, online) ? ok("Role of " + name + " cleared.") : fail("Roles config unavailable.");
            }
            case CLEAR_PROFESSION -> {
                int n = clearProfessions(server, uuid, online, name, adminName);
                yield n > 0 ? ok(n + " profession(s) cleared for " + name + ".") : fail(name + " has no profession.");
            }
            case RESET_ALL -> {
                if (self) yield fail("You cannot reset yourself.");
                if (pl.isOp(profile) && server.getProfilePermissions(profile) > adminLevel) {
                    yield fail("You cannot reset a higher level operator.");
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
                yield ok("Full reset of " + name + ": " + done + " step(s) applied or queued.");
            }
            case TP_TO_PLAYER -> {
                if (admin == null) yield fail("Only a player can teleport.");
                if (self) yield fail("You cannot teleport to yourself.");
                if (online == null) yield fail(name + " is not online.");
                admin.teleportTo(online.serverLevel(), online.getX(), online.getY(), online.getZ(),
                        online.getYRot(), online.getXRot());
                yield ok("Teleported to " + name + ".");
            }
            case TP_PLAYER_HERE -> {
                if (admin == null) yield fail("Only a player can use this action.");
                if (self) yield fail("You cannot teleport yourself to yourself.");
                if (online == null) yield fail(name + " is not online.");
                online.teleportTo(admin.serverLevel(), admin.getX(), admin.getY(), admin.getZ(),
                        admin.getYRot(), admin.getXRot());
                yield ok(name + " teleported to you.");
            }
            case TP_PLAYER_PLATFORM -> {
                if (online == null) yield fail(name + " is not online.");
                Spot spot = platformSpot(server, finalReason);
                if (spot == null) yield fail("Unknown platform: " + finalReason);
                online.teleportTo(spot.level(), spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch());
                yield ok(name + " sent to " + spot.name() + ".");
            }
            case TP_PLAYER_AFK -> {
                if (online == null) yield fail(name + " is not online.");
                Spot spot = afkSpot(server);
                if (spot == null) yield fail("AFK dimension not found.");
                online.teleportTo(spot.level(), spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch());
                yield ok(name + " sent to the AFK zone.");
            }
            case VANISH -> {
                if (!VanishManager.enabled()) yield fail("Vanish is disabled in the config.");
                if (online == null) yield fail(name + " is not online.");
                int own = admin != null ? VanishManager.levelOf(admin) : 4;
                int level = Math.max(0, Math.min(4, number));
                if (level > own) yield fail("You cannot vanish above your own OP level.");
                if (VanishManager.isVanished(uuid) && VanishManager.getLevel(uuid) > own) {
                    yield fail(name + " is hidden above your OP level.");
                }
                VanishManager.set(server, online, level);
                yield ok(name + " is now vanished (level " + level + ").");
            }
            case UNVANISH -> {
                if (online == null) yield fail(name + " is not online.");
                if (!VanishManager.isVanished(uuid)) yield fail(name + " is not vanished.");
                int own = admin != null ? VanishManager.levelOf(admin) : 4;
                if (VanishManager.getLevel(uuid) > own) yield fail(name + " is hidden above your OP level.");
                VanishManager.clear(server, online);
                yield ok(name + " is now visible.");
            }
        };

        boolean silent = action == Action.TP_TO_PLAYER || action == Action.VANISH || action == Action.UNVANISH;
        if (result.ok() && !bulk && !silent) alertStaff(server, admin, adminName, action.name() + " on " + name
                + (finalReason.isEmpty() ? "" : " (" + finalReason + ")"));
        return result;
    }

    private record Spot(ServerLevel level, double x, double y, double z, float yaw, float pitch, String name) {}

    private static Result queue(UUID uuid, String name, Action action, String issuer) {
        PendingActionManager.add(uuid, name, action, issuer);
        return ok(name + " is offline: " + action.name() + " queued until the next login.");
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
        try {
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                server.getCommands().performPrefixedCommand(silent,
                        "tag " + name + " remove " + entry.split(";", 2)[0].trim());
            }
        } catch (IllegalStateException e) {
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
            LicenseManager.logActionSystem("REVOKE", name, uuid.toString(), prof, "via manage by " + adminName);
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

    private static void alertStaff(MinecraftServer server, ServerPlayer admin, String adminName, String msg) {
        Component c = Component.literal("§6[Manage] §e" + adminName + " §7: " + msg);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpEssentialsPermissions.isStaff(p) && (admin == null || !p.getUUID().equals(admin.getUUID()))) {
                p.sendSystemMessage(c);
            }
        }
        RpEssentials.LOGGER.info("[Manage] {} : {}", adminName, msg);
    }
}