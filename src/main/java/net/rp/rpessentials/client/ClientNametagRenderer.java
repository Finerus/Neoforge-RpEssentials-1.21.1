package net.rp.rpessentials.client;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.rp.rpessentials.RpEssentials;

@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class ClientNametagRenderer {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!ClientNametagConfig.hasReceivedServerConfig()) {
            return;
        }

        if (!ClientNametagConfig.shouldHideNametags()) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        event.setCanRender(TriState.FALSE);
    }
}