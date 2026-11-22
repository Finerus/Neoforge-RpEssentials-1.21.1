package net.rp.rpessentials.commands;

import net.rp.rpessentials.config.Cfg;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.rp.rpessentials.RpEssentials;

/**
 * Unprefixed versions of the /rpessentials and /rp subcommands (/warn, /afk...).
 * They are always registered and only become usable when their config option is on,
 * so a config reload is enough. The prefixed versions always keep working.
 * A subcommand whose name is already taken (vanilla /help, /list, another mod) is skipped.
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public final class CommandToggles {

    private CommandToggles() {}

    /** Top-level names registered by RpEssentials itself (shortcuts such as /vanish, /manage). */
    private static final Set<String> OWN = new HashSet<>();
    /** Skipped unprefixed commands: [prefix, name]. */
    private static final List<String[]> CONFLICTS = new ArrayList<>();

    private static boolean opt(ForgeConfigSpec.BooleanValue v) {
        return Cfg.get(v, false);
    }

    public static boolean flatStaff(CommandSourceStack src) {
        return opt(RpEssentialsConfig.UNPREFIXED_STAFF_COMMANDS);
    }

    public static boolean flatRp(CommandSourceStack src) {
        return opt(RpEssentialsConfig.UNPREFIXED_RP_COMMANDS);
    }

    /** Root of the manage commands for clickable buttons. */
    public static String manage() {
        return "/rpessentials manage";
    }

    // ── Registration ────────────────────────────────────────────────────────

    public static Set<String> names(CommandDispatcher<CommandSourceStack> dispatcher) {
        Set<String> out = new HashSet<>();
        for (CommandNode<CommandSourceStack> n : dispatcher.getRoot().getChildren()) out.add(n.getName());
        return out;
    }

    /** Remembers the top-level names added since {@code before} as RpEssentials' own. */
    public static void markOwn(CommandDispatcher<CommandSourceStack> dispatcher, Set<String> before) {
        for (String n : names(dispatcher)) if (!before.contains(n)) OWN.add(n);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CONFLICTS.clear();
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        flatten(dispatcher, "rpessentials", CommandToggles::flatStaff);
        flatten(dispatcher, "rp", CommandToggles::flatRp);
        if (!CONFLICTS.isEmpty()) {
            RpEssentials.LOGGER.info("[RpEssentials] Unprefixed commands skipped (name already taken): {}",
                    String.join(", ", CONFLICTS.stream().map(c -> "/" + c[1] + " (/" + c[0] + ")").toList()));
        }
    }

    private static void flatten(CommandDispatcher<CommandSourceStack> dispatcher, String prefix,
                                Predicate<CommandSourceStack> toggle) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild(prefix);
        if (root == null) return;
        for (CommandNode<CommandSourceStack> child : new ArrayList<>(root.getChildren())) {
            if (!(child instanceof LiteralCommandNode<CommandSourceStack>)) continue;
            String name = child.getName();
            if (dispatcher.getRoot().getChild(name) != null) {
                if (!OWN.contains(name)) CONFLICTS.add(new String[]{prefix, name});
                continue;
            }
            LiteralArgumentBuilder<CommandSourceStack> b = Commands.literal(name)
                    .requires(src -> toggle.test(src) && root.getRequirement().test(src)
                            && child.getRequirement().test(src));
            if (child.getCommand() != null) b.executes(child.getCommand());
            if (child.getRedirect() != null) b.forward(child.getRedirect(), child.getRedirectModifier(), child.isFork());
            for (CommandNode<CommandSourceStack> c : child.getChildren()) b.then(c);
            dispatcher.register(b);
        }
    }

    /** Skipped unprefixed commands whose option is on, as "/name (/prefix name)". */
    public static List<String> activeConflicts() {
        List<String> out = new ArrayList<>();
        boolean staff = opt(RpEssentialsConfig.UNPREFIXED_STAFF_COMMANDS);
        boolean rp = opt(RpEssentialsConfig.UNPREFIXED_RP_COMMANDS);
        for (String[] c : CONFLICTS) {
            if (c[0].equals("rp") ? rp : staff) out.add("/" + c[1] + " (/" + c[0] + " " + c[1] + ")");
        }
        return out;
    }

    /** Sends the command tree again so suggestions follow the config without a relog. */
    public static void resend(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
    }
}
