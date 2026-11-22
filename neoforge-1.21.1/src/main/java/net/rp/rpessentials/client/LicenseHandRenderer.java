package net.rp.rpessentials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.items.LicenseItem;

/**
 * First person license card, held like a vanilla map: both hands when the other hand is empty,
 * one hand otherwise. Goes through RenderHandEvent so the equip animation (card lowered / raised
 * when it is taken or put away) and the arm swing of vanilla are kept.
 */
@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public final class LicenseHandRenderer {

    /** The card is wider than a map; held in one hand it is drawn at half size. */
    private static final float ONE_HAND_SCALE = 0.5f;

    private LicenseHandRenderer() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof LicenseItem)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isScoping()) return;
        event.setCanceled(true);

        PoseStack ps = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();

        ps.pushPose();
        if (mainHand && player.getOffhandItem().isEmpty()) {
            renderTwoHanded(ps, buffer, light, player, event.getInterpolatedPitch(),
                    event.getEquipProgress(), event.getSwingProgress(), stack);
        } else {
            renderOneHanded(ps, buffer, light, player, event.getEquipProgress(), arm, event.getSwingProgress(), stack);
        }
        ps.popPose();
    }

    // Same transforms as ItemInHandRenderer#renderTwoHandedMap.
    private static void renderTwoHanded(PoseStack ps, MultiBufferSource buffer, int light, LocalPlayer player,
                                        float pitch, float equip, float swing, ItemStack stack) {
        float f = Mth.sqrt(swing);
        float f1 = -0.2F * Mth.sin(swing * (float) Math.PI);
        float f2 = -0.4F * Mth.sin(f * (float) Math.PI);
        ps.translate(0.0F, -f1 / 2.0F, f2);
        float tilt = mapTilt(pitch);
        ps.translate(0.0F, 0.04F + equip * -1.2F + tilt * -0.5F, -0.72F);
        ps.mulPose(Axis.XP.rotationDegrees(tilt * -85.0F));
        if (!player.isInvisible()) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(90.0F));
            renderMapHand(ps, buffer, light, player, HumanoidArm.RIGHT);
            renderMapHand(ps, buffer, light, player, HumanoidArm.LEFT);
            ps.popPose();
        }
        ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(f * (float) Math.PI) * 20.0F));
        LicenseCardRenderer.renderCard(ps, buffer, stack, light);
    }

    // Same transforms as ItemInHandRenderer#renderOneHandedMap.
    private static void renderOneHanded(PoseStack ps, MultiBufferSource buffer, int light, LocalPlayer player,
                                        float equip, HumanoidArm arm, float swing, ItemStack stack) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        ps.translate(side * 0.125F, -0.125F, 0.0F);
        if (!player.isInvisible()) {
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(side * 10.0F));
            renderPlayerArm(ps, buffer, light, player, equip, swing, arm);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(side * 0.51F, -0.08F + equip * -1.2F, -0.75F);
        float f1 = Mth.sqrt(swing);
        float f2 = Mth.sin(f1 * (float) Math.PI);
        ps.translate(side * (-0.5F * f2), 0.4F * Mth.sin(f1 * (float) (Math.PI * 2)) - 0.3F * f2,
                -0.3F * Mth.sin(swing * (float) Math.PI));
        ps.mulPose(Axis.XP.rotationDegrees(f2 * -45.0F));
        ps.mulPose(Axis.YP.rotationDegrees(side * f2 * -30.0F));
        ps.scale(ONE_HAND_SCALE, ONE_HAND_SCALE, ONE_HAND_SCALE);
        LicenseCardRenderer.renderCard(ps, buffer, stack, light);
        ps.popPose();
    }

    private static float mapTilt(float pitch) {
        float f = 1.0F - pitch / 45.0F + 0.1F;
        f = Mth.clamp(f, 0.0F, 1.0F);
        return -Mth.cos(f * (float) Math.PI) * 0.5F + 0.5F;
    }

    private static PlayerRenderer renderer(LocalPlayer player) {
        return (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().<AbstractClientPlayer>getRenderer(player);
    }

    private static void renderMapHand(PoseStack ps, MultiBufferSource buffer, int light, LocalPlayer player, HumanoidArm side) {
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(92.0F));
        ps.mulPose(Axis.XP.rotationDegrees(45.0F));
        ps.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
        ps.translate(f * 0.3F, -1.1F, 0.45F);
        if (side == HumanoidArm.RIGHT) renderer(player).renderRightHand(ps, buffer, light, player);
        else renderer(player).renderLeftHand(ps, buffer, light, player);
        ps.popPose();
    }

    private static void renderPlayerArm(PoseStack ps, MultiBufferSource buffer, int light, LocalPlayer player,
                                        float equip, float swing, HumanoidArm side) {
        boolean right = side != HumanoidArm.LEFT;
        float f = right ? 1.0F : -1.0F;
        float f1 = Mth.sqrt(swing);
        float f2 = -0.3F * Mth.sin(f1 * (float) Math.PI);
        float f3 = 0.4F * Mth.sin(f1 * (float) (Math.PI * 2));
        float f4 = -0.4F * Mth.sin(swing * (float) Math.PI);
        ps.translate(f * (f2 + 0.64000005F), f3 + -0.6F + equip * -0.6F, f4 + -0.71999997F);
        ps.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float f5 = Mth.sin(swing * swing * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);
        ps.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0F));
        ps.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
        ps.translate(f * -1.0F, 3.6F, 3.5F);
        ps.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        ps.mulPose(Axis.XP.rotationDegrees(200.0F));
        ps.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        ps.translate(f * 5.6F, 0.0F, 0.0F);
        if (right) renderer(player).renderRightHand(ps, buffer, light, player);
        else renderer(player).renderLeftHand(ps, buffer, light, player);
    }
}
