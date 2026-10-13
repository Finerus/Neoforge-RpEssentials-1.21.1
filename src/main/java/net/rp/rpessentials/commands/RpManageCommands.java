package net.rp.rpessentials.commands;

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
import net.rp.rpessentials.RpEssentialsCommands;
import net.rp.rpessentials.RpEssentialsPermissions;
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

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var node = Commands.literal("manage").requires(RpEssentialsPermissions::isStaffSource);

        node.then(Commands.literal("confirm").executes(RpManageCommands::confirm));
        node.then(Commands.literal("cancel").executes(ctx -> {
            pending.remove(key(ctx));
            ctx.getSource().sendSuccess(() -> Component.literal("§7[Manage] Cancelled."), false);
            return 1;
        }));

        node.then(Commands.argument("player", StringArgumentType.word())
                .suggests(SUGGEST_KNOWN)
                .then(Commands.argument("action", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Arrays.stream(ManageService.Action.values())
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
                                    if (action.equals("tp_player_platform")) {
                                        try {
                                            for (String p : ModerationConfig.PLATFORMS.get()) {
                                                builder.suggest(p.split(";", 2)[0].trim());
                                            }
                                        } catch (IllegalStateException ignored) {}
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> request(ctx, StringArgumentType.getString(ctx, "value"))))));
        return node;
    }

    private static UUID key(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : CONSOLE;
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
            ctx.getSource().sendFailure(Component.literal("§c[Manage] Unknown action."));
            return 0;
        }
        UUID target = RpEssentialsCommands.findUUIDByName(ctx.getSource().getServer(), playerName);
        if (target == null || VanishManager.canHide(VanishManager.Aspect.SELECTORS, ctx.getSource(), target)) {
            ctx.getSource().sendFailure(Component.literal("§c[Manage] Player not found: " + playerName));
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
            } else if (action == ManageService.Action.ABSENCE_ADD) {
                String[] parts = text.split("\\s+", 2);
                number = Integer.parseInt(parts[0]);
                text = parts.length > 1 ? parts[1] : "";
            }
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(Component.literal("§c[Manage] A number is expected."));
            return 0;
        }

        boolean reset = action == ManageService.Action.RESET_ALL;
        if (reset) {
            if (!text.equalsIgnoreCase(playerName)) {
                ctx.getSource().sendFailure(Component.literal(
                        "§c[Manage] Full reset requires the player name as confirmation: /rpessentials manage "
                                + playerName + " reset_all " + playerName));
                return 0;
            }
            text = "";
        }

        pending.put(key(ctx), new Pending(target, action, text, number, System.currentTimeMillis()));

        final String shownText = text;
        final int shownNumber = number;
        Component confirm = reset
                ? Component.literal("§c§l[Manage] FULL RESET of §e§l" + playerName
                        + "§c§l: inventory, ender chest, role, professions, nickname, absence, OP and whitelist. ")
                .append(button("§c§l[CONFIRM FULL RESET]", "/rpessentials manage confirm"))
                .append(Component.literal(" "))
                .append(button("§7[Cancel]", "/rpessentials manage cancel"))
                : Component.literal("§6[Manage] §7Confirm §f" + action.name() + " §7on §e" + playerName
                        + (shownNumber > 0 ? " §8(" + shownNumber + ")" : "")
                        + (shownText.isEmpty() ? "" : " §8(" + shownText + ")") + "§7? ")
                .append(button("§a[Confirm]", "/rpessentials manage confirm"))
                .append(Component.literal(" "))
                .append(button("§c[Cancel]", "/rpessentials manage cancel"));
        ctx.getSource().sendSuccess(() -> confirm, false);
        return 1;
    }

    private static int confirm(CommandContext<CommandSourceStack> ctx) {
        Pending p = pending.remove(key(ctx));
        if (p == null || System.currentTimeMillis() - p.at() > EXPIRY_MS) {
            ctx.getSource().sendFailure(Component.literal("§c[Manage] Nothing to confirm."));
            return 0;
        }
        ManageService.Result r = ManageService.execute(ctx.getSource().getServer(), ctx.getSource().getPlayer(),
                p.target(), p.action(), p.text(), p.number());
        if (r.ok()) ctx.getSource().sendSuccess(() -> Component.literal(r.message()), false);
        else ctx.getSource().sendFailure(Component.literal(r.message()));
        return r.ok() ? 1 : 0;
    }
}