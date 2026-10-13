package net.rp.rpessentials;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class GuiFeedback {

    private static final int MAX_LINES = 12;
    private static final int MAX_HOVER = 40;

    private GuiFeedback() {}

    public static void report(ServerPlayer actor, String title, List<String> details, boolean alertOtherStaff) {
        MutableComponent msg = Component.literal("§a[RpEssentials] §f" + title);
        int shown = 0;
        for (String d : details) {
            if (shown >= MAX_LINES) {
                msg.append(withTooltip(Component.literal("\n  §8... and " + (details.size() - shown) + " more"), details));
                break;
            }
            msg.append(Component.literal("\n  " + d));
            shown++;
        }
        actor.sendSystemMessage(msg);

        if (alertOtherStaff && actor.getServer() != null) {
            for (ServerPlayer p : actor.getServer().getPlayerList().getPlayers()) {
                if (!p.getUUID().equals(actor.getUUID()) && RpEssentialsPermissions.isStaff(p)) p.sendSystemMessage(msg);
            }
        }
        RpEssentials.LOGGER.info("[GUI] {} | {} | {}", actor.getName().getString(),
                strip(title), strip(String.join(" ; ", details)));
    }

    public static MutableComponent withTooltip(MutableComponent component, List<String> full) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(full.size(), MAX_HOVER);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append("\n");
            sb.append(full.get(i));
        }
        if (full.size() > MAX_HOVER) sb.append("\n§8... and ").append(full.size() - MAX_HOVER).append(" more");
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