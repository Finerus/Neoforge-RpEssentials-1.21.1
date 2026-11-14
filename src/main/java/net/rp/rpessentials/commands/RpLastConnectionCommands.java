package net.rp.rpessentials.commands;

import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.lang.Msg;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.VanishManager;

import java.util.UUID;

public class RpLastConnectionCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var lastConnNode = Commands.literal("lastconnection")
                .requires(RpEssentialsPermissions::isStaffSource);

        lastConnNode.then(Commands.argument("player", StringArgumentType.word())
                .executes(RpLastConnectionCommands::lastConnectionPlayer));

        lastConnNode.then(Commands.literal("list")
                .executes(ctx -> lastConnectionList(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> lastConnectionList(ctx, IntegerArgumentType.getInteger(ctx, "page")))));

        return lastConnNode;
    }

    private static int lastConnectionPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) {
                ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_DISABLED)));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
        UUID targetUUID = online != null ? online.getUUID() : LastConnectionManager.findUUIDByName(targetName);
        if (online != null && VanishManager.canHide(VanishManager.Aspect.LIST, ctx.getSource(), online.getUUID())) {
            online = null;
        }

        if (targetUUID == null) {
            ctx.getSource().sendFailure(Component.literal(
                    Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_PLAYER_NOT_FOUND, "player", targetName)));
            return 0;
        }

        final UUID finalTargetUUID = targetUUID;
        LastConnectionManager.ConnectionEntry entry = LastConnectionManager.getEntry(finalTargetUUID);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal(
                    Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_NO_DATA, "player", targetName)));
            return 0;
        }

        String status = online != null
                ? Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_ONLINE)
                : Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_OFFLINE);
        String unknown = Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_UNKNOWN);
        String loginStr = entry.lastLogin != null ? "§f" + entry.lastLogin : unknown;
        String logoutStr = entry.lastLogout != null ? "§f" + entry.lastLogout : unknown;
        String displayName = entry.mcName != null ? entry.mcName : targetName;

        MutableComponent nameComp = Component.literal("§e" + displayName).withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        ColorHelper.parseSection(Msg.get(ctx.getSource(), MessagesConfig.STAFF_LASTCO_COPY_HOVER, "uuid", finalTargetUUID.toString()))))
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, finalTargetUUID.toString())));

        MutableComponent out = Component.literal(
                        Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_BOX_HEADER) + "\n" + RpChatUi.LINE + "\n"
                                + Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_BOX_PLAYER))
                .append(nameComp)
                .append(Component.literal("\n" + Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_BOX_STATUS) + status
                        + "\n" + Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_BOX_LOGIN) + loginStr
                        + "\n" + Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_BOX_LOGOUT) + logoutStr
                        + "\n" + RpChatUi.LINE));
        ctx.getSource().sendSuccess(() -> out, false);
        return 1;
    }

    private static int lastConnectionList(CommandContext<CommandSourceStack> ctx, int page) {
        try {
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) {
                ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_DISABLED)));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        var allEntries = LastConnectionManager.getAllSortedByLogin();
        int total = allEntries.size();

        if (total == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_NO_DATA_LIST)), false);
            return 1;
        }

        int pages = RpChatUi.pageCount(total);
        int current = Math.min(page, pages);
        int from = (current - 1) * RpChatUi.PAGE_SIZE;
        int to = Math.min(total, from + RpChatUi.PAGE_SIZE);

        String unknown = Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_UNKNOWN);
        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.LASTCONN_LIST_HEADER,
                        "page", String.valueOf(current), "pages", String.valueOf(pages),
                        "total", String.valueOf(total), "shown", String.valueOf(to - from)))
                .append("\n").append(RpChatUi.LINE).append("\n");

        for (int i = from; i < to; i++) {
            var e = allEntries.get(i);
            UUID uuid = e.getKey();
            LastConnectionManager.ConnectionEntry entry = e.getValue();
            String name = entry.mcName != null ? entry.mcName : uuid.toString().substring(0, 8);
            boolean shownOnline = server.getPlayerList().getPlayer(uuid) != null
                    && !VanishManager.canHide(VanishManager.Aspect.LIST, ctx.getSource(), uuid);
            String bullet = shownOnline ? "§a●" : "§7○";
            String loginStr = entry.lastLogin != null ? entry.lastLogin : unknown;
            sb.append(bullet).append(" §e").append(name).append(" §8- §7").append(loginStr).append("\n");
        }
        sb.append(RpChatUi.LINE);

        MutableComponent out = Component.literal(sb.toString());
        if (pages > 1) {
            out.append(Component.literal("\n")).append(RpChatUi.pager(ctx.getSource(), current, pages, "/rpessentials lastconnection list"));
        }
        ctx.getSource().sendSuccess(() -> out, false);
        return 1;
    }
}