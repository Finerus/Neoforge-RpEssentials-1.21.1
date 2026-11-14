package net.rp.rpessentials.moderation;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.util.ColorHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class ManageSuggestions {

    public enum Kind { UNWHITELISTED, DEOPPED, BANNED }

    private static final Map<String, Long> recent = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        String raw = event.getParseResults().getReader().getString().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        if (!(raw.startsWith("whitelist ") || raw.startsWith("deop ") || raw.startsWith("ban ")
                || raw.startsWith("minecraft:"))) return;
        String[] t = raw.split("\\s+");
        if (t.length < 2) return;
        String root = t[0].startsWith("minecraft:") ? t[0].substring(10) : t[0];

        Kind kind;
        String name;
        if (root.equals("whitelist") && t.length >= 3 && t[1].equals("remove")) { kind = Kind.UNWHITELISTED; name = t[2]; }
        else if (root.equals("deop")) { kind = Kind.DEOPPED; name = t[1]; }
        else if (root.equals("ban")) { kind = Kind.BANNED; name = t[1]; }
        else return;
        if (name.startsWith("@")) return;

        MinecraftServer server = event.getParseResults().getContext().getSource().getServer();
        final Kind k = kind;
        final String n = name;
        CompletableFuture.runAsync(() -> server.execute(() -> {
            GameProfile profile = ManageService.resolveByName(server, n);
            if (profile != null) propose(server, profile.getId(), profile.getName(), k);
        }), CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS));
    }

    public static void propose(MinecraftServer server, UUID uuid, String name, Kind kind) {
        GameProfile profile = ManageService.resolveProfile(server, uuid);
        PlayerList pl = server.getPlayerList();
        boolean valid = switch (kind) {
            case UNWHITELISTED -> !pl.getWhiteList().isWhiteListed(profile);
            case DEOPPED -> !pl.isOp(profile);
            case BANNED -> pl.getBans().isBanned(profile);
        };
        if (!valid) return;

        long now = System.currentTimeMillis();
        Long last = recent.put(uuid + ":" + kind, now);
        if (last != null && now - last < 5000L) return;

        String base = net.rp.rpessentials.commands.CommandToggles.manage() + " " + name + " ";
        ModConfigSpec.ConfigValue<String> header = switch (kind) {
            case UNWHITELISTED -> MessagesConfig.MANAGE_SUG_HEAD_WL;
            case DEOPPED -> MessagesConfig.MANAGE_SUG_HEAD_DEOP;
            case BANNED -> MessagesConfig.MANAGE_SUG_HEAD_BAN;
        };

        for (ServerPlayer p : pl.getPlayers()) {
            if (!RpEssentialsPermissions.isStaff(p)) continue;
            p.sendSystemMessage(ColorHelper.parseSection(ServerLang.get(p, header, "player", name)));
            Component[] lines = switch (kind) {
                case UNWHITELISTED -> new Component[]{
                        line(p, MessagesConfig.MANAGE_SUG_L_REWL, MessagesConfig.MANAGE_SUG_H_REWL, base + "whitelist_add", true),
                        line(p, MessagesConfig.MANAGE_SUG_L_BAN, MessagesConfig.MANAGE_SUG_H_BAN, base + "ban ", false),
                        line(p, MessagesConfig.MANAGE_SUG_L_TEMPBAN, MessagesConfig.MANAGE_SUG_H_TEMPBAN, base + "tempban 7d ", false),
                        line(p, MessagesConfig.MANAGE_SUG_L_CLRINV, MessagesConfig.MANAGE_SUG_H_CLRINV, base + "clear_inventory", true),
                        line(p, MessagesConfig.MANAGE_SUG_L_ABS, MessagesConfig.MANAGE_SUG_H_ABS, base + "absence_add ", false)};
                case DEOPPED -> new Component[]{
                        line(p, MessagesConfig.MANAGE_SUG_L_REOP, MessagesConfig.MANAGE_SUG_H_REOP, base + "op ", false),
                        line(p, MessagesConfig.MANAGE_SUG_L_KICK, MessagesConfig.MANAGE_SUG_H_KICK, base + "kick ", false),
                        line(p, MessagesConfig.MANAGE_SUG_L_BAN, MessagesConfig.MANAGE_SUG_H_BAN, base + "ban ", false)};
                case BANNED -> new Component[]{
                        line(p, MessagesConfig.MANAGE_SUG_L_PARDON, MessagesConfig.MANAGE_SUG_H_PARDON, base + "pardon", true),
                        line(p, MessagesConfig.MANAGE_SUG_L_UNWL, MessagesConfig.MANAGE_SUG_H_UNWL, base + "whitelist_remove", true),
                        line(p, MessagesConfig.MANAGE_SUG_L_CLRINV, MessagesConfig.MANAGE_SUG_H_CLRINV, base + "clear_inventory", true)};
            };
            for (Component c : lines) p.sendSystemMessage(c);
        }
    }

    private static Component line(ServerPlayer p, ModConfigSpec.ConfigValue<String> label,
                                  ModConfigSpec.ConfigValue<String> hover, String command, boolean run) {
        Component hoverText = ColorHelper.parseSection(ServerLang.get(p, hover));
        return Component.literal("  §8> ").append(ColorHelper.parseSection(ServerLang.get(p, label)).copy()
                .withStyle(s -> s
                        .withClickEvent(new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))));
    }
}