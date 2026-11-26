package net.rp.rpessentials.commands;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Runs commands from the config as a player: @s targets the player, ~ ~ ~ is the player position,
 * {player} is replaced by the Minecraft name. Commands run with OP level 4 and their output is hidden.
 */
public final class PlayerCommands {

    private PlayerCommands() {}


    public static List<? extends String> get(ModConfigSpec.ConfigValue<List<? extends String>> cv) {
        return Cfg.get(cv, List.of());
    }

    private static final java.util.regex.Pattern PLAIN_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_.+-]{1,32}");

    /**
     * Name written in a command run by the server, or the UUID when the name could mean something else there
     * (offline-mode servers accept names such as "@a").
     */
    public static String safeName(String name, java.util.UUID uuid) {
        return name != null && PLAIN_NAME.matcher(name).matches() ? name : uuid.toString();
    }

    public static void runAs(ServerPlayer player, List<? extends String> commands) {
        if (commands == null || commands.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CommandSourceStack src = player.createCommandSourceStack().withSource(server).withPermission(4).withSuppressedOutput();
        String name = safeName(player.getGameProfile().getName(), player.getUUID());
        for (String raw : commands) {
            if (raw == null) continue;
            String cmd = raw.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (cmd.isEmpty()) continue;
            server.getCommands().performPrefixedCommand(src, cmd.replace("{player}", name));
        }
    }
}
