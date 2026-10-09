package net.rp.rpessentials;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.rp.rpessentials.client.ClientNametagCache;

@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientNametagCache.reset();
        net.rp.rpessentials.client.ClientNametagConfig.reset();
        net.rp.rpessentials.client.ClientProfessionRestrictions.clear();
        RpEssentials.LOGGER.info("[RpEssentials] Nametag cache reset on disconnect");
    }
}