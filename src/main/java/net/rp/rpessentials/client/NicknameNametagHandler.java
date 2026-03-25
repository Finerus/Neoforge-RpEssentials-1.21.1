package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.RpEssentials;

import java.util.UUID;

/**
 * Remplace le Component du nametag par le nickname reçu du serveur via SyncNametagDataPacket.
 * Ne gère QUE la substitution du nom — la logique d'affichage (occlusion, distance, etc.)
 * reste dans MixinEntityRenderer.
 */
@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class NicknameNametagHandler {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        // Ne pas toucher au nametag du joueur local
        // if (player.getUUID().equals(mc.player.getUUID())) return;

        UUID uuid = player.getUUID();
        String realName = player.getGameProfile().getName();

        ClientNametagCache.NametagData data = ClientNametagCache.get(uuid);

        if (data == null) {
            // Données pas encore reçues — afficher le vrai nom sans modifier
            return;
        }

        String prefix  = data.prefix() != null ? data.prefix() : "";
        String display = data.displayName() != null && !data.displayName().isEmpty()
                ? data.displayName()
                : realName;

        Component nicknameComponent = ColorHelper.parseColors(prefix + display);
        event.setContent(nicknameComponent);
        // Ne pas toucher à setCanRender — laisser MixinEntityRenderer gérer ça
    }
}
