package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.client.gui.LicenseScreen;
import net.rp.rpessentials.items.LicenseItem;

@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class LicenseClientHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getItemStack().getItem() instanceof LicenseItem)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (!ClientFeatures.professionsEnabled()) {
            // Profession system turned off on the server: the license no longer opens.
            if (mc.player != null) mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("rpessentials.professions.disabled.license")
                            .withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return;
        }

        mc.setScreen(new LicenseScreen(event.getItemStack()));
    }

    /** Profession system turned off: every line of the license tooltip is scrambled, except the one saying so. */
    @SubscribeEvent
    public static void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if (ClientFeatures.professionsEnabled() || !(event.getItemStack().getItem() instanceof LicenseItem)) return;
        java.util.List<net.minecraft.network.chat.Component> lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) {
            net.minecraft.network.chat.Component line = lines.get(i);
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("rpessentials.professions.disabled.license_tooltip")) continue;
            lines.set(i, net.minecraft.network.chat.Component.empty()
                    .withStyle(st -> st.withObfuscated(true)).append(line));
        }
    }
}
