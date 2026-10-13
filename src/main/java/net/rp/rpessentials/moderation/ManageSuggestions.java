package net.rp.rpessentials.moderation;

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
import net.rp.rpessentials.RpEssentialsPermissions;

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

        String base = "/rpessentials manage " + name + " ";
        String header = switch (kind) {
            case UNWHITELISTED -> "§6[Manage] §e" + name + " §7has been removed from the whitelist. Quick actions:";
            case DEOPPED -> "§6[Manage] §e" + name + " §7is no longer an operator. Quick actions:";
            case BANNED -> "§6[Manage] §e" + name + " §7has been banned. Quick actions:";
        };

        Component[] lines = switch (kind) {
            case UNWHITELISTED -> new Component[]{
                    line("Re-whitelist", "Add back to the whitelist", base + "whitelist_add", true),
                    line("Ban", "Ban (type a reason)", base + "ban ", false),
                    line("Clear inventory", "Clear the inventory (online only)", base + "clear_inventory", true),
                    line("Absence", "Declare an absence: days then reason", base + "absence_add ", false)};
            case DEOPPED -> new Component[]{
                    line("Re-op", "Grant OP (type the level 1 to 4)", base + "op ", false),
                    line("Kick", "Kick (type a reason)", base + "kick ", false),
                    line("Ban", "Ban (type a reason)", base + "ban ", false)};
            case BANNED -> new Component[]{
                    line("Pardon", "Lift the ban", base + "pardon", true),
                    line("Unwhitelist", "Remove from the whitelist", base + "whitelist_remove", true),
                    line("Clear inventory", "Clear the inventory (online only)", base + "clear_inventory", true)};
        };

        for (ServerPlayer p : pl.getPlayers()) {
            if (!RpEssentialsPermissions.isStaff(p)) continue;
            p.sendSystemMessage(Component.literal(header));
            for (Component c : lines) p.sendSystemMessage(c);
        }
    }

    private static Component line(String label, String hover, String command, boolean run) {
        return Component.literal("  §8> ").append(Component.literal("§e[" + label + "]").withStyle(s -> s
                .withClickEvent(new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7" + hover)))));
    }
}