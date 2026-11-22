package net.rp.rpessentials.moderation;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.util.TimeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What happens around a report: messages to the reporter and the staff, clickable buttons,
 * teleport to the report place. Shared by the commands and the profile GUI.
 */
public final class ReportService {

    public static final String STAFF_ROOT = "/rpessentials reports";

    private ReportService() {}

    // =========================================================================
    // CREATE
    // =========================================================================
    /** Sends a report and tells the reporter and the online staff. Returns 1 on success. */
    public static int submit(ServerPlayer reporter, UUID target, String targetName, String reason) {
        ReportManager.Created c = ReportManager.create(reporter, target, targetName, reason);
        switch (c.result()) {
            case DISABLED -> reporter.sendSystemMessage(msg(reporter, MessagesConfig.REPORT_DISABLED));
            case SELF -> reporter.sendSystemMessage(msg(reporter, MessagesConfig.REPORT_SELF));
            case COOLDOWN -> reporter.sendSystemMessage(msg(reporter, MessagesConfig.REPORT_COOLDOWN,
                    "seconds", String.valueOf(c.waitSeconds())));
            case TOO_MANY -> reporter.sendSystemMessage(msg(reporter, MessagesConfig.REPORT_TOO_MANY,
                    "count", String.valueOf(c.waitSeconds())));
            case OK -> {
                ReportManager.Report r = c.report();
                reporter.sendSystemMessage(msg(reporter, MessagesConfig.REPORT_SENT,
                        "id", String.valueOf(r.id), "player", r.targetName));
                notifyStaff(reporter.getServer(), r);
                return 1;
            }
        }
        return 0;
    }

