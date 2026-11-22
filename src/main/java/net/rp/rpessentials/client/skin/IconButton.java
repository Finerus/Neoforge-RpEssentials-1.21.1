package net.rp.rpessentials.client.skin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.rp.rpessentials.RpEssentials;

/**
 * Button showing an icon of {@code assets/rpessentials/textures/gui/icons/} instead of a text,
 * centered on the button. The icon is drawn in both looks (textured and legacy).
 */
public class IconButton extends SkinnedButton {

    /** Size of the icon PNGs, in pixels. */
    public static final int ICON_SIZE = 12;

    public static final String REFRESH = "refresh";

    private final ResourceLocation icon;

    private IconButton(Button.Builder builder, String icon, String skinId) {
        super(builder, skinId);
        this.icon = texture(icon);
    }

    public static ResourceLocation texture(String icon) {
        return ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "textures/gui/icons/" + icon + ".png");
    }

    /** Draws an icon of the icons folder at its real size. */
    public static void drawIcon(GuiGraphics g, String icon, int x, int y) {
        g.blit(texture(icon), x, y, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    /**
     * @param icon   PNG of the icons folder (without .png)
     * @param skinId own button texture (button_&lt;skinId&gt;), or null for the common button texture
     */
    public static Button create(String icon, String skinId, int x, int y, int w, int h, Button.OnPress onPress, Tooltip tooltip) {
        return Button.builder(Component.empty(), onPress)
                .pos(x, y).size(w, h).tooltip(tooltip)
                .build(b -> new IconButton(b, icon, skinId));
    }

    /** Refresh button (refresh icon, refresh tooltip). */
    public static Button refresh(int x, int y, int w, int h, Button.OnPress onPress) {
        return create(REFRESH, "refresh", x, y, w, h, onPress,
                Tooltip.create(Component.translatable("rpessentials.gui.refresh_tooltip")));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(g, mouseX, mouseY, partialTick); // background, empty label
        int s = Math.min(ICON_SIZE, Math.min(getWidth(), getHeight()));
        int ix = getX() + (getWidth() - s) / 2;
        int iy = getY() + (getHeight() - s) / 2;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        if (!active) g.setColor(0.6f, 0.6f, 0.6f, this.alpha);
        else g.setColor(1f, 1f, 1f, this.alpha);
        g.blit(icon, ix, iy, s, s, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
