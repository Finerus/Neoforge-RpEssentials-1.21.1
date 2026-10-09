package net.rp.rpessentials.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.rp.rpessentials.client.LicenseCardRenderer;

@OnlyIn(Dist.CLIENT)
public class LicenseScreen extends Screen {

    private static final int TEX_W = 280;
    private static final int TEX_H = 200;

    private final String profId;
    private final String holder;
    private final String issueDate;
    private final String expiryDate;
    private final boolean revoked;

    public LicenseScreen(ItemStack stack) {
        super(Component.literal("License"));

        String p = "", h = "Unknown", d = "Unknown";
        String e = null;
        boolean r = false;

        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            CompoundTag tag = cd.copyTag();
            p = tag.getString("professionId");
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
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int cardY = cy - TEX_H / 2;

        addRenderableWidget(Button.builder(Component.translatable("rpessentials.license.screen.close"), btn -> onClose())
                .pos(cx - 25, cardY + TEX_H + 6)
                .size(50, 16).build());
    }

    private int field(GuiGraphics g, String labelKey, String value, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(this.font, "§8" + I18n.get(labelKey), 0, 0, 0xFFFFFF);
        g.drawString(this.font, "§f" + value, 8, 10, 0xFFFFFF);
        g.pose().popPose();
        return y + 22;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xAA000000);

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

        g.fill(cardX + 14, y, cardX + TEX_W - 14, y + 1, 0x88FFFFFF);
        y += 10;

        y = field(g, "rpessentials.license.card.profession", profId, pad, y);
        y = field(g, "rpessentials.license.card.holder", holder, pad, y);
        y = field(g, "rpessentials.license.card.issued", issueDate, pad, y);
        if (expiryDate != null) y = field(g, "rpessentials.license.card.until", expiryDate, pad, y);

        g.fill(cardX + 14, y + 2, cardX + TEX_W - 14, y + 3, 0x88FFFFFF);
        y += 12;

        g.drawCenteredString(this.font,
                revoked
                        ? "§c" + I18n.get("rpessentials.license.card.invalid")
                        : "§a" + I18n.get("rpessentials.license.card.valid"),
                cx, y, 0xFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

    @Override
    public boolean isPauseScreen() { return false; }
}