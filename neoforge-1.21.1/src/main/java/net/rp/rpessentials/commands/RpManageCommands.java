package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.commands.CommandToggles;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.util.ColorHelper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.commands.RpEssentialsCommands;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.ManageService;
import net.rp.rpessentials.moderation.VanishManager;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RpManageCommands {

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_KNOWN = (ctx, builder) -> {
        Set<String> names = new LinkedHashSet<>();
        VanishManager.visibleTo(ctx.getSource()).forEach(p -> names.add(p.getName().getString()));
        LastConnectionManager.getAllSortedByLogin().forEach(e -> {
            if (e.getValue().mcName != null
                    && !VanishManager.canHide(VanishManager.Aspect.SELECTORS, ctx.getSource(), e.getKey())) {
                names.add(e.getValue().mcName);
            }
        });
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private record Pending(UUID target, ManageService.Action action, String text, int number, long at) {}

    private static final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private static final UUID CONSOLE = new UUID(0L, 0L);
    private static final long EXPIRY_MS = 60_000L;

    /** /manage shortcut, used by the clickable buttons when /rpessentials is turned off. */
    public static void registerAliases(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build());
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var node = Commands.literal("manage").requires(RpPerms::anyManage);

        node.then(Commands.literal("confirm").executes(RpManageCommands::confirm));
        node.then(Commands.literal("cancel").executes(ctx -> {
            pending.remove(key(ctx));
            ctx.getSource().sendSuccess(() -> ColorHelper.parseSection(t(ctx, MessagesConfig.MANAGE_CANCELLED)), false);
            return 1;
        }));

        node.then(Commands.argument("player", StringArgumentType.word())
                .suggests(SUGGEST_KNOWN)
                .then(Commands.argument("action", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Arrays.stream(ManageService.Action.values())
                                    .filter(a -> a.available() && RpPerms.has(ctx.getSource(), RpPerm.manage(a)))
                                    .forEach(a -> builder.suggest(a.name().toLowerCase()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> request(ctx, ""))
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    String action = StringArgumentType.getString(ctx, "action").toLowerCase();
                                    if (action.equals("reset_all")) {
                                        builder.suggest(StringArgumentType.getString(ctx, "player"));
                                    }
                                    if (action.equals("tempban")) {
                                        builder.suggest("1d").suggest("3d").suggest("7d").suggest("12h").suggest("30m");
                                    }
                                    if (action.equals("tp_player_platform")) {
                                        if (Cfg.ready(ModerationConfig.SPEC)) {
                                            for (String p : ModerationConfig.PLATFORMS.get()) {
                                                builder.suggest(p.split(";", 2)[0].trim());
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> request(ctx, StringArgumentType.getString(ctx, "value"))))));
        return node;
    }

    private static String t(CommandContext<CommandSourceStack> ctx, ModConfigSpec.ConfigValue<String> cv, String... a) {
        return ServerLang.get(ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null, cv, a);
    }

    private static UUID key(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = RpPerms.actor(ctx.getSource());
        return p != null ? p.getUUID() : CONSOLE;
    }

    /** Checked here too: {@link ManageService} only knows the player, not the sign or command block behind them. */
    private static boolean refused(CommandContext<CommandSourceStack> ctx, ManageService.Action action) {
        if (RpPerms.has(ctx.getSource(), RpPerm.manage(action))) return false;
        ctx.getSource().sendFailure(ColorHelper.parseSection(t(ctx, MessagesConfig.MANAGE_NO_PERMISSION,
                "node", RpPerm.manage(action).node())));
        return true;
    }

    private static Component button(String label, String command) {
        return Component.literal(label).withStyle(s -> s.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static int request(CommandContext<CommandSourceStack> ctx, String value) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ManageService.Action action;
        try {
            action = ManageService.Action.valueOf(StringArgumentType.getString(ctx, "action").toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(ColorHelper.parseSection(t(ctx, MessagesConfig.MANAGE_UNKNOWN_ACTION)));
            return 0;
        }
        if (refused(ctx, action)) return 0;
        UUID target = RpEssentialsCommands.findUUIDByName(ctx.getSource().getServer(), playerName);
        if (target == null || VanishManager.canHide(VanishManager.Aspect.SELECTORS, ctx.getSource(), target)) {
            ctx.getSource().sendFailure(ColorHelper.parseSection(
                    t(ctx, MessagesConfig.MANAGE_PLAYER_NOT_FOUND, "player", playerName)));
            return 0;
        }

        String text = value.trim();
        int number = 0;
        try {
            if (action == ManageService.Action.OP) {
                number = text.isEmpty() ? 2 : Integer.parseInt(text);
                text = "";
            } else if (action == ManageService.Action.VANISH) {
                number = text.isEmpty() ? VanishManager.defaultLevel() : Integer.parseInt(text);
                text = "";
            } else if (action == ManageService.Action.TEMPBAN) {
                String[] parts = text.split("\\s+", 2);
                number = ManageService.parseDurationMinutes(parts[0]);
                if (number <= 0) {
                    ctx.getSource().sendFailure(ColorHelper.parseSection(t(ctx, MessagesConfig.MANAGE_DURATION_EXPECTED)));
                    return 0;
                }
                text = parts.length > 1 ? parts[1] : "";
            } else if (action == ManageService.Action.ABSENCE_ADD) {
                String[] parts = text.split("\\s+", 2);
                number = Integer.parseInt(parts[0]);
                text = parts.length > 1 ? parts[1] : "";
            }
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(ColorHelper.parseSection(t(ctx, MessagesConfig.MANAGE_NUMBER_EXPECTED)));
            return 0;
        }

        boolean reset = action == ManageService.Action.RESET_ALL;
        if (reset) {
            if (!text.equalsIgnoreCase(playerName)) {
                ctx.getSource().sendFailure(ColorHelper.parseSection(
                        t(ctx, MessagesConfig.MANAGE_RESET_NEEDS_NAME, "player", playerName)));
                return 0;
            }
            text = "";
        }

        pending.put(key(ctx), new Pending(target, action, text, number, System.currentTimeMillis()));

        Component confirm;
        if (reset) {
            confirm = ColorHelper.parseSection(t(ctx, MessagesConfig.MANAGE_RESET_PROMPT, "player", playerName)).copy()
                    .append(button(t(ctx, MessagesConfig.MANAGE_BTN_CONFIRM_RESET), CommandToggles.manage() + " confirm"))
                    .append(Component.literal(" "))
                    .append(button(t(ctx, MessagesConfig.MANAGE_BTN_CANCEL_GREY), CommandToggles.manage() + " cancel"));
        } else {
            String detail = (number > 0 ? " §8(" + number + ")" : "")
                    + (text.isEmpty() ? "" : " §8(" + text + ")");
            confirm = ColorHelper.parseSection(t(ctx, MessagesConfig.MANAGE_CONFIRM_PROMPT,
                            "action", action.name(), "player", playerName, "detail", detail)).copy()
                    .append(button(t(ctx, MessagesConfig.MANAGE_BTN_CONFIRM), CommandToggles.manage() + " confirm"))
                    .append(Component.literal(" "))
                    .append(button(t(ctx, MessagesConfig.MANAGE_BTN_CANCEL), CommandToggles.manage() + " cancel"));
        }
        final Component finalConfirm = confirm;
        ctx.getSource().sendSuccess(() -> finalConfirm, false);
        return 1;
    }

    private static int confirm(CommandContext<CommandSourceStack> ctx) {
        Pending p = pending.remove(key(ctx));
        if (p == null || System.currentTimeMillis() - p.at() > EXPIRY_MS) {
            ctx.getSource().sendFailure(ColorHelper.parseSection(t(ctx, MessagesConfig.MANAGE_NOTHING)));
            return 0;
        }
        if (refused(ctx, p.action())) return 0;
        ManageService.Result r = ManageService.execute(ctx.getSource().getServer(), RpPerms.actor(ctx.getSource()),
                p.target(), p.action(), p.text(), p.number());
        if (r.ok()) ctx.getSource().sendSuccess(() -> Component.literal(r.message()), false);
        else ctx.getSource().sendFailure(Component.literal(r.message()));
        return r.ok() ? 1 : 0;
    }
}