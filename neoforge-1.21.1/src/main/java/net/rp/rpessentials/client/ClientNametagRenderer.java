package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
//#if forge
//? import net.minecraftforge.eventbus.api.Event;
//#else
import net.neoforged.neoforge.common.util.TriState;
//#endif
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.RpEssentials;

import java.util.UUID;

@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class ClientNametagRenderer {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (!(event.getEntity() instanceof AbstractClientPlayer target)) return;

        if (ClientNametagConfig.shouldHideNametags()) {
            //#if forge
            //? event.setResult(Event.Result.DENY);
            //#else
            event.setCanRender(TriState.FALSE);
            //#endif
            return;
        }

        if (target != mc.player) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(target.getUUID());
            Component tab = info != null ? info.getTabListDisplayName() : null;
            String ins = tab != null ? tab.getStyle().getInsertion() : null;
            if (ins != null && ins.startsWith("rpe_blur")) {
                if (ins.endsWith(":HIDE")) {
                    //#if forge
                    //? event.setResult(Event.Result.DENY);
                    //#else
                    event.setCanRender(TriState.FALSE);
                    //#endif
                    return;
                }
                if (ins.endsWith(":BLUR")) {
                    event.setContent(tab);
                    return;
                }
            }
        }

        Component c = ClientNametagCache.rendered(target.getUUID(), target.getGameProfile().getName());
        if (c != null) event.setContent(c);
    }
}