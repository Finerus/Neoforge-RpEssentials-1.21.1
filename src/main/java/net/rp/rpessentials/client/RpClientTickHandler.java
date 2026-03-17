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
 *
 * Bus GAME = valeur par défaut de @EventBusSubscriber, pas besoin de le préciser.
 * (Bus.GAME était deprecated depuis NeoForge 1.21.1)
 */
@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class RpClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Ne rien faire si pas en jeu ou si un écran est déjà ouvert
        if (mc.player == null || mc.screen != null) return;

        // ── Touche GUI Profession ────────────────────────────────────────────
        while (RpKeyBindings.OPEN_PROFESSION_GUI != null
                && RpKeyBindings.OPEN_PROFESSION_GUI.consumeClick()) {
            PacketDistributor.sendToServer(
                    new RequestOpenGuiPacket(RequestOpenGuiPacket.GuiType.PROFESSION));
            RpEssentials.LOGGER.debug("[RPEssentials] Sent PROFESSION GUI request to server");
        }

        // ── Touche GUI Profil Joueur ─────────────────────────────────────────
        while (RpKeyBindings.OPEN_PLAYER_PROFILE_GUI != null
                && RpKeyBindings.OPEN_PLAYER_PROFILE_GUI.consumeClick()) {
            PacketDistributor.sendToServer(
                    new RequestOpenGuiPacket(RequestOpenGuiPacket.GuiType.PLAYER_PROFILE));
            RpEssentials.LOGGER.debug("[RPEssentials] Sent PLAYER_PROFILE GUI request to server");
        }
    }
}