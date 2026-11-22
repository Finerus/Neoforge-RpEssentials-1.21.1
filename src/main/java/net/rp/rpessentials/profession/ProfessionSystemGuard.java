package net.rp.rpessentials.profession;

import com.mojang.brigadier.ParseResults;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.CommandEvent;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.ProfessionConfig;

/**
 * While the profession system is turned off, vanilla commands cannot create a license item
 * (/give, /item ... with rpessentials:license): they are stopped with a dedicated message.
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public final class ProfessionSystemGuard {

    private ProfessionSystemGuard() {}

    private static final String LICENSE_ID = RpEssentials.MODID + ":license";

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (ProfessionConfig.enabled()) return;
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        String input = parse.getReader().getString().trim();
        if (input.startsWith("/")) input = input.substring(1);
        String first = input.split("\s+", 2)[0];
        if (first.startsWith("minecraft:")) first = first.substring("minecraft:".length());
        if (!first.equals("give") && !first.equals("item")) return;
        if (!input.contains(LICENSE_ID)) return;
        event.setCanceled(true);
        parse.getContext().getSource().sendFailure(Component.translatableWithFallback(
                "rpessentials.professions.disabled.give",
                "The license cannot be given: the profession system is turned off on this server.")
                .withStyle(ChatFormatting.RED));
    }
}
