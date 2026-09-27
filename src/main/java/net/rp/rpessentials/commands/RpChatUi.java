package net.rp.rpessentials.commands;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class RpChatUi {

    public static final String LINE = "§6§m                              §r";
    public static final int PAGE_SIZE = 8;

    private RpChatUi() {}

    public static int pageCount(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static String kv(String label, String value) {
        return "§7" + label + ": " + value;
    }

    public static String row(String... items) {
        return String.join(" §8| ", items) + "\n";
    }

    public static String flag(BooleanSupplier supplier) {
        try { return supplier.getAsBoolean() ? "§aON" : "§cOFF"; }
        catch (Exception e) { return "§8N/A"; }
    }

    public static String value(Supplier<?> supplier) {
        try {
            Object v = supplier.get();
            return v != null ? "§f" + v : "§8N/A";
        } catch (Exception e) { return "§8N/A"; }
    }

    public static MutableComponent pager(int page, int pages, String baseCommand) {
        MutableComponent out = Component.empty();
        out.append(button("<<", page > 1, baseCommand + " " + (page - 1)));
        out.append(Component.literal(" §7Page §f" + page + "§7/§f" + pages + " "));
        out.append(button(">>", page < pages, baseCommand + " " + (page + 1)));
        return out;
    }

    private static MutableComponent button(String label, boolean active, String command) {
        if (!active) return Component.literal("§8[" + label + "]");
        return Component.literal("§e[" + label + "]").withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7" + command))));
    }
}