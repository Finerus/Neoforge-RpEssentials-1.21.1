package net.rp.rpessentials.client.gui;

import net.rp.rpessentials.client.skin.SkinnedEditBox;
import net.rp.rpessentials.client.skin.SkinnedButton;
import net.rp.rpessentials.client.skin.GuiSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.client.LicenseCardRenderer;

@OnlyIn(Dist.CLIENT)
public class LicenseScreen extends net.rp.rpessentials.client.skin.CanvasScreen {

    private static final int TEX_W = 280;
    private static final int TEX_H = 200;

    private final String profId;
    private final String holder;
    private final String issueDate;
    private final String expiryDate;
    private final boolean revoked;

    public LicenseScreen(ItemStack stack) {
        super(Component.translatable("item.rpessentials.license"));

        String p = "", h = I18n.get("rpessentials.gui.unknown"), d = h;
        String e = null;
        boolean r = false;

        CompoundTag cd = stack.getTag();
        if (cd != null) {
            CompoundTag tag = cd.copy();
            p = LicenseCardRenderer.shownProfession(tag);
            if (tag.contains("holderName"))  h = tag.getString("holderName");
            if (tag.contains("issueDate"))   d = tag.getString("issueDate");
            if (tag.contains("expiryDate"))  e = tag.getString("expiryDate");
            r = tag.getBoolean("revoked");
        }

        this.profId     = p;
        this.holder     = h;
        this.issueDate  = d;
        this.expiryDate = e;
        this.revoked    = r;
    }

    @Override
    protected void initContent() {
        int cx = this.width / 2;
        int cardY = this.height / 2 - TEX_H / 2;
        int btnY = Math.min(cardY + TEX_H + 6, this.height - 22);
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        addRenderableWidget(Button.builder(Component.translatable("rpessentials.license.screen.close"), btn -> onClose())
                .pos(cx - 25, btnY).size(50, 16).build(SkinnedButton.named("close")));
    }

    private static final int FOOTER_Y = 136;

    private int field(GuiGraphics g, String labelKey, String value, int x, int y) {
        String shown = value;
        if (this.font.width(shown) > 260) {
            shown = this.font.plainSubstrByWidth(shown, 260 - this.font.width("...")) + "...";
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(this.font, "§8" + I18n.get(labelKey), 0, 0, 0xFFFFFF);
        g.drawString(this.font, ColorHelper.parseSection("§f" + shown), 8, 10, 0xFFFFFF);
        g.pose().popPose();
        return y + 22;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiSkin.background(g, this.width, this.height, 0xAA000000);

        int cx = this.width  / 2;
        int cy = this.height / 2;
        int cardX = cx - TEX_W / 2;
        int cardY = cy - TEX_H / 2;

        g.blit(LicenseCardRenderer.CARD_TEXTURE, cardX, cardY, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        int y = cardY + 18;
        int pad = cardX + 20;

        g.drawCenteredString(this.font,
                revoked
                        ? "§c§l" + I18n.get("rpessentials.license.card.revoked")
                        : "§6§l" + I18n.get("rpessentials.license.card.title"),
                cx, y, 0xFFFFFF);
        y += 18;

        GuiSkin.separator(g, "header", cardX + 14, y, cardX + TEX_W - 14, y + 1, 0x88FFFFFF);
        y += 10;

        y = field(g, "rpessentials.license.card.profession", profId, pad, y);
        y = field(g, "rpessentials.license.card.holder", holder, pad, y);
        y = field(g, "rpessentials.license.card.issued", issueDate, pad, y);
        if (expiryDate != null) field(g, "rpessentials.license.card.until", expiryDate, pad, y);

        int footerY = cardY + FOOTER_Y;
        GuiSkin.separator(g, "footer", cardX + 14, footerY, cardX + TEX_W - 14, footerY + 1, 0x88FFFFFF);
        g.drawCenteredString(this.font,
                revoked
                        ? "§c" + I18n.get("rpessentials.license.card.invalid")
                        : "§a" + I18n.get("rpessentials.license.card.valid"),
                cx, footerY + 10, 0xFFFFFF);

        renderScreen(g, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public boolean isPauseScreen() { return false; }
}