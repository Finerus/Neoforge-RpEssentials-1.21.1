package net.rp.rpessentials;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.network.HideNametagsPacket;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.profession.ProfessionSyncHelper;

public final class ConfigRefresh {

    private ConfigRefresh() {}

    public static void refresh(MinecraftServer server) {
        RpEssentialsRoleManager.clearAll();
        RpEssentialsRoleManager.reload();
        RpEssentialsScheduleManager.reload();
        RpEssentialsScheduleManager.enforceOnline(server);
        ProfessionRestrictionManager.reloadCache();
        RpEssentialsPatternUtils.clearCache();
        ImmersivePresetHelper.clearCache();
        RpEssentialsPermissions.clearCache();
        TabListCache.reload();
        if (server == null) return;
        boolean hide = false;
        try { hide = RpEssentialsConfig.HIDE_NAMETAGS.get(); } catch (IllegalStateException ignored) {}
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new HideNametagsPacket(hide));
            ProfessionSyncHelper.syncToPlayer(p);
        }
    }
}