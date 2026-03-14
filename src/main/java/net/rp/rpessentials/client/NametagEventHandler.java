package net.rp.rpessentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.rp.rpessentials.NametagConfig;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;

import java.util.UUID;

/**
 * Gestionnaire principal du système de nametag custom.
 * CLIENT-SIDE uniquement.
 *
 * Logique en cascade (court-circuit à la première condition remplie) :
 *
 *  1. Système désactivé (enabled = false)   → cancel()
 *  2. Entité non-joueur                     → laisser vanilla gérer
 *  3. Staff + staffAlwaysSeeReal            → afficher nom lisible, skip raycast/distance
 *  4. Bloc entre viewer et cible            → cancel() si hideBehindBlocks
 *  5. Distance > renderDistance             → cancel() (invisible)
 *  6. Distance > obfuscationDistance        → afficher nom obfusqué
 *  7. Sinon                                 → afficher format configurable
 */
@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public class NametagEventHandler {

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        // ─── Étape 0 : système activé ? ───────────────────────────────────────
        boolean enabled;
        try {
            enabled = NametagConfig.ENABLED.get();
        } catch (IllegalStateException e) {
            return; // config pas encore chargée, laisser vanilla
        }

        if (!enabled) {
            // Système désactivé = comportement HideNametagsPacket actuel
            // On ne cancel pas ici : HideNametagsPacket gère déjà ce cas
            return;
        }

        // ─── Étape 1 : uniquement les joueurs ─────────────────────────────────
        Entity entity = event.getEntity();
        if (!(entity instanceof AbstractClientPlayer target)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Ne pas traiter son propre nametag (vanilla ne le montre pas non plus)
        if (target.getUUID().equals(mc.player.getUUID())) return;

        UUID targetUUID = target.getUUID();

        // ─── Étape 2 : récupérer les données du cache ─────────────────────────
        ClientNametagCache.NametagData data = ClientNametagCache.get(targetUUID);
        // Si pas de données encore (packet pas encore reçu), laisser vanilla
        if (data == null) return;

        // ─── Étape 3 : staff voit tout ────────────────────────────────────────
        boolean staffAlwaysSeeReal;
        try {
            staffAlwaysSeeReal = NametagConfig.STAFF_ALWAYS_SEE_REAL.get();
        } catch (IllegalStateException e) {
            staffAlwaysSeeReal = true;
        }

        ClientNametagCache.NametagData selfData = ClientNametagCache.get(mc.player.getUUID());
        boolean viewerIsStaff = selfData != null && selfData.isStaff();
        if (viewerIsStaff && staffAlwaysSeeReal) {
            // Montrer le nom lisible sans passer par le reste des checks
            event.setContent(buildReadable(data, target));
            event.setCanRender(TriState.TRUE);
            return;
        }

        // ─── Étape 4 : render distance ────────────────────────────────────────
        double renderDist;
        try {
            renderDist = NametagConfig.RENDER_DISTANCE.get();
        } catch (IllegalStateException e) {
            renderDist = -1.0;
        }

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double distSq = cam.distanceToSqr(target.getEyePosition());

        if (renderDist > 0 && distSq > renderDist * renderDist) {
            event.setCanRender(TriState.FALSE);
            return;
        }

        // ─── Étape 5 : occlusion derrière un bloc ─────────────────────────────
        boolean hideBehindBlocks;
        try {
            hideBehindBlocks = NametagConfig.HIDE_BEHIND_BLOCKS.get();
        } catch (IllegalStateException e) {
            hideBehindBlocks = true;
        }

        if (hideBehindBlocks) {
            boolean occluded = isOccluded(mc, target, targetUUID, cam);
            if (occluded) {
                event.setCanRender(TriState.FALSE);
                return;
            }
        }

        // ─── Étape 6 : sneak ──────────────────────────────────────────────────
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

        // ─── Étape 7 : obfuscation distance ───────────────────────────────────
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
            // Au-delà de la distance d'obfuscation → nom obfusqué
            String realName = target.getGameProfile().getName();
            event.setContent(NametagFormatter.formatObfuscated(realName.length()));
            event.setCanRender(TriState.TRUE);
            return;
        }

        // ─── Étape 8 : nom lisible ────────────────────────────────────────────
        event.setContent(buildReadable(data, target));
        event.setCanRender(TriState.TRUE);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Effectue le raycast avec cache TTL 50ms.
     * Retourne true si un bloc opaque se trouve entre la caméra et les yeux de la cible.
     */
    private static boolean isOccluded(Minecraft mc, AbstractClientPlayer target,
                                       UUID uuid, Vec3 cam) {
        NametagOcclusionCache.Entry cached = NametagOcclusionCache.get(uuid);

        if (cached != null && !cached.isExpired()) {
            return cached.occluded();
        }

        // Raycast depuis la caméra vers les yeux du joueur cible
        Vec3 eyePos = target.getEyePosition();
        ClipContext ctx = new ClipContext(
                cam,
                eyePos,
                ClipContext.Block.COLLIDER,   // blocs solides seulement
                ClipContext.Fluid.NONE,        // ignorer les fluides
                target                         // exclure l'entité cible elle-même
        );

        BlockHitResult result = mc.level.clip(ctx);
        boolean occluded = result.getType() == HitResult.Type.BLOCK;

        NametagOcclusionCache.put(uuid, occluded);
        return occluded;
    }

    /**
     * Construit le Component pour un nametag lisible (via NametagFormatter).
     */
    private static Component buildReadable(ClientNametagCache.NametagData data,
                                            AbstractClientPlayer target) {
        String realName = target.getGameProfile().getName();
        return NametagFormatter.formatReadable(data, realName);
    }
}
