package net.rp.rpessentials.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.rp.rpessentials.client.ClientNametagCache;
import net.rp.rpessentials.client.ClientNametagConfig;
import net.rp.rpessentials.client.ClientProfessionRestrictions;
import net.rp.rpessentials.client.ClientTooltipRestrictions;
import net.rp.rpessentials.profession.ProfessionRestrictionEventHandler;
import net.rp.rpessentials.RpEssentials;

@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientNametagCache.reset();
        ClientNametagConfig.reset();
        ClientPerms.clear();
        ClientFeatures.reset();
        ClientProfessionRestrictions.clear();
        ProfessionRestrictionEventHandler.clearTooltipCache();
        ClientTooltipRestrictions.clear();
        RpEssentials.LOGGER.info("[RpEssentials] Nametag cache reset on disconnect");
    }
}