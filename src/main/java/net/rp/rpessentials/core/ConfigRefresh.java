package net.rp.rpessentials.core;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.compat.LuckPermsSync;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.moderation.VanishManager;
import net.rp.rpessentials.network.HideNametagsPacket;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.profession.ProfessionSyncHelper;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.commands.CommandToggles;
import net.rp.rpessentials.gameplay.DiceManager;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.gameplay.TabBlur;
import net.rp.rpessentials.gameplay.TabListCache;
import net.rp.rpessentials.gameplay.WorldBorderManager;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ImmersivePresetHelper;
import net.rp.rpessentials.util.RpEssentialsPatternUtils;

public final class ConfigRefresh {

    private ConfigRefresh() {}

    public static void refresh(MinecraftServer server) { refresh(server, null); }

    public static void refresh(MinecraftServer server, Object spec) {
        boolean all = spec == null;
        boolean core = all || spec == RpEssentialsConfig.SPEC;
        boolean schedule = all || spec == ScheduleConfig.SPEC;
        boolean moderation = all || spec == ModerationConfig.SPEC;
        boolean professions = all || spec == ProfessionConfig.SPEC;
        boolean rp = all || spec == RpConfig.SPEC;

        if (core) {
            RpEssentialsRoleManager.clearAll();
            RpEssentialsRoleManager.reload();
            RpEssentialsPermissions.clearCache();
            TabListCache.reload();
            TabBlur.reload();
            WorldBorderManager.reloadConfig();
            RpEssentials.clearMetaCache();
            LuckPermsSync.pushAll();
        }
        if (core || schedule) {
            RpEssentialsScheduleManager.reload();
            RpEssentialsScheduleManager.enforceOnline(server);
        }
        if (professions) {
            ProfessionRestrictionManager.reloadCache();
            RpEssentialsPatternUtils.clearCache();
        }
        if (rp) { ImmersivePresetHelper.clearCache(); DiceManager.clearCache(); }
        if (server == null) return;
        if (moderation) VanishManager.refreshAll(server);

        boolean hide = false;
        if (core) {
            hide = Cfg.get(RpEssentialsConfig.HIDE_NAMETAGS, hide);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (core) PacketDistributor.sendToPlayer(p, new HideNametagsPacket(hide));
            if (core || professions) ProfessionSyncHelper.syncToPlayer(p);
        }
        if (core || rp) CommandToggles.resend(server);
    }
}