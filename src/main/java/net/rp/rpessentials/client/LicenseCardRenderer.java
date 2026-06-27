package net.rp.rpessentials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.RpEssentials;

@OnlyIn(Dist.CLIENT)
public class LicenseCardRenderer {

    public static final ResourceLocation CARD_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "textures/item/license_card.png");

    private static final float CARD_W = 128f;
    private static final float CARD_H = 92f;
    private static final float HALF_W = CARD_W / 2f;
    private static final float HALF_H = CARD_H / 2f;

    public static void applyCardTransform(PoseStack ps, HumanoidArm arm,
                                          float equippedProgress, float swingProgress) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sqrt(swingProgress);
        float f1 = -0.4f * Mth.sin(f * (float) Math.PI);
        float f2 = -0.4f * Mth.sin(swingProgress * (float) Math.PI);

        ps.translate(side * (f1 + 0.56f), -0.52f + equippedProgress * -0.6f + 0.2f * Mth.sin(f * (float) (Math.PI * 2)), f2 - 0.72f);
        ps.mulPose(Axis.XP.rotationDegrees(-15f));
        ps.mulPose(Axis.YP.rotationDegrees(side * 10f));
        ps.mulPose(Axis.ZP.rotationDegrees(side * 5f));
    }

    public static void renderCard(PoseStack poseStack, MultiBufferSource bufferSource,
                                  ItemStack stack, int combinedLight) {

        Font font = Minecraft.getInstance().font;
        int light = LightTexture.FULL_BRIGHT;

        String profId = "", holder = "?", issueDate = "?";
        String expiryDate = null;
        boolean revoked = false;

        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            CompoundTag tag = cd.copyTag();
            profId = tag.getString("professionId");
            if (tag.contains("holderName")) holder = tag.getString("holderName");
            if (tag.contains("issueDate")) issueDate = tag.getString("issueDate");
            if (tag.contains("expiryDate")) expiryDate = tag.getString("expiryDate");
            revoked = tag.getBoolean("revoked");
        }

        poseStack.pushPose();

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.007f, 0.007f, 0.007f);

        var vc = bufferSource.getBuffer(RenderType.text(CARD_TEXTURE));

        vc.addVertex(poseStack.last(), -HALF_W,  HALF_H, 0f).setColor(255, 255, 255, 255).setUv(0f, 1f).setLight(light);
        vc.addVertex(poseStack.last(),  HALF_W,  HALF_H, 0f).setColor(255, 255, 255, 255).setUv(1f, 1f).setLight(light);
        vc.addVertex(poseStack.last(),  HALF_W, -HALF_H, 0f).setColor(255, 255, 255, 255).setUv(1f, 0f).setLight(light);
        vc.addVertex(poseStack.last(), -HALF_W, -HALF_H, 0f).setColor(255, 255, 255, 255).setUv(0f, 0f).setLight(light);

        poseStack.translate(0f, 0f, -0.2f);

        float textX = -HALF_W + 8f;

        drawCentered(font, bufferSource, poseStack,
                revoked
                        ? "§c§l" + I18n.get("rpessentials.license.card.revoked")
                        : "§6§l" + I18n.get("rpessentials.license.card.title"),
                0f, -HALF_H + 8f, light);

        float y = -HALF_H + 22f;

        drawLeft(font, bufferSource, poseStack, "§8" + I18n.get("rpessentials.license.card.profession"), textX, y, light);
        y += 7f;
        drawLeft(font, bufferSource, poseStack, "§f" + profId, textX + 6f, y, light);
        y += 10f;

        drawLeft(font, bufferSource, poseStack, "§8" + I18n.get("rpessentials.license.card.holder"), textX, y, light);
        y += 7f;
        drawLeft(font, bufferSource, poseStack, "§f" + holder, textX + 6f, y, light);
        y += 10f;

        drawLeft(font, bufferSource, poseStack, "§8" + I18n.get("rpessentials.license.card.issued"), textX, y, light);
        y += 7f;
        drawLeft(font, bufferSource, poseStack, "§f" + issueDate, textX + 6f, y, light);
        y += 10f;

        if (expiryDate != null) {
            drawLeft(font, bufferSource, poseStack, "§8" + I18n.get("rpessentials.license.card.until"), textX, y, light);
            y += 7f;
            drawLeft(font, bufferSource, poseStack, "§f" + expiryDate, textX + 6f, y, light);
        }

        drawCentered(font, bufferSource, poseStack,
                revoked
                        ? "§c" + I18n.get("rpessentials.license.card.invalid")
                        : "§a" + I18n.get("rpessentials.license.card.valid"),
                0f, HALF_H - 14f, light);

        poseStack.popPose();
    }

    private static void drawCentered(Font font, MultiBufferSource buffer, PoseStack ps, String text, float x, float y, int light) {
        FormattedCharSequence seq = ColorHelper.parseColors(text).getVisualOrderText();
        font.drawInBatch(seq, x - font.width(seq) / 2f, y, 0xFFFFFF, false, ps.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, light);
    }

    private static void drawLeft(Font font, MultiBufferSource buffer, PoseStack ps, String text, float x, float y, int light) {
        FormattedCharSequence seq = ColorHelper.parseColors(text).getVisualOrderText();
        font.drawInBatch(seq, x, y, 0xFFFFFF, false, ps.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, light);
    }
}