    private static void notifyStaff(MinecraftServer server, ReportManager.Report r) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!RpPerms.has(p, RpPerm.NOTIFY_REPORTS)) continue;
            MutableComponent line = msg(p, MessagesConfig.REPORT_STAFF_NEW, "id", String.valueOf(r.id),
                    "reporter", r.reporterName, "player", r.targetName, "reason", r.reason).copy();
            line.append(Component.literal(" ")).append(button(p, MessagesConfig.REPORT_BTN_VIEW,
                    ClickEvent.Action.RUN_COMMAND, STAFF_ROOT + " info " + r.id, MessagesConfig.REPORT_BTN_VIEW_HOVER));
            p.sendSystemMessage(line);
        }
    }

    /** Called when a staff member logs in. */
    public static void onStaffLogin(ServerPlayer staff) {
        if (!ReportManager.enabled() || !Cfg.get(ModerationConfig.REPORT_NOTIFY_ON_JOIN, true)) return;
        if (!RpPerms.has(staff, RpPerm.NOTIFY_REPORTS)) return;
        int open = ReportManager.countOpen();
        if (open <= 0) return;
        MutableComponent line = msg(staff, MessagesConfig.REPORT_STAFF_JOIN, "count", String.valueOf(open)).copy();
        line.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, STAFF_ROOT + " list")));
        staff.sendSystemMessage(line);
    }

    // =========================================================================
    // STAFF ACTIONS (viewer = staff member doing it; messages go to them)
    // =========================================================================
    public static boolean claim(ServerPlayer staff, int id) {
        ReportManager.Report r = ReportManager.get(id);
        if (r == null) { staff.sendSystemMessage(notFound(staff, id)); return false; }
        if (!r.isOpen()) { staff.sendSystemMessage(msg(staff, MessagesConfig.REPORT_ALREADY_CLOSED, "id", String.valueOf(id))); return false; }
        ReportManager.claim(id, staff);
        staff.sendSystemMessage(msg(staff, MessagesConfig.REPORT_CLAIMED, "id", String.valueOf(id)));
        MinecraftServer server = staff.getServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p != staff && RpPerms.has(p, RpPerm.NOTIFY_REPORTS)) {
                    p.sendSystemMessage(msg(p, MessagesConfig.REPORT_CLAIMED_STAFF,
                            "id", String.valueOf(id), "staff", staff.getGameProfile().getName()));
                }
            }
        }
        return true;
    }

    /** {@code staff} null = console. */
    public static boolean close(MinecraftServer server, ServerPlayer staff, int id, String note) {
        ReportManager.Report r = ReportManager.get(id);
        if (r == null) { send(server, staff, notFound(staff, id)); return false; }
        if (!r.isOpen()) { send(server, staff, msg(staff, MessagesConfig.REPORT_ALREADY_CLOSED, "id", String.valueOf(id))); return false; }
        String name = staff != null ? staff.getGameProfile().getName() : "Console";
        String uuid = staff != null ? staff.getUUID().toString() : null;
        ReportManager.close(id, name, uuid, note);
        send(server, staff, msg(staff, MessagesConfig.REPORT_CLOSED, "id", String.valueOf(id)));
        ServerPlayer reporter = server != null ? server.getPlayerList().getPlayer(r.reporter()) : null;
        if (reporter != null) {
            reporter.sendSystemMessage(msg(reporter, MessagesConfig.REPORT_REPORTER_CLOSED,
                    "id", String.valueOf(id), "player", r.targetName));
        }
        return true;
    }

    public static boolean reopen(MinecraftServer server, ServerPlayer staff, int id) {
        ReportManager.Report r = ReportManager.get(id);
        if (r == null) { send(server, staff, notFound(staff, id)); return false; }
        if (r.isOpen()) { send(server, staff, msg(staff, MessagesConfig.REPORT_NOT_CLOSED, "id", String.valueOf(id))); return false; }
        ReportManager.reopen(id);
        send(server, staff, msg(staff, MessagesConfig.REPORT_REOPENED, "id", String.valueOf(id)));
        return true;
    }

    public static boolean delete(MinecraftServer server, ServerPlayer staff, int id) {
        if (!ReportManager.delete(id)) { send(server, staff, notFound(staff, id)); return false; }
        send(server, staff, msg(staff, MessagesConfig.REPORT_DELETED, "id", String.valueOf(id)));
        return true;
    }

    /** Teleports a staff member to where the reporter stood. */
    public static boolean teleport(ServerPlayer staff, int id) {
        ReportManager.Report r = ReportManager.get(id);
        if (r == null) { staff.sendSystemMessage(notFound(staff, id)); return false; }
        ResourceLocation dim = r.dimension != null ? ResourceLocation.tryParse(r.dimension) : null;
        ServerLevel level = dim != null && staff.getServer() != null
                ? staff.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim)) : null;
        if (level == null) { staff.sendSystemMessage(msg(staff, MessagesConfig.REPORT_TP_FAILED)); return false; }
        staff.teleportTo(level, r.x + 0.5, r.y, r.z + 0.5, staff.getYRot(), staff.getXRot());
        return true;
    }

    // =========================================================================
    // DISPLAY
    // =========================================================================
    public static String status(ServerPlayer viewer, ReportManager.Report r) {
        return ServerLang.get(viewer, switch (r.status) {
            case OPEN -> MessagesConfig.REPORT_STATUS_OPEN;
            case CLAIMED -> MessagesConfig.REPORT_STATUS_CLAIMED;
            case CLOSED -> MessagesConfig.REPORT_STATUS_CLOSED;
        });
    }

    public static String date(long ms) {
        return TimeHelper.format("dd/MM/yyyy HH:mm", ms);
    }

    public static String place(ReportManager.Report r) {
        return (r.dimension != null ? r.dimension : "?") + " " + r.x + " " + r.y + " " + r.z;
    }

    /** One line per open report, each one clickable. */
    public static List<Component> listLines(ServerPlayer viewer) {
        List<ReportManager.Report> open = ReportManager.open();
        List<Component> out = new ArrayList<>();
        out.add(msg(viewer, MessagesConfig.REPORT_LIST_HEADER, "count", String.valueOf(open.size())));
        if (open.isEmpty()) out.add(msg(viewer, MessagesConfig.REPORT_LIST_NONE));
        for (ReportManager.Report r : open) {
            MutableComponent line = msg(viewer, MessagesConfig.REPORT_LIST_LINE, "id", String.valueOf(r.id),
                    "status", status(viewer, r), "reporter", r.reporterName, "player", r.targetName,
                    "date", date(r.createdAt)).copy();
            line.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, STAFF_ROOT + " info " + r.id))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            msg(viewer, MessagesConfig.REPORT_BTN_VIEW_HOVER))));
            out.add(line);
        }
        return out;
    }

    /** Full report with action buttons. */
    public static List<Component> infoLines(ServerPlayer viewer, ReportManager.Report r) {
        List<Component> out = new ArrayList<>();
        out.add(msg(viewer, MessagesConfig.REPORT_INFO, "id", String.valueOf(r.id), "status", status(viewer, r),
                "reporter", r.reporterName, "player", r.targetName, "date", date(r.createdAt),
                "place", place(r), "reason", r.reason));
        if (r.handlerName != null) out.add(msg(viewer, MessagesConfig.REPORT_INFO_HANDLER, "staff", r.handlerName));
        if (r.closeNote != null) out.add(msg(viewer, MessagesConfig.REPORT_INFO_NOTE, "note", r.closeNote));
        MutableComponent buttons = Component.empty();
        if (r.isOpen()) {
            if (r.status == ReportManager.Status.OPEN) {
                buttons.append(button(viewer, MessagesConfig.REPORT_BTN_CLAIM, ClickEvent.Action.RUN_COMMAND,
                        STAFF_ROOT + " claim " + r.id, null)).append(Component.literal(" "));
            }
            buttons.append(button(viewer, MessagesConfig.REPORT_BTN_CLOSE, ClickEvent.Action.SUGGEST_COMMAND,
                    STAFF_ROOT + " close " + r.id + " ", null)).append(Component.literal(" "));
        }
        buttons.append(button(viewer, MessagesConfig.REPORT_BTN_TP, ClickEvent.Action.RUN_COMMAND,
                STAFF_ROOT + " tp " + r.id, null));
        out.add(buttons);
        return out;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    private static Component msg(ServerPlayer viewer, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> key,
                                 String... replacements) {
        return ColorHelper.parseSection(ServerLang.get(viewer, key, replacements));
    }

    /** Tells a staff member (null = console) that report {@code id} does not exist. */
    public static void reportNotFound(MinecraftServer server, ServerPlayer staff, int id) {
        send(server, staff, notFound(staff, id));
    }

    private static Component notFound(ServerPlayer viewer, int id) {
        return msg(viewer, MessagesConfig.REPORT_NOT_FOUND, "id", String.valueOf(id));
    }

    private static Component button(ServerPlayer viewer, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> label,
                                    ClickEvent.Action action, String command,
                                    net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> hover) {
        MutableComponent b = msg(viewer, label).copy();
        b.withStyle(s -> {
            var st = s.withClickEvent(new ClickEvent(action, command));
            return hover != null ? st.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, msg(viewer, hover))) : st;
        });
        return b;
    }

    /** To a staff member, or to the server log when it comes from the console. */
    private static void send(MinecraftServer server, ServerPlayer staff, Component c) {
        if (staff != null) staff.sendSystemMessage(c);
        else if (server != null) server.sendSystemMessage(c);
    }
}
