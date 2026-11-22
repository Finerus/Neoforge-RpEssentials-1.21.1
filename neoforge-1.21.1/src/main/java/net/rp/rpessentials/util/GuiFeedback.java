package net.rp.rpessentials.util;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.config.MessagesConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

public final class GuiFeedback {

    private static final int MAX_LINES = 12;
    private static final int MAX_HOVER = 40;

    private GuiFeedback() {}

    /** Legacy form: raw title (prefixed with "§a[RpEssentials] §f") and the same details for every viewer. */
    public static void report(ServerPlayer actor, String title, List<String> details, boolean alertOtherStaff) {
        send(actor, p -> "§a[RpEssentials] §f" + title, p -> details, alertOtherStaff);
    }

    /**
     * Translated form: the title comes from MessagesConfig and the details are built for each viewer
     * (viewer is null for the console log line).
     */
    public static void report(ServerPlayer actor, ModConfigSpec.ConfigValue<String> title, String[] titleArgs,
                              Function<ServerPlayer, List<String>> details, boolean alertOtherStaff) {
        send(actor, p -> ServerLang.get(p, title, titleArgs), details, alertOtherStaff);
    }

    private static void send(ServerPlayer actor, Function<ServerPlayer, String> title,
                             Function<ServerPlayer, List<String>> details, boolean alertOtherStaff) {
        actor.sendSystemMessage(build(actor, title.apply(actor), details.apply(actor)));

        if (alertOtherStaff && actor.getServer() != null) {
            for (ServerPlayer p : actor.getServer().getPlayerList().getPlayers()) {
                if (!p.getUUID().equals(actor.getUUID()) && RpPerms.has(p, RpPerm.NOTIFY)) {
                    p.sendSystemMessage(build(p, title.apply(p), details.apply(p)));
                }
            }
        }
        RpEssentials.LOGGER.info("[GUI] {} | {} | {}", actor.getName().getString(),
                strip(title.apply(null)), strip(String.join(" ; ", details.apply(null))));
    }

    private static MutableComponent build(ServerPlayer viewer, String title, List<String> details) {
        MutableComponent msg = Component.literal(title);
        int shown = 0;
        for (String d : details) {
            if (shown >= MAX_LINES) {
                msg.append(withTooltip(viewer, Component.literal("\n  " + ServerLang.get(viewer, MessagesConfig.GUI_MORE,
                        "count", String.valueOf(details.size() - shown))), details));
                break;
            }
            msg.append(Component.literal("\n  " + d));
            shown++;
        }
        return msg;
    }

    public static MutableComponent withTooltip(MutableComponent component, List<String> full) {
        return withTooltip(null, component, full);
    }

    public static MutableComponent withTooltip(ServerPlayer viewer, MutableComponent component, List<String> full) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(full.size(), MAX_HOVER);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append("\n");
            sb.append(full.get(i));
        }
        if (full.size() > MAX_HOVER) {
            sb.append("\n").append(ServerLang.get(viewer, MessagesConfig.GUI_MORE,
                    "count", String.valueOf(full.size() - MAX_HOVER)));
        }
        final String tip = sb.toString();
        return component.withStyle(s -> s.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tip))));
    }

    public static List<String> diff(Collection<String> before, Collection<String> after) {
        List<String> out = new ArrayList<>();
        for (String s : before) if (!after.contains(s)) out.add("§c- §7" + s);
        for (String s : after) if (!before.contains(s)) out.add("§a+ §7" + s);
        return out;
    }

    public static String strip(String s) {
        return s.replaceAll("§.", "");
    }
}
