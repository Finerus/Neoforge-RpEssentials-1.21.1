package net.rp.rpessentials.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.VanishManager;

import java.util.Map;
import java.util.UUID;

public class RpVanishCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return node();
    }

    public static void registerAliases(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(node());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("vanish")
                .requires(RpEssentialsPermissions::isStaffSource)
                .executes(ctx -> run(ctx, ctx.getSource().getPlayerOrException(), -1))
                .then(Commands.literal("list").executes(RpVanishCommands::list))
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
                        .executes(ctx -> run(ctx, ctx.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(ctx, "level")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> run(ctx, EntityArgument.getPlayer(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> run(ctx, EntityArgument.getPlayer(ctx, "player"), -1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
                                .executes(ctx -> run(ctx, EntityArgument.getPlayer(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "level")))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, ServerPlayer target, int requested) {
        CommandSourceStack src = ctx.getSource();
        if (!VanishManager.enabled()) {
            src.sendFailure(Component.literal("§c[Vanish] Vanish is disabled in the config."));
            return 0;
        }
        int own = src.getEntity() instanceof ServerPlayer p ? VanishManager.levelOf(p) : 4;
        UUID id = target.getUUID();
        String name = target.getName().getString();
        boolean on = VanishManager.isVanished(id);

        if (on && VanishManager.getLevel(id) > own) {
            src.sendFailure(Component.literal("§c[Vanish] " + name + " is hidden above your OP level."));
            return 0;
        }
        if (on && requested < 0) {
            VanishManager.clear(src.getServer(), target);
            feedback(src, target, "§a[Vanish] §f" + name + " §7is now visible.");
            return 1;
        }
        int level = Math.min(requested >= 0 ? requested : VanishManager.defaultLevel(), own);
        VanishManager.set(src.getServer(), target, level);
        feedback(src, target, "§a[Vanish] §f" + name + " §7is now vanished, hidden from OP level §e"
                + level + " §7and below.");
        return 1;
    }

    private static void feedback(CommandSourceStack src, ServerPlayer target, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
        if (src.getEntity() != target) target.sendSystemMessage(Component.literal(msg));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StringBuilder sb = new StringBuilder("§6Vanished players\n" + RpChatUi.LINE + "\n");
        int count = 0;
        for (Map.Entry<UUID, Integer> e : VanishManager.getAll().entrySet()) {
            if (src.getEntity() instanceof ServerPlayer v && VanishManager.isHiddenFrom(v, e.getKey())) continue;
            sb.append("§f").append(LastConnectionManager.resolveName(src.getServer(), e.getKey()))
                    .append(" §8| §7level §e").append(e.getValue()).append("\n");
            count++;
        }
        if (count == 0) sb.append("§8None\n");
        sb.append(RpChatUi.LINE);
        RpChatUi.sendFormatted(ctx, sb.toString());
        return 1;
    }
}