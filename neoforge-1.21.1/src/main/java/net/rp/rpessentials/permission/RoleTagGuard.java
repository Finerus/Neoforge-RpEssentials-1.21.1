package net.rp.rpessentials.permission;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.Collection;

/**
 * A role is a vanilla tag of the player: /tag would be a way to give its permissions (or to take away the refusals of
 * a role) with OP 2 or a command block. See the option protectRoleTags.
 */
public final class RoleTagGuard {

    private RoleTagGuard() {}

    public static boolean refuses(CommandSourceStack src, Collection<? extends Entity> targets, String tag) {
        // Cheapest first: nearly every /tag of a map or datapack stops here.
        if (!RpEssentialsRoleManager.givesPermissions(tag)) return false;
        // The server itself: console, tick and load functions, commands of the config files.
        if (src.source instanceof MinecraftServer) return false;
        if (!Cfg.get(RpEssentialsConfig.PROTECT_ROLE_TAGS, true)) return false;
        boolean player = false;
        for (Entity e : targets) if (e instanceof ServerPlayer) { player = true; break; }
        if (!player || RpPerms.has(src, RpPerm.ROLES_SET)) return false;
        src.sendFailure(Component.translatableWithFallback("rpessentials.tag.protected",
                "The tag '%s' gives the permissions of a role: use /rpessentials setrole (needs rpessentials.roles.set).", tag)
                .withStyle(ChatFormatting.RED));
        // The place tells which command block or sign it is.
        RpEssentials.LOGGER.warn("[Roles] {} tried to change the tag '{}' of a player with /tag (refused), at {} in {}", src.getTextName(),
                tag, net.minecraft.core.BlockPos.containing(src.getPosition()).toShortString(), src.getLevel().dimension().location());
        return true;
    }
}
