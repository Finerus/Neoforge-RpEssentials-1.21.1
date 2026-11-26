package net.rp.rpessentials.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.moderation.HistoryManager;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.ManageService;

import java.util.Collection;
import java.util.UUID;

/** The player of a GameProfileArgument, among those known by the server. */
public final class KnownPlayers {

    private KnownPlayers() {}

    /**
     * The vanilla argument asks the Mojang servers for unknown names, on the server thread: any player allowed to run
     * the command could freeze the server with random names.
     */
    public static GameProfile get(CommandContext<CommandSourceStack> ctx, String argument) throws CommandSyntaxException {
        String name = "";
        for (var node : ctx.getNodes()) {
            if (node.getNode().getName().equals(argument)) name = node.getRange().get(ctx.getInput());
        }
        if (name.startsWith("@")) {
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, argument);
            if (profiles.size() != 1) throw EntityArgument.ERROR_NOT_SINGLE_PLAYER.create();
            return profiles.iterator().next();
        }
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return online.getGameProfile();
        UUID id = LastConnectionManager.findUUIDByName(name);
        if (id == null) id = HistoryManager.findByName(name);
        if (id == null) throw GameProfileArgument.ERROR_UNKNOWN_PLAYER.create();
        return ManageService.resolveProfile(ctx.getSource().getServer(), id);
    }
}
