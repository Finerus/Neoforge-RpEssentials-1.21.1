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

    public static void runAs(ServerPlayer player, List<? extends String> commands) {
        if (commands == null || commands.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CommandSourceStack src = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        String name = player.getGameProfile().getName();
        for (String raw : commands) {
            if (raw == null) continue;
            String cmd = raw.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (cmd.isEmpty()) continue;
            server.getCommands().performPrefixedCommand(src, cmd.replace("{player}", name));
        }
    }
}
