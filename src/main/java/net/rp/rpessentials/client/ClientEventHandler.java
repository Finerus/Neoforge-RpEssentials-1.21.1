package net.rp.rpessentials.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
        ClientProfessionRestrictions.clear();
        ProfessionRestrictionEventHandler.clearTooltipCache();
        ClientTooltipRestrictions.clear();
        RpEssentials.LOGGER.info("[RpEssentials] Nametag cache reset on disconnect");
    }
}