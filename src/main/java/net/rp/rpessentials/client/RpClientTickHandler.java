package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.network.RequestOpenGuiPacket;

/**
 * Écoute les ticks client pour détecter les appuis de touches GUI.
 */
@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class RpClientTickHandler {

    private static int evictTick = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Éviction périodique du cache des joueurs déconnectés (~toutes les 10s)
        if (mc.level != null && mc.getConnection() != null) {
            evictTick++;
            if (evictTick >= 200) {
                evictTick = 0;
                ClientNametagCache.evictDisconnected();
            }
        }

        if (mc.player == null || mc.screen != null) return;

        while (RpKeyBindings.OPEN_PROFESSION_GUI != null
                && RpKeyBindings.OPEN_PROFESSION_GUI.consumeClick()) {
            PacketDistributor.sendToServer(
                    new RequestOpenGuiPacket(RequestOpenGuiPacket.GuiType.PROFESSION));
            RpEssentials.LOGGER.debug("[RpEssentials] Sent PROFESSION GUI request to server");
        }

        while (RpKeyBindings.OPEN_PLAYER_PROFILE_GUI != null
                && RpKeyBindings.OPEN_PLAYER_PROFILE_GUI.consumeClick()) {
            PacketDistributor.sendToServer(
                    new RequestOpenGuiPacket(RequestOpenGuiPacket.GuiType.PLAYER_PROFILE));
            RpEssentials.LOGGER.debug("[RpEssentials] Sent PLAYER_PROFILE GUI request to server");
        }

        while (RpKeyBindings.OPEN_DICE_GUI != null
                && RpKeyBindings.OPEN_DICE_GUI.consumeClick()) {
            try {
                if (net.rp.rpessentials.config.RpConfig.ENABLE_DICE_SYSTEM.get()) {
                    mc.setScreen(new net.rp.rpessentials.client.gui.DiceSelectionScreen());
                }
            } catch (IllegalStateException ignored) {}
        }

        while (RpKeyBindings.OPEN_CONFIG_MANAGER_GUI != null
                && RpKeyBindings.OPEN_CONFIG_MANAGER_GUI.consumeClick()) {
            PacketDistributor.sendToServer(
                    new RequestOpenGuiPacket(RequestOpenGuiPacket.GuiType.CONFIG_MANAGER));
            RpEssentials.LOGGER.debug("[RpEssentials] Sent CONFIG_MANAGER GUI request to server");
        }
    }
}
