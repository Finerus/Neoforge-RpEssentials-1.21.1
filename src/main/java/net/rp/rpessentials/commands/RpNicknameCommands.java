package net.rp.rpessentials.commands;

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
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.ChatConfig;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.identity.RpEssentialsChatFormatter;
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

        nickNode.then(Commands.literal("list").executes(RpNicknameCommands::listNicknames));
        return nickNode;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildWhois() {
        return Commands.literal("whois")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(RpNicknameCommands::whoisCommand));
    }

    /** Registers /whois, /list, /colors standalone aliases */
    public static void registerAliases(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("whois")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(RpNicknameCommands::whoisCommand)));

        dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("list"));
        dispatcher.register(Commands.literal("list").executes(RpNicknameCommands::playerList));

        dispatcher.register(Commands.literal("colors")
                .executes(ctx -> {
                    if (ChatConfig.ENABLE_COLORS_COMMAND != null && !ChatConfig.ENABLE_COLORS_COMMAND.get()) {
                        ctx.getSource().sendFailure(Component.literal("§cColors command is disabled."));
                        return 0;
                    }
                    return showColors(ctx);
                }));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildHelp() {
        return Commands.literal("help")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()))
                .executes(ctx -> showHelpPublic(ctx, true));
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    private static int setNickname(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String nickname = StringArgumentType.getString(ctx, "nickname");
            String formattedNickname = nickname.replace("&", "§");

            NicknameManager.setNickname(target.getUUID(), formattedNickname);
            target.getServer().getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                            List.of(target)));

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a[RpEssentials] Nickname for §f" + target.getName().getString() + "§a set to: " + formattedNickname), true);
            target.sendSystemMessage(Component.literal("§aYour nickname has been changed to: " + formattedNickname));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError while setting nickname."));
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

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a[RpEssentials] Nickname for §f" + target.getName().getString() + "§a reset."), true);
            target.sendSystemMessage(Component.literal("§aYour nickname has been reset."));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError while resetting nickname."));
            return 0;
        }
    }

    private static int listNicknames(CommandContext<CommandSourceStack> ctx) {
        int count = NicknameManager.count();
        if (count == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] No active nicknames."), false);
            return 1;
        }

        StringBuilder list = new StringBuilder("§6╔═══════════════════════════════════╗\n");
        list.append("§6║ §e§lACTIVE NICKNAMES §6(§e").append(count).append("§6)\n");
        list.append("§6╠═══════════════════════════════════╣\n");

        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player -> {
            if (NicknameManager.hasNickname(player.getUUID())) {
                list.append("§6║ §f").append(player.getName().getString())
                        .append(" §7→ ").append(NicknameManager.getNickname(player.getUUID())).append("\n");
            }
        });

        list.append("§6╚═══════════════════════════════════╝");
        ctx.getSource().sendSuccess(() -> Component.literal(list.toString()), false);
        return 1;
    }

    static int whoisCommand(CommandContext<CommandSourceStack> ctx) {
        String searchNick = StringArgumentType.getString(ctx, "nickname");
        var server = ctx.getSource().getServer();
        Map<UUID, String> allNicknames = NicknameManager.getAllNicknames();
        List<MutableComponent> results = new ArrayList<>();

        for (Map.Entry<UUID, String> entry : allNicknames.entrySet()) {
            if (entry.getValue() == null) continue;
            String cleanNick = entry.getValue()
                    .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                    .replaceAll("&[0-9a-fk-orA-FK-OR]", "");
            if (!cleanNick.equalsIgnoreCase(searchNick)) continue;

            UUID uuid = entry.getKey();
            String mcName = "Offline";
            try {
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != null) {
                    mcName = online.getName().getString();
                } else {
                    try {
                        var cache = server.getProfileCache();
                        if (cache != null) {
                            var profile = cache.get(uuid);
                            if (profile != null && profile.isPresent()) mcName = profile.get().getName();
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            final String finalMcName = mcName;
            final String finalNick = entry.getValue();
            final UUID finalUuid = uuid;

            MutableComponent uuidComponent = Component.literal("§8" + finalUuid)
                    .withStyle(style -> style
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Click to open NameMC")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://namemc.com/profile/" + finalUuid)));

            results.add(Component.literal("§7MC: §f" + finalMcName + " §8| §7UUID: ")
                    .append(uuidComponent)
                    .append(Component.literal(" §8| §7Nick: §r" + finalNick)));
        }

        if (!results.isEmpty()) {
            for (Map.Entry<UUID, String> entry : allNicknames.entrySet()) {
                if (entry.getValue() == null) continue;
                String cleanNick = entry.getValue()
                        .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                        .replaceAll("&[0-9a-fk-orA-FK-OR]", "");
                if (!cleanNick.equalsIgnoreCase(searchNick)) continue;
                List<NoteManager.NoteEntry> notes = NoteManager.getNotes(entry.getKey());
                if (!notes.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6§l[" + notes.size() + " staff note(s)]"), false);
                    for (NoteManager.NoteEntry n : notes) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "§8  #" + n.id + " §7[" + n.timestamp + "] §fby §e" + n.authorName + "§7: §f" + n.text), false);
                    }
                }
                break;
            }
        }

        if (results.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.WHOIS_NOT_FOUND, "nick", searchNick)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(MessagesConfig.get(MessagesConfig.WHOIS_RESULTS_HEADER, "nick", searchNick)), false);
        for (MutableComponent r : results) ctx.getSource().sendSuccess(() -> r, false);
        return 1;
    }

    private static int playerList(CommandContext<CommandSourceStack> ctx) {
        var players = ctx.getSource().getServer().getPlayerList().getPlayers();
        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.PLAYERLIST_HEADER, "count", String.valueOf(players.size())));
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            String nick = NicknameManager.getNickname(p.getUUID());
            if (nick != null) sb.append(nick).append(" §8(").append(p.getName().getString()).append(")§r");
            else sb.append("§f").append(p.getName().getString());
            if (i < players.size() - 1) sb.append("§7, ");
        }
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return players.size();
    }

    private static int showColors(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> RpEssentialsChatFormatter.getColorsHelp(), false);
        return 1;
    }

    public static int showHelpPublic(CommandContext<CommandSourceStack> ctx, boolean isStaff) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═══════════════════════════════════╗\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_TITLE)).append("\n");
        sb.append("§6╠═══════════════════════════════════╣\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_CMD_LIST)).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_CMD_SCHEDULE)).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_CMD_MSG)).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_CMD_REPLY)).append("\n");
        if (isStaff) {
            sb.append("§6╠═══════════════════════════════════╣\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_STAFF_SECTION)).append("\n");
            sb.append("§6║ §e/rpessentials nick §8<player> <nick>\n");
            sb.append("§6║ §e/rpessentials license give/revoke/list\n");
            sb.append("§6║ §e/rpessentials staff tp/gamemode/effect\n");
            sb.append("§6║ §e/whois §8<nick>\n");
            sb.append("§6║ §e/rpessentials config status/reload\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_DEATHRP_ENABLE)).append("\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_DEATHRP_PLAYER)).append("\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_DEATHRP_RESET)).append("\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_DEATHRP_STATUS)).append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
