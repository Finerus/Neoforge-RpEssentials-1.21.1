package net.rp.rpessentials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
//#if forge
//#else
import net.minecraft.core.component.DataComponents;
//#endif
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
//#if forge
//#else
import net.minecraft.world.item.component.CustomData;
//#endif
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.rp.rpessentials.util.ColorHelper;
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

    public static String shownProfession(CompoundTag tag) {
        String id = tag.getString("professionId");
        String name = ClientTooltipRestrictions.displayName(id);
        if (name == null && tag.contains("professionName")) name = tag.getString("professionName");
        String mode = ClientTooltipRestrictions.idMode();
        if (name == null || name.isBlank() || mode.equals("ID_ONLY")) return id;
        name = name.replace("&", "§");
        return mode.equals("NAME_ONLY") ? name : name + " §8(" + id + ")";
    }

    /** First person: always lit (readable in the dark, like before). */
    public static void renderCard(PoseStack poseStack, MultiBufferSource bufferSource,
                                  ItemStack stack, int combinedLight) {
        renderCard(poseStack, bufferSource, stack, combinedLight, false);
    }

    /** {@code worldLight}: use the light of the place (card held by a player seen in the world). */
    public static void renderCard(PoseStack poseStack, MultiBufferSource bufferSource,
                                  ItemStack stack, int combinedLight, boolean worldLight) {

        Font font = Minecraft.getInstance().font;
        int light = worldLight ? combinedLight : LightTexture.FULL_BRIGHT;

        String profId = "", holder = "?", issueDate = "?";
        String expiryDate = null;
        boolean revoked = false;

        //#if forge
        //? CompoundTag cd = stack.getTag();
        //#else
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        //#endif
        if (cd != null) {
            //#if forge
            //? CompoundTag tag = cd.copy();
            //#else
            CompoundTag tag = cd.copyTag();
            //#endif
            profId = shownProfession(tag);
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

        //#if forge
        //? vc.vertex(poseStack.last().pose(), -HALF_W,  HALF_H, 0f).color(255, 255, 255, 255).uv(0f, 1f).uv2(light).endVertex();
        //? vc.vertex(poseStack.last().pose(),  HALF_W,  HALF_H, 0f).color(255, 255, 255, 255).uv(1f, 1f).uv2(light).endVertex();
        //? vc.vertex(poseStack.last().pose(),  HALF_W, -HALF_H, 0f).color(255, 255, 255, 255).uv(1f, 0f).uv2(light).endVertex();
        //? vc.vertex(poseStack.last().pose(), -HALF_W, -HALF_H, 0f).color(255, 255, 255, 255).uv(0f, 0f).uv2(light).endVertex();
        //#else
        vc.addVertex(poseStack.last(), -HALF_W,  HALF_H, 0f).setColor(255, 255, 255, 255).setUv(0f, 1f).setLight(light);
        vc.addVertex(poseStack.last(),  HALF_W,  HALF_H, 0f).setColor(255, 255, 255, 255).setUv(1f, 1f).setLight(light);
        vc.addVertex(poseStack.last(),  HALF_W, -HALF_H, 0f).setColor(255, 255, 255, 255).setUv(1f, 0f).setLight(light);
        vc.addVertex(poseStack.last(), -HALF_W, -HALF_H, 0f).setColor(255, 255, 255, 255).setUv(0f, 0f).setLight(light);
        //#endif

        drawThickness(vc, poseStack, light);

        poseStack.translate(0f, 0f, -0.2f);

        float textX = -HALF_W + 8f;
        float maxW = CARD_W - 16f;

        drawCentered(font, bufferSource, poseStack,
                revoked
                        ? "§c§l" + I18n.get("rpessentials.license.card.revoked")
                        : "§6§l" + I18n.get("rpessentials.license.card.title"),
                0f, -HALF_H + 7f, light, 1f, maxW);

        float y = -HALF_H + 20f;
        y = drawField(font, bufferSource, poseStack, I18n.get("rpessentials.license.card.profession"), profId, textX, y, light);
        y = drawField(font, bufferSource, poseStack, I18n.get("rpessentials.license.card.holder"), holder, textX, y, light);
        y = drawField(font, bufferSource, poseStack, I18n.get("rpessentials.license.card.issued"), issueDate, textX, y, light);
        if (expiryDate != null) {
            drawField(font, bufferSource, poseStack, I18n.get("rpessentials.license.card.until"), expiryDate, textX, y, light);
        }

        drawCentered(font, bufferSource, poseStack,
                revoked
                        ? "§c" + I18n.get("rpessentials.license.card.invalid")
                        : "§a" + I18n.get("rpessentials.license.card.valid"),
                0f, HALF_H - 12f, light, 0.85f, maxW);

        poseStack.popPose();
    }

    /** Text of the card; scrambled (obfuscated) while the profession system is turned off on the server. */
    private static FormattedCharSequence cardText(String text) {
        net.minecraft.network.chat.MutableComponent c = ColorHelper.parseColors(text).copy();
        if (!ClientFeatures.professionsEnabled()) c = net.minecraft.network.chat.Component.empty()
                .withStyle(st -> st.withObfuscated(true)).append(c);
        return c.getVisualOrderText();
    }

    /** Thickness of the card, in card pixels (128 x 92 card): the back and the edges give it volume. */
    private static final float THICKNESS = 4f;
    /** Texture point used for the edges (the border of the card). */
    private static final float EDGE_U = 0.006f, EDGE_V = 0.5f;

    /**
     * Back face (the card picture, mirrored and darker, without text) and the four edges, behind the
     * front face (z = 0, text toward -z). Drawn on both sides so they show whatever the culling.
     */
    private static void drawThickness(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack ps, int light) {
        var m = ps.last();
        float t = THICKNESS;
        int back = 170, edge = 140;
        // Back: mirrored picture.
        quad(vc, m, light, back,
                -HALF_W, HALF_H, t, 1f, 1f,
                HALF_W, HALF_H, t, 0f, 1f,
                HALF_W, -HALF_H, t, 0f, 0f,
                -HALF_W, -HALF_H, t, 1f, 0f);
        // Edges: border color.
        float[][] sides = {
                {-HALF_W, -HALF_H, HALF_W, -HALF_H},
                {HALF_W, -HALF_H, HALF_W, HALF_H},
                {HALF_W, HALF_H, -HALF_W, HALF_H},
                {-HALF_W, HALF_H, -HALF_W, -HALF_H}};
        for (float[] e : sides) {
            quad(vc, m, light, edge,
                    e[0], e[1], 0f, EDGE_U, EDGE_V,
                    e[2], e[3], 0f, EDGE_U, EDGE_V,
                    e[2], e[3], t, EDGE_U, EDGE_V,
                    e[0], e[1], t, EDGE_U, EDGE_V);
        }
    }

    /** One quad, emitted in both windings (seen from both sides). */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose m, int light, int shade,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4) {
        float[][] p = {{x1, y1, z1, u1, v1}, {x2, y2, z2, u2, v2}, {x3, y3, z3, u3, v3}, {x4, y4, z4, u4, v4}};
        int[][] orders = {{0, 1, 2, 3}, {3, 2, 1, 0}};
        for (int[] o : orders) {
            for (int i : o) {
                //#if forge
                //? vc.vertex(m.pose(), p[i][0], p[i][1], p[i][2]).color(shade, shade, shade, 255).uv(p[i][3], p[i][4]).uv2(light).endVertex();
                //#else
                vc.addVertex(m, p[i][0], p[i][1], p[i][2]).setColor(shade, shade, shade, 255)
                        .setUv(p[i][3], p[i][4]).setLight(light);
                //#endif
            }
        }
    }

    private static void drawCentered(Font font, MultiBufferSource buffer, PoseStack ps, String text,
                                     float x, float y, int light, float scale, float maxWidth) {
        FormattedCharSequence seq = cardText(text);
        float w = font.width(seq) * scale;
        float s = w > maxWidth ? scale * maxWidth / w : scale;
        ps.pushPose();
        ps.translate(x, y, 0f);
        ps.scale(s, s, 1f);
        font.drawInBatch(seq, -font.width(seq) / 2f, 0f, 0xFFFFFF, false, ps.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }

    private static void drawLeft(Font font, MultiBufferSource buffer, PoseStack ps, String text,
                                 float x, float y, int light, float scale, float maxWidth) {
        FormattedCharSequence seq = cardText(text);
        float w = font.width(seq) * scale;
        float s = w > maxWidth ? scale * maxWidth / w : scale;
        ps.pushPose();
        ps.translate(x, y, 0f);
        ps.scale(s, s, 1f);
        font.drawInBatch(seq, 0f, 0f, 0xFFFFFF, false, ps.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }

    private static float drawField(Font font, MultiBufferSource buffer, PoseStack ps, String label,
                                   String value, float x, float y, int light) {
        drawLeft(font, buffer, ps, "§8" + label, x, y, light, 0.7f, CARD_W - 16f);
        drawLeft(font, buffer, ps, "§f" + value, x + 5f, y + 6f, light, 0.8f, CARD_W - 21f);
        return y + 14f;
    }
}