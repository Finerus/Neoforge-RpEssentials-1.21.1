package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.Msg;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.moderation.VanishManager;

import java.util.ArrayList;
import java.util.List;

public class RpBlurTabCommands {

    private static final SuggestionProvider<CommandSourceStack> ONLINE_NAMES = (ctx, builder) -> {
        VanishManager.visibleTo(ctx.getSource()).forEach(p -> builder.suggest(p.getName().getString()));
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var blurtabNode = Commands.literal("blurtab")
                .requires(RpPerms.cmd(RpPerm.BLURTAB));

        var whitelistNode = Commands.literal("whitelist");
        whitelistNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ONLINE_NAMES)
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.WHITELIST, MessagesConfig.BLUR_LIST_WHITELIST, true))));
        whitelistNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            RpEssentialsConfig.WHITELIST.get().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.WHITELIST, MessagesConfig.BLUR_LIST_WHITELIST, false))));
        whitelistNode.then(Commands.literal("list").executes(ctx -> listConfig(ctx, RpEssentialsConfig.WHITELIST, MessagesConfig.BLUR_TITLE_WHITELIST)));
        blurtabNode.then(whitelistNode);

        var blacklistNode = Commands.literal("blacklist");
        blacklistNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ONLINE_NAMES)
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.BLACKLIST, MessagesConfig.BLUR_LIST_BLACKLIST, true))));
        blacklistNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            RpEssentialsConfig.BLACKLIST.get().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.BLACKLIST, MessagesConfig.BLUR_LIST_BLACKLIST, false))));
        blacklistNode.then(Commands.literal("list").executes(ctx -> listConfig(ctx, RpEssentialsConfig.BLACKLIST, MessagesConfig.BLUR_TITLE_BLACKLIST)));
        blurtabNode.then(blacklistNode);

        var alwaysVisibleNode = Commands.literal("alwaysvisible");
        alwaysVisibleNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ONLINE_NAMES)
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.ALWAYS_VISIBLE_LIST, MessagesConfig.BLUR_LIST_ALWAYS, true))));
        alwaysVisibleNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            try {
                                if (RpEssentialsConfig.ALWAYS_VISIBLE_LIST != null)
                                    RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().forEach(builder::suggest);
                            } catch (Exception ignored) {}
                            return builder.buildFuture();
                        })
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.ALWAYS_VISIBLE_LIST, MessagesConfig.BLUR_LIST_ALWAYS, false))));
        alwaysVisibleNode.then(Commands.literal("list").executes(ctx -> listConfig(ctx, RpEssentialsConfig.ALWAYS_VISIBLE_LIST, MessagesConfig.BLUR_TITLE_ALWAYS)));
        blurtabNode.then(alwaysVisibleNode);

        return blurtabNode;
    }

    private static int modifyList(CommandContext<CommandSourceStack> ctx,
                                  ModConfigSpec.ConfigValue<List<? extends String>> config,
                                  ModConfigSpec.ConfigValue<String> listKey, boolean add) {
        String listName = Msg.get(ctx.getSource(), listKey);
        String player = StringArgumentType.getString(ctx, "player");
        List<String> list = new ArrayList<>(config.get());

        if (add) {
            if (list.contains(player)) {
                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_BLUR_ALREADY, "player", player, "list", listName));
                return 0;
            }
            list.add(player);
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_BLUR_ADDED, "player", player, "list", listName), true);
        } else {
            if (!list.remove(player)) {
                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_BLUR_NOT_IN, "player", player, "list", listName));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_BLUR_REMOVED, "player", player, "list", listName), true);
        }

        config.set(list);
        config.save();
        net.rp.rpessentials.gameplay.TabListCache.reload();
        // The whitelist is part of the cached blur settings, and viewers who see everyone
        // skip known targets: both must be rebuilt for the change to apply right away.
        net.rp.rpessentials.gameplay.TabBlur.reload();
        return 1;
    }

    private static int listConfig(CommandContext<CommandSourceStack> ctx,
                                  ModConfigSpec.ConfigValue<List<? extends String>> config,
                                  ModConfigSpec.ConfigValue<String> labelKey) {
        String label = Msg.get(ctx.getSource(), labelKey);
        List<? extends String> list = config.get();
        if (list.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_BLUR_EMPTY, "label", label), false);
        } else {
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_BLUR_CONTENT,
                "label", label, "list", String.join(", ", list)), false);
        }
        return 1;
    }
}