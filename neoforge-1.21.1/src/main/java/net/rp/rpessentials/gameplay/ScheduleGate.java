package net.rp.rpessentials.gameplay;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.moderation.ManageService;
import net.rp.rpessentials.moderation.PendingActionManager;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.profession.PendingProfileManager;
import net.rp.rpessentials.util.PlayerRefs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refuses a player while the server is closed BEFORE they enter the world (like the whitelist), without keeping any
 * copy of their roles: the tags are read in their save file, and everything that can change while they are away is
 * looked at live (role given or cleared offline, LuckPerms groups, OP level, nodes of the roles, schedule whitelist).
 * <p>
 * When something cannot be known at this point (save file unreadable, other permission mod...), the player goes on
 * and the check made once they are in the world decides, as before.
 */
public final class ScheduleGate {

    private enum Verdict { ALLOW, REFUSE, UNKNOWN }

    private record Tags(long modified, long size, Set<String> tags) {}

    /** Tags already read, so a player who keeps trying does not make the server read their file each time. */
    private static final Map<UUID, Tags> TAGS = new ConcurrentHashMap<>();

    private ScheduleGate() {}

    public static void clear() { TAGS.clear(); }

    /** Why this player cannot log in, null when they can (or when it has to be checked later). */
    public static Component refusal(MinecraftServer server, GameProfile profile) {
        if (server == null || RpEssentialsScheduleManager.isServerOpen()) return null;
        Verdict v;
        try {
            v = check(server, profile);
        } catch (RuntimeException | LinkageError e) {
            RpEssentials.LOGGER.warn("[Schedule] {} is checked after the login: {}", profile.getName(), e.toString());
            return null;
        }
        if (v != Verdict.REFUSE) return null;
        RpEssentialsScheduleManager.notifyRefused(server, profile.getName());
        return RpEssentialsScheduleManager.closedMessage();
    }

    private static Verdict check(MinecraftServer server, GameProfile profile) {
        UUID id = profile.getId();
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return RpEssentialsScheduleManager.isScheduleExempt(online) ? Verdict.ALLOW : Verdict.REFUSE;
        if (server.isSingleplayerOwner(profile) || PlayerRefs.SCHEDULE.contains(profile)) return Verdict.ALLOW;

        Set<String> roles = new LinkedHashSet<>(RpEssentialsRoleManager.luckPermsRoleIds(id));
        boolean known = true;
        String pending = pendingRole(id);
        if (pending != null) {
            roles.add(pending);
        } else if (!PendingActionManager.has(id, ManageService.Action.CLEAR_ROLE)) {
            Set<String> tags = savedTags(server, id);
            if (tags == null) known = false;
            else roles.addAll(tags);
        }
        boolean allowed = grants(id, roles, RpPerm.BYPASS_SCHEDULE, server.getProfilePermissions(profile));
        if (allowed) return Verdict.ALLOW;
        return known && RpEssentialsRoleManager.canDecideOffline(id) ? Verdict.REFUSE : Verdict.UNKNOWN;
    }

    /** Same order as RpPerms: LuckPerms, roles, then staff (role, or opLevelBypass). */
    private static boolean grants(UUID id, Collection<String> roles, RpPerm perm, int opLevel) {
        Boolean v = RpEssentialsRoleManager.luckPermsValue(id, perm.node());
        if (v == null) v = RpEssentialsRoleManager.roleValueForRoles(roles, perm);
        if (v != null) return v;
        if (perm == RpPerm.STAFF) {
            int level = Cfg.get(RpEssentialsConfig.OP_LEVEL_BYPASS, 0);
            return level > 0 && opLevel >= level;
        }
        return perm.staffDefault() && grants(id, roles, RpPerm.STAFF, opLevel);
    }

    private static String pendingRole(UUID id) {
        String role = PendingProfileManager.getRole(id);
        return role == null || role.isBlank() ? null : role;
    }

    /** A role given while the player was away, not applied yet: true when it lets them in. */
    public static boolean pendingRoleAllows(MinecraftServer server, GameProfile profile) {
        String role = pendingRole(profile.getId());
        return role != null && grants(profile.getId(), Set.of(role), RpPerm.BYPASS_SCHEDULE, server.getProfilePermissions(profile));
    }

    /** Vanilla tags in the save file of a player: empty when they never came, null when they cannot be read. */
    private static Set<String> savedTags(MinecraftServer server, UUID id) {
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
        try {
            BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class);
            Tags cached = TAGS.get(id);
            if (cached != null && cached.modified() == a.lastModifiedTime().toMillis() && cached.size() == a.size()) return cached.tags();
            // Only this field is kept: the inventory and the data of other mods are skipped.
            CollectFields visitor = new CollectFields(new FieldSelector(ListTag.TYPE, "Tags"));
            //#if forge
            //? net.minecraft.nbt.NbtIo.parseCompressed(file.toFile(), visitor);
            //#else
            net.minecraft.nbt.NbtIo.parseCompressed(file, visitor, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            //#endif
            Set<String> tags = new HashSet<>();
            if (visitor.getResult() instanceof CompoundTag root) {
                ListTag list = root.getList("Tags", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) tags.add(list.getString(i));
            }
            if (TAGS.size() > 500) TAGS.clear();
            TAGS.put(id, new Tags(a.lastModifiedTime().toMillis(), a.size(), tags));
            return tags;
        } catch (NoSuchFileException e) {
            return Set.of();
        } catch (IOException | RuntimeException e) {
            RpEssentials.LOGGER.warn("[Schedule] Could not read the tags of {}: {}", id, e.toString());
            return null;
        }
    }
}
