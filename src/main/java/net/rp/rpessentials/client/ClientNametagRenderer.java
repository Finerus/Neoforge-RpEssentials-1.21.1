package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
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
            event.setResult(Event.Result.DENY);
            return;
        }

        if (target != mc.player) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(target.getUUID());
            Component tab = info != null ? info.getTabListDisplayName() : null;
            String ins = tab != null ? tab.getStyle().getInsertion() : null;
            if (ins != null && ins.startsWith("rpe_blur")) {
                if (ins.endsWith(":HIDE")) {
                    event.setResult(Event.Result.DENY);
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