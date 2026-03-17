package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.rp.rpessentials.config.NametagConfig;
import net.rp.rpessentials.RpEssentials;

import java.util.UUID;

/**
 * Handler unique pour tout le système nametag (remplace ClientNametagRenderer).
 * Ordre de priorité :
 *  1. Entité non-joueur             → laisser vanilla
 *  2. hideNametags actif            → FALSE (sans condition sur le packet reçu)
 *  3. Système avancé désactivé      → laisser vanilla
 *  4. Cache miss (data == null)     → FALSE (évite les fuites see-through)
 *  5. Staff + staffAlwaysSeeReal    → TRUE, nom lisible, skip checks
 *  6. Distance > renderDistance     → FALSE
 *  7. Derrière un bloc              → FALSE
 *  8. Sneak sans showWhileSneaking  → FALSE
 *  9. Distance > obfuscationDist    → TRUE, nom obfusqué
 * 10. Sinon                         → TRUE, nom lisible
 */
@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class NametagEventHandler {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // ─── 1 : uniquement les joueurs ───────────────────────────────────────
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;

        // ─── 2 : hideNametags — SANS garde hasReceivedServerConfig() ─────────
        // L'ancien code conditionnait ce check à hasReceivedServerConfig().
        // Quand vanilla force le see-through (joueur derrière un bloc),
        // l'event fire mais le garde pouvait valoir false → nametag visible.
        if (ClientNametagConfig.shouldHideNametags()) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        // ─── 3 : système avancé activé ? ─────────────────────────────────────
        boolean enabled;
        try {
            enabled = NametagConfig.ENABLED.get();
        } catch (IllegalStateException e) {
            return;
        }
        if (!enabled) return;

        // ─── 4 : AbstractClientPlayer requis pour la suite ───────────────────
        if (!(entity instanceof AbstractClientPlayer target)) return;
        if (target.getUUID().equals(mc.player.getUUID())) return;

        UUID targetUUID = target.getUUID();

        // ─── 5 : cache miss → FALSE pour éviter les fuites see-through ───────
        ClientNametagCache.NametagData data = ClientNametagCache.get(targetUUID);
        if (data == null) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        // ─── 6 : staff voit tout ──────────────────────────────────────────────
        boolean staffAlwaysSeeReal;
        try {
            staffAlwaysSeeReal = NametagConfig.STAFF_ALWAYS_SEE_REAL.get();
        } catch (IllegalStateException e) {
            staffAlwaysSeeReal = true;
        }

        ClientNametagCache.NametagData selfData = ClientNametagCache.get(mc.player.getUUID());
        boolean viewerIsStaff = selfData != null && selfData.isStaff();
        if (viewerIsStaff && staffAlwaysSeeReal) {
            event.setContent(buildReadable(data, target));
            event.setCanRender(TriState.TRUE);
            return;
        }

        // ─── 7 : render distance ─────────────────────────────────────────────
        double renderDist;
        try {
            renderDist = NametagConfig.RENDER_DISTANCE.get();
        } catch (IllegalStateException e) {
            renderDist = -1.0;
        }

        Vec3 cam    = mc.gameRenderer.getMainCamera().getPosition();
        double distSq = cam.distanceToSqr(target.getEyePosition());

        if (renderDist > 0 && distSq > renderDist * renderDist) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        // ─── 8 : occlusion derrière un bloc ──────────────────────────────────
        boolean hideBehindBlocks;
        try {
            hideBehindBlocks = NametagConfig.HIDE_BEHIND_BLOCKS.get();
        } catch (IllegalStateException e) {
            hideBehindBlocks = true;
        }

        if (hideBehindBlocks && isOccluded(mc, target, targetUUID, cam)) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        // ─── 9 : sneak ───────────────────────────────────────────────────────
        boolean showWhileSneaking;
        try {
            showWhileSneaking = NametagConfig.SHOW_WHILE_SNEAKING.get();
        } catch (IllegalStateException e) {
            showWhileSneaking = false;
        }

        if (!showWhileSneaking && target.isCrouching()) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        // ─── 10 : obfuscation distance ────────────────────────────────────────
        boolean obfEnabled;
        double obfDist;
        try {
            obfEnabled = NametagConfig.OBFUSCATION_ENABLED.get();
            obfDist    = NametagConfig.OBFUSCATION_DISTANCE.get();
        } catch (IllegalStateException e) {
            obfEnabled = true;
            obfDist    = 10.0;
        }

        if (obfEnabled && distSq > obfDist * obfDist) {
            String realName = target.getGameProfile().getName();
            event.setContent(NametagFormatter.formatObfuscated(realName.length()));
            event.setCanRender(TriState.TRUE);
            return;
        }

        // ─── 11 : nom lisible ─────────────────────────────────────────────────
        event.setContent(buildReadable(data, target));
        event.setCanRender(TriState.TRUE);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private static boolean isOccluded(Minecraft mc, AbstractClientPlayer target,
                                      UUID uuid, Vec3 cam) {
        NametagOcclusionCache.Entry cached = NametagOcclusionCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.occluded();
        }

        Vec3 eyePos = target.getEyePosition();
        ClipContext ctx = new ClipContext(
                cam,
                eyePos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                target
        );

        BlockHitResult result = mc.level.clip(ctx);
        boolean occluded = result.getType() == HitResult.Type.BLOCK;
        NametagOcclusionCache.put(uuid, occluded);
        return occluded;
    }

    private static Component buildReadable(ClientNametagCache.NametagData data,
                                           AbstractClientPlayer target) {
        String realName = target.getGameProfile().getName();
        return NametagFormatter.formatReadable(data, realName);
    }
}