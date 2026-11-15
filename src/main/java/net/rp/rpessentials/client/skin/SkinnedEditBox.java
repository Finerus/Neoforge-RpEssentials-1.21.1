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

    public SkinnedEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
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

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderField(g, mouseX, mouseY, partialTick);
        if (error && isVisible()) g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xFFFF4444);
    }

    private void renderField(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isBordered() || !isVisible() || !GuiSkin.active(GuiSkin.TEXT_FIELD)) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        g.blitSprite(GuiSkin.id(GuiSkin.TEXT_FIELD), getX(), getY(), getWidth(), getHeight());
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
