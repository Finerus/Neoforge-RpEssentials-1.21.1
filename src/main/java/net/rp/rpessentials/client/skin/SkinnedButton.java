package net.rp.rpessentials.client.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.Mth;

import java.util.function.Function;

/**
 * Vanilla button that uses the RpEssentials sprites in textured mode:
 * button / button_hover / button_disabled, list_row(_hover/_selected) for list lines,
 * tab_active / tab_inactive for tabs. Falls back to the vanilla look when a sprite is missing.
 */
public class SkinnedButton extends Button {

    public enum Kind { BUTTON, ROW, TAB }

    private final Kind kind;
    private final boolean selected;

    public SkinnedButton(Button.Builder builder) {
        this(builder, Kind.BUTTON, false);
    }

    protected SkinnedButton(Button.Builder builder, Kind kind, boolean selected) {
        super(builder);
        this.kind = kind;
        this.selected = selected;
    }

    /** For {@code Button.builder(...).build(SkinnedButton.row(isSelected))}. */
    public static Function<Button.Builder, Button> row(boolean selected) {
        return b -> new SkinnedButton(b, Kind.ROW, selected);
    }

    /** For {@code Button.builder(...).build(SkinnedButton.tab(isActive))}. */
    public static Function<Button.Builder, Button> tab(boolean active) {
        return b -> new SkinnedButton(b, Kind.TAB, active);
    }

    private String spriteName() {
        boolean hover = isHoveredOrFocused();
        return switch (kind) {
            case ROW -> selected ? GuiSkin.LIST_ROW + "_selected" : hover && active ? GuiSkin.LIST_ROW + "_hover" : GuiSkin.LIST_ROW;
            case TAB -> selected ? GuiSkin.TAB + "_active" : GuiSkin.TAB + "_inactive";
            default -> !active ? GuiSkin.BUTTON + "_disabled" : hover ? GuiSkin.BUTTON + "_hover" : GuiSkin.BUTTON;
        };
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String sprite = spriteName();
        if (!GuiSkin.active(sprite)) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        g.setColor(1f, 1f, 1f, this.alpha);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        g.blitSprite(GuiSkin.id(sprite), getX(), getY(), getWidth(), getHeight());
        g.setColor(1f, 1f, 1f, 1f);
        int color = active ? 0xFFFFFF : 0xA0A0A0;
        renderString(g, Minecraft.getInstance().font, color | Mth.ceil(this.alpha * 255.0F) << 24);
    }
}
