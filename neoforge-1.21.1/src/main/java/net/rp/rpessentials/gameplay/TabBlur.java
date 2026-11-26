package net.rp.rpessentials.gameplay;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ColorHelper;

public final class TabBlur {

    public record Settings(boolean enabled, boolean opsSeeAll, boolean debug, boolean blurSpectators,
                           boolean sneakStealth, boolean obfPrefix, double maxDistSq, double sneakDistSq,
                           int obfLen, String style, String codes, String nametagMode) {}

    /**
     * Tab-list names of one player, parsed once and shared by every viewer.
     * Rebuilt when the prefix, nickname or Minecraft name it was built from changes.
     */
    private record Names(String prefix, String nickname, String realName,
                         Component clear, Component clearWithReal, Component hidden) {}

    private static volatile Settings current;
    private static final Map<UUID, Map<UUID, Boolean>> states = new ConcurrentHashMap<>();
    private static final Map<UUID, Names> names = new ConcurrentHashMap<>();
    /** Viewers that saw every name clearly on the previous tick. */
    private static final Set<UUID> seeAllLastTick = ConcurrentHashMap.newKeySet();

    private TabBlur() {}

    public static void reload() {
        clearAll();
    }

    public static void clearAll() {
        current = null;
        states.clear();
        names.clear();
        seeAllLastTick.clear();
    }

    /** The rights of a viewer changed: every name is checked and sent to them again. */
    public static void resendTo(UUID viewer) {
        states.remove(viewer);
        seeAllLastTick.remove(viewer);
    }

    public static void onLogout(UUID id) {
        states.remove(id);
        names.remove(id);
        seeAllLastTick.remove(id);
        for (Map<UUID, Boolean> m : states.values()) m.remove(id);
    }

    /**
     * The tab-list name of {@code target}: clear, clear with the real name in brackets
     * (nickname + viewer allowed to see real names), or blurred.
     */
    public static Component displayName(ServerPlayer target, boolean clear, boolean showRealNames, Settings s) {
        String realName = target.getGameProfile().getName();
        String nickname = NicknameManager.getNickname(target.getUUID());
        String prefix = ColorHelper.translateAlternateColorCodes(RpEssentials.getPlayerPrefix(target));

        Names n = names.get(target.getUUID());
        if (n == null || !n.prefix().equals(prefix) || !Objects.equals(n.nickname(), nickname)
                || !n.realName().equals(realName)) {
            String displayed = nickname != null ? nickname : realName;
            Component plain = ColorHelper.parseColors(prefix + displayed);
            Component withReal = nickname != null
                    ? ColorHelper.parseColors(prefix + nickname + " §7§o(" + realName + ")")
                    : plain;
            n = new Names(prefix, nickname, realName, plain, withReal, hidden(displayed, prefix, s));
            names.put(target.getUUID(), n);
        }
        if (!clear) return n.hidden();
        return showRealNames ? n.clearWithReal() : n.clear();
    }

    public static Settings settings() {
        Settings s = current;
        if (s != null) return s;
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
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
                    net.rp.rpessentials.config.ConfigRules.value(RpEssentialsConfig.BLUR_STYLE),
                    RpEssentialsConfig.BLUR_CODES.get(),
                    net.rp.rpessentials.config.ConfigRules.value(RpEssentialsConfig.NAMETAG_BLUR_MODE));
            current = s;
            return s;
        } else {
            return null;
        }
    }

    public static boolean seesAll(ServerPlayer receiver, Settings s) {
        if (s.debug()) return false;
        return RpPerms.has(receiver, RpPerm.SEE_ALL)
                || net.rp.rpessentials.util.PlayerRefs.TAB_WHITELIST.contains(receiver.getGameProfile())
                || (s.opsSeeAll() && receiver.hasPermissions(2));
    }

    public static boolean isClear(ServerPlayer receiver, boolean seesAll, ServerPlayer target, Settings s) {
        return isClear(receiver, seesAll, target, s, null);
    }

    /** {@code alwaysVisible}: results of the node check already done this tick (null = check it now). */
    private static boolean isClear(ServerPlayer receiver, boolean seesAll, ServerPlayer target, Settings s,
                                   Map<UUID, Boolean> alwaysVisible) {
        if (net.rp.rpessentials.util.PlayerRefs.TAB_BLACKLIST.contains(target.getGameProfile())) return false;
        if (seesAll || net.rp.rpessentials.util.PlayerRefs.TAB_ALWAYS_VISIBLE.contains(target.getGameProfile())) return true;
        boolean always = alwaysVisible == null ? RpPerms.has(target, RpPerm.TAB_ALWAYS_VISIBLE)
                : alwaysVisible.computeIfAbsent(target.getUUID(), k -> RpPerms.has(target, RpPerm.TAB_ALWAYS_VISIBLE));
        if (always) return true;
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
        Map<UUID, Boolean> alwaysVisible = new java.util.HashMap<>();
        for (ServerPlayer receiver : players) {
            boolean seesAll = seesAll(receiver, s);
            // A viewer who sees everyone keeps the same result for every target until the config
            // is reloaded (which clears the states) or they lose that right: only new targets
            // need a check.
            boolean stable;
            if (seesAll) {
                stable = !seeAllLastTick.add(receiver.getUUID());
            } else {
                seeAllLastTick.remove(receiver.getUUID());
                stable = false;
            }
            Map<UUID, Boolean> known = states.computeIfAbsent(receiver.getUUID(), k -> new ConcurrentHashMap<>());
            List<ServerPlayer> changed = null;
            for (ServerPlayer target : players) {
                if (stable && known.containsKey(target.getUUID())) continue;
                if (VanishManager.isHiddenFrom(receiver, target.getUUID())) continue;
                boolean clear = isClear(receiver, seesAll, target, s, alwaysVisible);
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