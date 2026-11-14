package net.rp.rpessentials.commands;

import net.rp.rpessentials.lang.Msg;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.config.ChatConfig;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.identity.RpEssentialsChatFormatter;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.NoteManager;

import java.util.*;

public class RpNicknameCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> buildNick() {
        var nickNode = Commands.literal("nick")
                .requires(source -> source.hasPermission(2));

        nickNode.then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(RpNicknameCommands::setNickname))
                .executes(RpNicknameCommands::resetNickname));

        nickNode.then(Commands.literal("list")
                .executes(ctx -> listNicknames(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> listNicknames(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
        return nickNode;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildWhois() {
        return Commands.literal("whois")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            NicknameManager.getAllNicknames().values().stream()
                                    .filter(Objects::nonNull)
                                    .map(NicknameManager::plain)
                                    .filter(n -> !n.isEmpty())
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(RpNicknameCommands::whoisCommand));
    }

    /** Registers /whois, /list, /colors standalone aliases */
    public static void registerAliases(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("whois")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            NicknameManager.getAllNicknames().values().stream()
                                    .filter(Objects::nonNull)
                                    .map(NicknameManager::plain)
                                    .filter(n -> !n.isEmpty())
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(RpNicknameCommands::whoisCommand)));

        dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("list"));
        dispatcher.register(Commands.literal("list").executes(RpNicknameCommands::playerList));

        dispatcher.register(Commands.literal("colors")
                .executes(ctx -> {
                    if (ChatConfig.ENABLE_COLORS_COMMAND != null && !ChatConfig.ENABLE_COLORS_COMMAND.get()) {
                        ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_NICK_COLORS_OFF));
                        return 0;
                    }
                    return showColors(ctx);
                }));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildHelp() {
        return Commands.literal("help")
                .executes(ctx -> {
                    boolean isStaff = RpEssentialsPermissions.isStaff(ctx.getSource().getPlayer());
                    return showHelpPublic(ctx, isStaff);
                });
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    private static int setNickname(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String nickname = StringArgumentType.getString(ctx, "nickname");
            String formattedNickname = nickname.replace("&", "§");
            String err = NicknameManager.rejection(target.getUUID(), formattedNickname, true, ctx.getSource().getPlayer());
            if (err != null) {
                ctx.getSource().sendFailure(ColorHelper.parseColors(err));
                return 0;
            }

            NicknameManager.setNickname(target.getUUID(), formattedNickname);
            target.getServer().getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                            List.of(target)));

            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_NICK_SET,
                    "player", target.getName().getString(), "nick", formattedNickname), true);
            target.sendSystemMessage(Msg.c(target, MessagesConfig.STAFF_NICK_SET_TARGET, "nick", formattedNickname));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_NICK_SET_ERROR));
            return 0;
        }
    }

    private static int resetNickname(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            NicknameManager.removeNickname(target.getUUID());
            target.getServer().getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                            List.of(target)));

            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_NICK_RESET,
                    "player", target.getName().getString()), true);
            target.sendSystemMessage(Msg.c(target, MessagesConfig.STAFF_NICK_RESET_TARGET));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_NICK_RESET_ERROR));
            return 0;
        }
    }

    private record NickRow(String realName, String nick, boolean online) {}

    private static int listNicknames(CommandContext<CommandSourceStack> ctx, int page) {
        Map<UUID, String> all = NicknameManager.getAllNicknames();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_NICK_LIST_EMPTY), false);
            return 1;
        }

        var server = ctx.getSource().getServer();
        List<NickRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, String> e : all.entrySet()) {
            rows.add(new NickRow(LastConnectionManager.resolveName(server, e.getKey()), e.getValue(),
                    server.getPlayerList().getPlayer(e.getKey()) != null));
        }
        rows.sort(Comparator.comparing(NickRow::realName, String.CASE_INSENSITIVE_ORDER));

        int pages = RpChatUi.pageCount(rows.size());
        int current = Math.min(page, pages);
        int from = (current - 1) * RpChatUi.PAGE_SIZE;
        int to = Math.min(rows.size(), from + RpChatUi.PAGE_SIZE);

        StringBuilder sb = new StringBuilder();
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.STAFF_NICK_LIST_HEADER, "page", String.valueOf(current),
                        "pages", String.valueOf(pages), "total", String.valueOf(rows.size()))).append("\n").append(RpChatUi.LINE).append("\n");
        for (int i = from; i < to; i++) {
            NickRow r = rows.get(i);
            sb.append(r.online() ? "§a● " : "§7○ ").append("§f").append(r.realName())
                    .append(" §8> §r").append(r.nick()).append("§r\n");
        }
        sb.append(RpChatUi.LINE);

        MutableComponent out = ColorHelper.parseSection(sb.toString()).copy();
        if (pages > 1) {
            out.append(Component.literal("\n")).append(RpChatUi.pager(ctx.getSource(), current, pages, "/rpessentials nick list"));
        }
        ctx.getSource().sendSuccess(() -> out, false);
        return 1;
    }

    static int whoisCommand(CommandContext<CommandSourceStack> ctx) {
        String searchNick = StringArgumentType.getString(ctx, "nickname");
        String searchPlain = NicknameManager.plain(searchNick);
        var server = ctx.getSource().getServer();
        Map<UUID, String> allNicknames = NicknameManager.getAllNicknames();
        List<MutableComponent> results = new ArrayList<>();

        for (Map.Entry<UUID, String> entry : allNicknames.entrySet()) {
            if (entry.getValue() == null) continue;
            if (!NicknameManager.plain(entry.getValue()).equalsIgnoreCase(searchPlain)) continue;

            final UUID finalUuid = entry.getKey();
            final String finalNick = entry.getValue();
            final String finalMcName = LastConnectionManager.resolveName(server, finalUuid);

            MutableComponent uuidComponent = Component.literal("§8" + finalUuid)
                    .withStyle(style -> style
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ColorHelper.parseSection(Msg.get(ctx.getSource(), MessagesConfig.STAFF_NICK_WHOIS_HOVER))))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://namemc.com/profile/" + finalUuid)));

            results.add(ColorHelper.parseSection(Msg.get(ctx.getSource(), MessagesConfig.STAFF_NICK_WHOIS_MC, "player", finalMcName)).copy()
                    .append(uuidComponent)
                    .append(ColorHelper.parseSection(Msg.get(ctx.getSource(), MessagesConfig.STAFF_NICK_WHOIS_NICK, "nick", finalNick))));
        }

        if (results.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WHOIS_NOT_FOUND, "nick", searchNick)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(Msg.get(ctx.getSource(), MessagesConfig.WHOIS_RESULTS_HEADER, "nick", searchNick)), false);
        for (MutableComponent r : results) ctx.getSource().sendSuccess(() -> r, false);
        return 1;
    }

    private static int playerList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var players = src.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> !net.rp.rpessentials.moderation.VanishManager.canHide(
                        net.rp.rpessentials.moderation.VanishManager.Aspect.LIST, src, p.getUUID()))
                .toList();
        StringBuilder sb = new StringBuilder();
        boolean hideReal = false;
        try { hideReal = ChatConfig.HIDE_REAL_NAMES.get() && !RpEssentialsPermissions.isStaffSource(src); }
        catch (IllegalStateException ignored) {}
        sb.append(Msg.get(ctx.getSource(), MessagesConfig.PLAYERLIST_HEADER, "count", String.valueOf(players.size())));
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            String nick = NicknameManager.getNickname(p.getUUID());
            if (nick != null) {
                sb.append(nick);
                if (!hideReal) sb.append(" §8(").append(p.getName().getString()).append(")");
                sb.append("§r");
            }
            else sb.append("§f").append(p.getName().getString());
            if (i < players.size() - 1) sb.append("§7, ");
        }
        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return players.size();
    }

    private static int showColors(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> RpEssentialsChatFormatter.getColorsHelp(ctx.getSource().getPlayer()), false);
        return 1;
    }

    public static int showHelpPublic(CommandContext<CommandSourceStack> ctx, boolean isStaff) {
        StringBuilder sb = new StringBuilder();
        CommandSourceStack src = ctx.getSource();
        sb.append(Msg.get(src, MessagesConfig.STAFF_HELP_TITLE)).append("\n");
        sb.append("§6§m                              §r\n");
        sb.append(Msg.get(src, MessagesConfig.STAFF_HELP_PUBLIC)).append("\n");
        if (isStaff) {
            sb.append("§6§m                              §r\n");
            sb.append(Msg.get(src, MessagesConfig.STAFF_HELP_STAFF_TITLE)).append("\n");
            sb.append(Msg.get(src, MessagesConfig.STAFF_HELP_STAFF)).append("\n");
        }
        sb.append("§6§m                              §r");
        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }
}
