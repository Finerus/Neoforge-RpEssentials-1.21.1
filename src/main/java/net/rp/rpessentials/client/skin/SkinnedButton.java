package net.rp.rpessentials.client.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.Mth;

import java.util.function.Function;

/**
 * Vanilla button that uses the RpEssentials sprites in textured mode:
 * button / button_hover / button_disabled, list_row(_hover/_selected) for list lines,
 * tab_active / tab_inactive for tabs. A button with an id (see {@link #named}) first looks for
 * button_&lt;id&gt;(_hover/_disabled), a tab for tab_&lt;id&gt;_active... (see {@link GuiSkin} for the folders).
 * Falls back to the vanilla look when no sprite is found.
 */
public class SkinnedButton extends Button {

    public enum Kind { BUTTON, ROW, TAB }

    private final Kind kind;
    private final boolean selected;
    /** Own texture id (button_&lt;id&gt;), or null. */
    private final String skinId;
    /** Built as content of the open tab: its texture is in the tab folder (see GuiSkin). */
    private final boolean tabContent = GuiSkin.inTab();

    public SkinnedButton(Button.Builder builder) {
        this(builder, Kind.BUTTON, false, null);
    }

    protected SkinnedButton(Button.Builder builder, String skinId) {
        this(builder, Kind.BUTTON, false, skinId);
    }

    protected SkinnedButton(Button.Builder builder, Kind kind, boolean selected, String skinId) {
        super(builder);
        this.kind = kind;
        this.selected = selected;
        this.skinId = skinId;
    }

    /** For {@code Button.builder(...).build(SkinnedButton.named("apply"))}: looks for button_apply first. */
    public static Function<Button.Builder, Button> named(String id) {
        return b -> new SkinnedButton(b, Kind.BUTTON, false, id);
    }

    /** For {@code Button.builder(...).build(SkinnedButton.row(isSelected))}. */
    public static Function<Button.Builder, Button> row(boolean selected) {
        return b -> new SkinnedButton(b, Kind.ROW, selected, null);
    }

    /** List line of a named list: list_row_<id>(_hover/_selected) first. */
    public static Function<Button.Builder, Button> row(boolean selected, String id) {
        return b -> new SkinnedButton(b, Kind.ROW, selected, id);
    }

    /** For {@code Button.builder(...).build(SkinnedButton.tab(isActive))}. */
    public static Function<Button.Builder, Button> tab(boolean active) {
        return b -> new SkinnedButton(b, Kind.TAB, active, null);
    }

    /** Tab with its own texture: tab_&lt;id&gt;_active / tab_&lt;id&gt;_inactive first. */
    public static Function<Button.Builder, Button> tab(boolean active, String id) {
        return b -> new SkinnedButton(b, Kind.TAB, active, id);
    }

    private String base() {
        return switch (kind) {
            case ROW -> GuiSkin.LIST_ROW;
            case TAB -> GuiSkin.TAB;
            default -> GuiSkin.BUTTON;
        };
    }

    private String state() {
        boolean hover = isHoveredOrFocused();
        return switch (kind) {
            case ROW -> selected ? "_selected" : hover && active ? "_hover" : "";
            case TAB -> selected ? "_active" : "_inactive";
            default -> !active ? "_disabled" : hover ? "_hover" : "";
        };
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean old = GuiSkin.setTab(tabContent);
        try {
            renderSkinned(g, mouseX, mouseY, partialTick);
        } finally {
            GuiSkin.setTab(old);
        }
    }

    /** Adds this button to the F8 export without drawing it (list rows scrolled out of view). */
    public void recordForExport() {
        boolean old = GuiSkin.setTab(tabContent);
        try {
            GuiTemplateExporter.record(base(), skinId, state(), getX(), getY(), getWidth(), getHeight());
        } finally {
            GuiSkin.setTab(old);
        }
    }

    private void renderSkinned(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String base = base(), state = state();
        GuiTemplateExporter.record(base, skinId, state, getX(), getY(), getWidth(), getHeight());
        String sprite = GuiSkin.resolve(base, skinId, state);
        if (sprite == null) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        g.setColor(1f, 1f, 1f, this.alpha);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        GuiSprites.blit(g, GuiSkin.id(sprite), getX(), getY(), getWidth(), getHeight());
        g.setColor(1f, 1f, 1f, 1f);
        int color = active ? 0xFFFFFF : 0xA0A0A0;
        renderString(g, Minecraft.getInstance().font, color | Mth.ceil(this.alpha * 255.0F) << 24);
    }
}
