package net.rp.rpessentials.moderation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.rp.rpessentials.RpEssentials;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class VanishEvents {

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.hides(VanishManager.Aspect.MOBS)) {
            event.setNewTarget(null);
        }
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.hides(VanishManager.Aspect.PICKUP)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (event.getCause() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.hides(VanishManager.Aspect.GAME_EVENTS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p
                && VanishManager.isVanished(p.getUUID()) && VanishManager.invulnerable()) {
            event.setCanceled(true);
        }
    }
}