package net.rp.rpessentials.commands;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.compat.DiscordWebhook;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.util.ColorHelper;

/** /rpessentials discord test: checks the bot or the webhook set in rpessentials-core.toml. */
public final class RpDiscordCommands {

    private RpDiscordCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("discord")
                .requires(RpPerms.cmd(RpPerm.DISCORD))
                .then(Commands.literal("test").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer viewer = src.getEntity() instanceof ServerPlayer p ? p : null;
                    if (DiscordWebhook.ready()) {
                        src.sendSystemMessage(ColorHelper.parseSection(
                                ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_SENDING)));
                    }
                    DiscordWebhook.test(viewer, msg -> src.sendSystemMessage(ColorHelper.parseSection(msg)));
                    return 1;
                }));
    }
}
