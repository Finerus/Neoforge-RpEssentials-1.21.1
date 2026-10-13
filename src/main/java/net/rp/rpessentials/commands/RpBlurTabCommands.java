package net.rp.rpessentials.commands;

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
                .requires(source -> source.hasPermission(2));

        var whitelistNode = Commands.literal("whitelist");
        whitelistNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ONLINE_NAMES)
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.WHITELIST, "whitelist", true))));
        whitelistNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            RpEssentialsConfig.WHITELIST.get().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.WHITELIST, "whitelist", false))));
        whitelistNode.then(Commands.literal("list").executes(ctx -> listConfig(ctx, RpEssentialsConfig.WHITELIST, "Whitelist")));
        blurtabNode.then(whitelistNode);

        var blacklistNode = Commands.literal("blacklist");
        blacklistNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ONLINE_NAMES)
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.BLACKLIST, "blacklist", true))));
        blacklistNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            RpEssentialsConfig.BLACKLIST.get().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.BLACKLIST, "blacklist", false))));
        blacklistNode.then(Commands.literal("list").executes(ctx -> listConfig(ctx, RpEssentialsConfig.BLACKLIST, "Blacklist (always hidden)")));
        blurtabNode.then(blacklistNode);

        var alwaysVisibleNode = Commands.literal("alwaysvisible");
        alwaysVisibleNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ONLINE_NAMES)
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.ALWAYS_VISIBLE_LIST, "Always Visible list", true))));
        alwaysVisibleNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            try {
                                if (RpEssentialsConfig.ALWAYS_VISIBLE_LIST != null)
                                    RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().forEach(builder::suggest);
                            } catch (Exception ignored) {}
                            return builder.buildFuture();
                        })
                        .executes(ctx -> modifyList(ctx, RpEssentialsConfig.ALWAYS_VISIBLE_LIST, "Always Visible list", false))));
        alwaysVisibleNode.then(Commands.literal("list").executes(ctx -> listConfig(ctx, RpEssentialsConfig.ALWAYS_VISIBLE_LIST, "Always Visible (always shown in TabList)")));
        blurtabNode.then(alwaysVisibleNode);

        return blurtabNode;
    }

    private static int modifyList(CommandContext<CommandSourceStack> ctx,
                                  ModConfigSpec.ConfigValue<List<? extends String>> config,
                                  String listName, boolean add) {
        String player = StringArgumentType.getString(ctx, "player");
        List<String> list = new ArrayList<>(config.get());

        if (add) {
            if (list.contains(player)) {
                ctx.getSource().sendFailure(Component.literal("§c[RpEssentials] " + player + " is already in " + listName + "."));
                return 0;
            }
            list.add(player);
            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] " + player + " added to " + listName + "."), true);
        } else {
            if (!list.remove(player)) {
                ctx.getSource().sendFailure(Component.literal("§c[RpEssentials] " + player + " is not in " + listName + "."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] " + player + " removed from " + listName + "."), true);
        }

        config.set(list);
        config.save();
        net.rp.rpessentials.TabListCache.reload();
        return 1;
    }

    private static int listConfig(CommandContext<CommandSourceStack> ctx,
                                  ModConfigSpec.ConfigValue<List<? extends String>> config,
                                  String label) {
        List<? extends String> list = config.get();
        if (list.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] " + label + " is empty."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] " + label + ": §f" + String.join(", ", list)), false);
        }
        return 1;
    }
}