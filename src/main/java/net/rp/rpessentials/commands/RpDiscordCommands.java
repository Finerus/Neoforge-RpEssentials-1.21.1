package net.rp.rpessentials.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.compat.DiscordWebhook;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.util.ColorHelper;

/** /rpessentials discord test: checks the webhook set in rpessentials-core.toml. */
public final class RpDiscordCommands {

    private RpDiscordCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("discord")
                .then(Commands.literal("test").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer viewer = src.getEntity() instanceof ServerPlayer p ? p : null;
                    if (DiscordWebhook.configured() && DiscordWebhook.validUrl(currentUrl())) {
                        src.sendSystemMessage(ColorHelper.parseSection(
                                ServerLang.get(viewer, MessagesConfig.DISCORD_TEST_SENDING)));
                    }
                    DiscordWebhook.test(viewer, msg -> src.sendSystemMessage(ColorHelper.parseSection(msg)));
                    return 1;
                }));
    }

    private static String currentUrl() {
        return net.rp.rpessentials.config.Cfg.get(
                net.rp.rpessentials.config.RpEssentialsConfig.DISCORD_WEBHOOK_URL, "").trim();
    }
}
