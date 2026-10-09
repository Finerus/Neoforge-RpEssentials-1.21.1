package net.rp.rpessentials;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class GuiFeedback {

    private static final int MAX_LINES = 12;

    private GuiFeedback() {}

    public static void report(ServerPlayer actor, String title, List<String> details, boolean alertOtherStaff) {
        StringBuilder sb = new StringBuilder("§a[RpEssentials] §f").append(title);
        int shown = 0;
        for (String d : details) {
            if (shown >= MAX_LINES) {
                sb.append("\n  §8... and ").append(details.size() - shown).append(" more");
                break;
            }
            sb.append("\n  ").append(d);
            shown++;
        }
        Component msg = Component.literal(sb.toString());
        actor.sendSystemMessage(msg);

        if (alertOtherStaff && actor.getServer() != null) {
            for (ServerPlayer p : actor.getServer().getPlayerList().getPlayers()) {
                if (!p.getUUID().equals(actor.getUUID()) && RpEssentialsPermissions.isStaff(p)) p.sendSystemMessage(msg);
            }
        }
        RpEssentials.LOGGER.info("[GUI] {} | {} | {}", actor.getName().getString(),
                strip(title), strip(String.join(" ; ", details)));
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