package net.rp.rpessentials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.items.LicenseItem;

/**
 * Third person license (other players, and yourself in F5): the real card is drawn in the hand,
 * readable by the people in front of the holder. The flat item sprite is hidden in third person by
 * the item model (display scale 0).
 */
public class LicenseHeldLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Card size held in one hand: about 0.45 x 0.33 block. */
    private static final float SCALE = 0.5f;
    /** Card size held with both hands, in front of the chest: about 0.5 x 0.36 block. */
    private static final float SCALE_BOTH = 0.55f;
    /**
     * In the body axes of the model (y down, front = -z): turns the card (drawn upright, read from +z)
     * toward the holder, like a map being read: its top leans forward so the text faces the eyes.
     * The people in front see the back of the card (see LicenseCardRenderer, thickness).
     */
    private static final org.joml.Quaternionf CARD_FACING =
            Axis.XP.rotationDegrees(30.0F).mul(Axis.ZP.rotationDegrees(180.0F));

    public LicenseHeldLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (LicenseArmPose.bothHands(player)) {
            ItemStack stack = player.getMainHandItem().getItem() instanceof LicenseItem
                    ? player.getMainHandItem() : player.getOffhandItem();
            renderBothHands(ps, buffer, light, stack);
            return;
        }
        boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
        renderArm(ps, buffer, light, rightMain ? player.getMainHandItem() : player.getOffhandItem(), HumanoidArm.RIGHT);
        renderArm(ps, buffer, light, rightMain ? player.getOffhandItem() : player.getMainHandItem(), HumanoidArm.LEFT);
    }

    /**
     * Card held with both hands (arms forward, see LicenseArmPose): in front of the chest, its bottom edge
     * at the hands, facing the people in front of the holder. Follows the body (crouching included).
     */
    private void renderBothHands(PoseStack ps, MultiBufferSource buffer, int light, ItemStack stack) {
        ps.pushPose();
        if (getParentModel().young) {
            ps.translate(0.0F, 0.75F, 0.0F);
            ps.scale(0.5F, 0.5F, 0.5F);
        }
        getParentModel().body.translateAndRotate(ps);
        ps.translate(0.0F, 0.31F, -0.57F);
        ps.mulPose(CARD_FACING);
        ps.scale(SCALE_BOTH, SCALE_BOTH, SCALE_BOTH);
        LicenseCardRenderer.renderCard(ps, buffer, stack, light, true);
        ps.popPose();
    }

    private void renderArm(PoseStack ps, MultiBufferSource buffer, int light, ItemStack stack, HumanoidArm arm) {
        if (!(stack.getItem() instanceof LicenseItem)) return;
        float side = arm == HumanoidArm.LEFT ? -1f : 1f;
        ps.pushPose();
        if (getParentModel().young) {
            ps.translate(0.0F, 0.75F, 0.0F);
            ps.scale(0.5F, 0.5F, 0.5F);
        }
        // At the hand (bottom of the arm), then back to the body axes: the card stays upright whatever
        // the arm pose (raised forward by LicenseArmPose).
        net.minecraft.client.model.geom.ModelPart part = arm == HumanoidArm.RIGHT
                ? getParentModel().rightArm : getParentModel().leftArm;
        getParentModel().translateToHand(arm, ps);
        ps.translate(side / 16.0F, 0.625F, 0.0F);
        ps.mulPose(new org.joml.Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).conjugate());
        // Card above the fist, facing the people in front of the holder, slightly tilted toward them.
        ps.translate(0.0F, -0.15F, -0.06F);
        ps.mulPose(CARD_FACING);
        ps.scale(SCALE, SCALE, SCALE);
        LicenseCardRenderer.renderCard(ps, buffer, stack, light, true);
        ps.popPose();
    }

    /** Adds the layer to both player models (wide and slim arms). */
    @EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String model : event.getSkins()) {
                if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                    renderer.addLayer(new LicenseHeldLayer(renderer));
                }
            }
        }
    }
}
