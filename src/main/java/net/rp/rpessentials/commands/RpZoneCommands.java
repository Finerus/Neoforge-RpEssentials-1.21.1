package net.rp.rpessentials.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.Msg;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.WorldBorderManager;
import net.rp.rpessentials.config.MessagesConfig;

import java.util.List;

public class RpZoneCommands {

    private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, builder) -> {
        WorldBorderManager.zoneNames().forEach(builder::suggest);
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var node = Commands.literal("zone").requires(RpEssentialsPermissions::isStaffSource);
        node.then(Commands.literal("list").executes(RpZoneCommands::list));
        node.then(Commands.literal("show")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(NAMES)
                        .executes(RpZoneCommands::show)));
        node.then(Commands.literal("hide").executes(ctx -> {
            WorldBorderManager.hideZone(ctx.getSource().getPlayerOrException().getUUID());
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_ZONE_HIDDEN), false);
            return 1;
        }));
        return node;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<String> names = WorldBorderManager.zoneNames();
        src.sendSuccess(() -> names.isEmpty()
                ? Msg.c(src, MessagesConfig.STAFF_ZONE_NONE)
                : Msg.c(src, MessagesConfig.STAFF_ZONE_LIST, "zones", String.join("§7, §f", names)), false);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        if (!WorldBorderManager.showZone(p, name)) {
            src.sendFailure(Msg.c(src, MessagesConfig.STAFF_ZONE_UNKNOWN, "name", name));
            return 0;
        }
        src.sendSuccess(() -> Msg.c(src, MessagesConfig.STAFF_ZONE_SHOWING, "name", name), false);
        return 1;
    }
}
