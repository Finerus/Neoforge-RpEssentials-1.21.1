package net.rp.rpessentials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.RpEssentials;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class LicenseItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static LicenseItemRenderer instance;

    public static LicenseItemRenderer getInstance() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new LicenseItemRenderer(
                    mc.getBlockEntityRenderDispatcher(),
                    mc.getEntityModels()
            );
        }
        return instance;
    }

    public static final ResourceLocation TEXTURE_VALID =
            ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "textures/item/license_card.png");
    public static final ResourceLocation TEXTURE_REVOKED =
            ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "textures/item/license_card_revoked.png");

    private static final float CARD_W = 128f;
    private static final float CARD_H = 92f;
    private static final float SCALE  = 0.007f;

    public LicenseItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps,
                             MultiBufferSource buffer, int light, int overlay) {

        String profId = "", holder = "?", issueDate = "?", expiryDate = null;
        boolean revoked = false;

        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            CompoundTag tag = cd.copyTag();
            profId    = tag.getString("professionId");
            holder    = tag.contains("holderName")  ? tag.getString("holderName")  : "?";
            issueDate = tag.contains("issueDate")   ? tag.getString("issueDate")   : "?";
            expiryDate = tag.contains("expiryDate") ? tag.getString("expiryDate")  : null;
            revoked   = tag.getBoolean("revoked");
        }

        ps.pushPose();

        boolean firstPerson = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        if (!firstPerson) {
            ps.mulPose(Axis.YP.rotationDegrees(180f));
            ps.scale(0.5f, 0.5f, 0.5f);
        }

        renderCard(ps, buffer, revoked);
        renderText(ps, buffer, profId, holder, issueDate, expiryDate, revoked);

        ps.popPose();
    }

    private void renderCard(PoseStack ps, MultiBufferSource buffer, boolean revoked) {
        float hw = CARD_W / 2f * SCALE;
        float hh = CARD_H / 2f * SCALE;

        ResourceLocation texture = revoked ? TEXTURE_REVOKED : TEXTURE_VALID;
        var vc = buffer.getBuffer(RenderType.text(texture));
        Matrix4f m = ps.last().pose();

        vc.addVertex(m, -hw,  hh, 0f).setColor(255, 255, 255, 255).setUv(0f, 1f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m,  hw,  hh, 0f).setColor(255, 255, 255, 255).setUv(1f, 1f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m,  hw, -hh, 0f).setColor(255, 255, 255, 255).setUv(1f, 0f).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(m, -hw, -hh, 0f).setColor(255, 255, 255, 255).setUv(0f, 0f).setLight(LightTexture.FULL_BRIGHT);
    }

    private void renderText(PoseStack ps, MultiBufferSource buffer,
                            String profId, String holder, String issueDate,
                            String expiryDate, boolean revoked) {
        Font font = Minecraft.getInstance().font;

        ps.pushPose();
        ps.translate(0f, 0f, -0.01f);
        ps.scale(SCALE, -SCALE, SCALE);

        float textX = -CARD_W / 2f + 8f;
        float y = -CARD_H / 2f + 8f;

        drawCentered(font, buffer, ps,
                revoked ? "§c§l[ REVOKED ]" : "§6§l✦  LICENSE  ✦", 0f, y);
        y += 22f;

        drawLabel(font, buffer, ps, "§8Profession", textX, y); y += 7f;
        drawLabel(font, buffer, ps, "§f" + profId, textX + 6f, y); y += 10f;

        drawLabel(font, buffer, ps, "§8Holder", textX, y); y += 7f;
        drawLabel(font, buffer, ps, "§f" + holder, textX + 6f, y); y += 10f;

        drawLabel(font, buffer, ps, "§8Issue Date", textX, y); y += 7f;
        drawLabel(font, buffer, ps, "§f" + issueDate, textX + 6f, y);

        if (expiryDate != null) {
            y += 10f;
            drawLabel(font, buffer, ps, "§8Valid Until", textX, y); y += 7f;
            drawLabel(font, buffer, ps, "§f" + expiryDate, textX + 6f, y);
        }

        drawCentered(font, buffer, ps,
                revoked ? "§c✗ This license is no longer valid" : "§a✓ This license is valid",
                0f, CARD_H / 2f - 14f);

        ps.popPose();
    }

    private void drawCentered(Font font, MultiBufferSource buffer, PoseStack ps,
                              String text, float x, float y) {
        FormattedCharSequence seq = ColorHelper.parseColors(text).getVisualOrderText();
        font.drawInBatch(seq, x - font.width(seq) / 2f, y, 0xFFFFFF, false,
                ps.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    private void drawLabel(Font font, MultiBufferSource buffer, PoseStack ps,
                           String text, float x, float y) {
        FormattedCharSequence seq = ColorHelper.parseColors(text).getVisualOrderText();
        font.drawInBatch(seq, x, y, 0xFFFFFF, false,
                ps.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    // Transform premier-personne : carte tenue à plat face au joueur
    public static void applyCardTransform(PoseStack ps, HumanoidArm arm,
                                          float equipProgress, float swingProgress) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float f  = Mth.sqrt(swingProgress);
        float f1 = -0.4f * Mth.sin(f * (float) Math.PI);
        float f2 = -0.4f * Mth.sin(swingProgress * (float) Math.PI);

        ps.translate(side * (f1 + 0.56f),
                -0.52f + equipProgress * -0.6f + 0.2f * Mth.sin(f * (float)(Math.PI * 2)),
                f2 - 0.72f);
        ps.mulPose(Axis.XP.rotationDegrees(-15f));
        ps.mulPose(Axis.YP.rotationDegrees(side * 10f));
        ps.mulPose(Axis.ZP.rotationDegrees(side * 5f));
    }
}