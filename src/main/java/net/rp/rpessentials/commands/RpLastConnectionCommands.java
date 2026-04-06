package net.rp.rpessentials.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.moderation.LastConnectionManager;

import java.util.UUID;

public class RpLastConnectionCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var lastConnNode = Commands.literal("lastconnection")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()));

        lastConnNode.then(Commands.argument("player", StringArgumentType.word())
                .executes(RpLastConnectionCommands::lastConnectionPlayer));

        lastConnNode.then(Commands.literal("list")
                .executes(ctx -> lastConnectionList(ctx, 20))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> lastConnectionList(ctx, IntegerArgumentType.getInteger(ctx, "count")))));

        return lastConnNode;
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    private static int lastConnectionPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) {
                ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.LASTCONN_DISABLED)));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
        UUID targetUUID = online != null ? online.getUUID() : LastConnectionManager.findUUIDByName(targetName);

        if (targetUUID == null) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.LASTCONN_PLAYER_NOT_FOUND, "player", targetName)));
            return 0;
        }

        final UUID finalTargetUUID = targetUUID;
        LastConnectionManager.ConnectionEntry entry = LastConnectionManager.getEntry(finalTargetUUID);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.LASTCONN_NO_DATA, "player", targetName)));
            return 0;
        }

        String status = online != null
                ? MessagesConfig.get(MessagesConfig.LASTCONN_ONLINE)
                : MessagesConfig.get(MessagesConfig.LASTCONN_OFFLINE);
        String unknown = MessagesConfig.get(MessagesConfig.LASTCONN_UNKNOWN);
        String loginStr = entry.lastLogin != null ? "§f" + entry.lastLogin : unknown;
        String logoutStr = entry.lastLogout != null ? "§f" + entry.lastLogout : unknown;
        String displayName = entry.mcName != null ? entry.mcName : targetName;

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LASTCONN_BOX_HEADER) + "\n" +
                        MessagesConfig.get(MessagesConfig.LASTCONN_BOX_PLAYER) + "§e" + displayName + " §8(" + finalTargetUUID + ")\n" +
                        MessagesConfig.get(MessagesConfig.LASTCONN_BOX_STATUS) + status + "\n" +
                        MessagesConfig.get(MessagesConfig.LASTCONN_BOX_LOGIN) + loginStr + "\n" +
                        MessagesConfig.get(MessagesConfig.LASTCONN_BOX_LOGOUT) + logoutStr + "\n" +
                        "§6╚════════════════════════════════════╝"), false);
        return 1;
    }

    private static int lastConnectionList(CommandContext<CommandSourceStack> ctx, int count) {
        try {
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) {
                ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.LASTCONN_DISABLED)));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        var allEntries = LastConnectionManager.getAllSortedByLogin();
        int total = allEntries.size();

        if (total == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(MessagesConfig.get(MessagesConfig.LASTCONN_NO_DATA_LIST)), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.LASTCONN_LIST_HEADER,
                "shown", String.valueOf(Math.min(count, total)), "total", String.valueOf(total))).append("\n");

        String unknown = MessagesConfig.get(MessagesConfig.LASTCONN_UNKNOWN);
        int shown = 0;
        for (var e : allEntries) {
            if (shown >= count) break;
            UUID uuid = e.getKey();
            LastConnectionManager.ConnectionEntry entry = e.getValue();
            String name = entry.mcName != null ? entry.mcName : uuid.toString().substring(0, 8) + "...";
            boolean isOnline = server.getPlayerList().getPlayer(uuid) != null;
            String bullet = isOnline
                    ? MessagesConfig.get(MessagesConfig.LASTCONN_ONLINE).substring(0, 4)
                    : MessagesConfig.get(MessagesConfig.LASTCONN_OFFLINE).substring(0, 4);
            String loginStr = entry.lastLogin != null ? entry.lastLogin : unknown;
            sb.append("§6║ ").append(bullet).append(" §e").append(name)
                    .append("§7 — ").append(loginStr).append("\n");
            shown++;
        }
        sb.append("§6╚═══════════════════════════════════╝");

        String finalMsg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(finalMsg), false);
        return 1;
    }
}
