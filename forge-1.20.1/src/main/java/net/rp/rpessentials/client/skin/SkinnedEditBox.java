package net.rp.rpessentials.client.skin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Text field that uses the text_field sprite in textured mode, the vanilla look otherwise.
 * The text keeps the same position as in a vanilla field.
 */
public class SkinnedEditBox extends EditBox {

    /** Required field left empty when saving: red frame until something is typed in it. */
    private boolean error;
    /** Own texture id (text_field_&lt;id&gt;), or null. */
    private String skinId;
    /** Built as content of the open tab: its texture is in the tab folder (see GuiSkin). */
    private final boolean tabContent = GuiSkin.inTab();

    public SkinnedEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    /** Gives this field its own texture: text_field_&lt;id&gt;.png first (see GuiSkin). */
    public SkinnedEditBox skin(String id) {
        this.skinId = id;
        return this;
    }

    /** Shows (or hides) the red frame of a field that must be filled. Typing in the field hides it. */
    public void setError(boolean error) {
        this.error = error;
    }

    @Override
    public void insertText(String text) {
        super.insertText(text);
        if (!text.isEmpty()) error = false;
    }

    /** Copy of the private EditBox.bordered (no getter in 1.20.1). */
    private boolean bordered = true;

    @Override
    public void setBordered(boolean bordered) {
        this.bordered = bordered;
        super.setBordered(bordered);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderField(g, mouseX, mouseY, partialTick);
        if (error && isVisible()) g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xFFFF4444);
    }

    private void renderField(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean old = GuiSkin.setTab(tabContent);
        try {
            renderFieldSkinned(g, mouseX, mouseY, partialTick);
        } finally {
            GuiSkin.setTab(old);
        }
    }

    private void renderFieldSkinned(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!bordered || !isVisible()
                || !GuiSkin.draw(g, GuiSkin.TEXT_FIELD, skinId, "", getX(), getY(), getWidth(), getHeight())) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        // Draw the text the way a bordered field does (4 px inside, centered), without the vanilla frame.
        int w = getWidth();
        int dy = (getHeight() - 8) / 2;
        setBordered(false);
        setWidth(w - 8);
        g.pose().pushPose();
        g.pose().translate(4, dy, 0);
        try {
            super.renderWidget(g, mouseX - 4, mouseY - dy, partialTick);
        } finally {
            g.pose().popPose();
            setWidth(w);
            setBordered(true);
        }
    }
}
