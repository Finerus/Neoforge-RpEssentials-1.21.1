package net.rp.rpessentials;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.moderation.VanishManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TabBlur {

    public record Settings(boolean enabled, boolean opsSeeAll, boolean debug, boolean blurSpectators,
                           boolean sneakStealth, boolean obfPrefix, double maxDistSq, double sneakDistSq,
                           int obfLen, Set<String> whitelist, String style, String codes, String nametagMode) {}

    private static volatile Settings current;
    private static final Map<UUID, Map<UUID, Boolean>> states = new ConcurrentHashMap<>();

    private TabBlur() {}

    public static void reload() {
        current = null;
        states.clear();
    }

    public static void clearAll() {
        current = null;
        states.clear();
    }

    public static void onLogout(UUID id) {
        states.remove(id);
        for (Map<UUID, Boolean> m : states.values()) m.remove(id);
    }

    public static Settings settings() {
        Settings s = current;
        if (s != null) return s;
        try {
            s = new Settings(
                    RpEssentialsConfig.ENABLE_BLUR.get(),
                    RpEssentialsConfig.OPS_SEE_ALL.get(),
                    RpEssentialsConfig.DEBUG_SELF_BLUR.get(),
                    RpEssentialsConfig.BLUR_SPECTATORS.get(),
                    RpEssentialsConfig.ENABLE_SNEAK_STEALTH.get(),
                    RpEssentialsConfig.OBFUSCATE_PREFIX.get(),
                    Math.pow(RpEssentialsConfig.PROXIMITY_DISTANCE.get(), 2),
                    Math.pow(RpEssentialsConfig.SNEAK_PROXIMITY_DISTANCE.get(), 2),
                    RpEssentialsConfig.OBFUSCATED_NAME_LENGTH.get(),
                    new HashSet<>(RpEssentialsConfig.WHITELIST.get()),
                    RpEssentialsConfig.BLUR_STYLE.get(),
                    RpEssentialsConfig.BLUR_CODES.get(),
                    RpEssentialsConfig.NAMETAG_BLUR_MODE.get());
            current = s;
            return s;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public static boolean seesAll(ServerPlayer receiver, Settings s) {
        if (s.debug()) return false;
        return RpEssentialsRoleManager.has(receiver, RpEssentialsRoleManager.Permission.SEE_ALL)
                || s.whitelist().contains(receiver.getGameProfile().getName())
                || (s.opsSeeAll() && receiver.hasPermissions(2));
    }

    public static boolean isClear(ServerPlayer receiver, boolean seesAll, ServerPlayer target, Settings s) {
        String realName = target.getGameProfile().getName();
        if (TabListCache.isBlacklisted(realName)) return false;
        if (seesAll || TabListCache.isAlwaysVisible(realName)
                || RpEssentialsRoleManager.has(target, RpEssentialsRoleManager.Permission.TAB_WHITELIST)) return true;
        double maxSq = (s.sneakStealth() && target.isCrouching()) ? s.sneakDistSq() : s.maxDistSq();
        boolean blur = s.debug()
                || receiver.level() != target.level()
                || receiver.distanceToSqr(target) > maxSq
                || (target.isSpectator() && s.blurSpectators());
        return !blur;
    }

    public static Component hidden(String displayed, String coloredPrefix, Settings s) {
        int len = Math.min(ColorHelper.stripAll(displayed).length(), s.obfLen());
        String body = switch (s.style()) {
            case "DASHES" -> "-".repeat(len);
            case "HASHES" -> "#".repeat(len);
            case "STARS" -> "*".repeat(len);
            default -> "§k" + "?".repeat(len);
        };
        String text = (s.obfPrefix() ? "" : coloredPrefix + "§r") + s.codes() + body;
        return ColorHelper.parseColors(text).copy()
                .withStyle(st -> st.withInsertion("rpe_blur:" + s.nametagMode()));
    }

    public static void tick(MinecraftServer server) {
        Settings s = settings();
        if (s == null || !s.enabled()) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer receiver : players) {
            boolean seesAll = seesAll(receiver, s);
            Map<UUID, Boolean> known = states.computeIfAbsent(receiver.getUUID(), k -> new ConcurrentHashMap<>());
            List<ServerPlayer> changed = null;
            for (ServerPlayer target : players) {
                if (VanishManager.isHiddenFrom(receiver, target.getUUID())) continue;
                boolean clear = isClear(receiver, seesAll, target, s);
                Boolean old = known.put(target.getUUID(), clear);
                if (old == null || old != clear) {
                    if (changed == null) changed = new ArrayList<>();
                    changed.add(target);
                }
            }
            if (changed != null) {
                receiver.connection.send(new ClientboundPlayerInfoUpdatePacket(
                        EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), changed));
            }
        }
    }
}