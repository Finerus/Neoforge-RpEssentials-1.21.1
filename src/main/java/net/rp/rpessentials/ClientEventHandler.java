package net.rp.rpessentials;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.rp.rpessentials.client.ClientNametagCache;
import net.rp.rpessentials.client.ClientNametagConfig;
import net.rp.rpessentials.client.NametagOcclusionCache;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientNametagConfig.reset();
        ClientNametagCache.reset();
        NametagOcclusionCache.reset();
        RpEssentials.LOGGER.info("[RPEssentials] Nametag caches reset on disconnect");
    }
}