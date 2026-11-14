package net.rp.rpessentials.client.gui;

import net.rp.rpessentials.client.skin.GuiSkin;
import net.rp.rpessentials.util.ColorHelper;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * List of rows that each hold one or more widgets (a main button, a small delete button...),
 * built on the Minecraft list widget: smooth scrolling, mouse wheel, draggable vanilla scroll bar.
 * <p>
 * The screen keeps one instance, calls {@link #layout} and {@link #setRows} when it rebuilds,
 * and adds the list again: the scroll position is kept.
 */
public class ButtonRowList extends ContainerObjectSelectionList<ButtonRowList.Row> {

    /** Space kept on each side of the rows (the scroll bar sits in the right one). */
    private static final int SIDE = 6;

    private boolean scrollToEnd;

    public ButtonRowList(Minecraft mc, int rowHeight) {
        super(mc, 100, 100, 0, rowHeight);
    }

    /** Places the list. */
    public void layout(int x, int y, int w, int h) {
        // Not updateSizeAndPosition(): in 1.21.1 it also moves the list back to x = 0.
        setSize(w, h);
        setPosition(x, y);
        clampScrollAmount();
    }

    /** Width available for the widgets of a row. */
    public int rowWidth() {
        return getRowWidth();
    }

    /** Replaces the rows and keeps the scroll position (or goes to the end after {@link #scrollToEndOnce}). */
    public void setRows(List<Row> rows) {
        double keep = getScrollAmount();
        replaceEntries(rows);
        setScrollAmount(scrollToEnd ? getMaxScroll() : keep);
        scrollToEnd = false;
    }

    /** The next {@link #setRows} shows the end of the list (for a row that was just added). */
    public void scrollToEndOnce() {
        scrollToEnd = true;
    }

    @Override
    public int getRowWidth() {
        return getWidth() - SIDE * 2;
    }

    /** Same as the hit area of the vanilla list, so what is drawn is what is clicked. */
    @Override
    public int getRowLeft() {
        return getX() + getWidth() / 2 - getRowWidth() / 2;
    }

    @Override
    protected int getScrollbarPosition() {
        return getRight() - SIDE;
    }

    @Override
    protected void renderListBackground(GuiGraphics g) {}

    @Override
    protected void renderListSeparators(GuiGraphics g) {}

    /**
     * One line of the list: widgets and texts placed at an offset from the top left corner of the row,
     * with an optional background shared by several consecutive rows (a "block", e.g. one note).
     */
    public static class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final List<int[]> offsets = new ArrayList<>();
        private final List<FormattedCharSequence> texts = new ArrayList<>();
        private final List<int[]> textPos = new ArrayList<>();
        private final List<net.minecraft.world.item.ItemStack> items = new ArrayList<>();
        private final List<int[]> itemPos = new ArrayList<>();
        private String blockSprite;
        private int blockColor, blockIndex, blockSize = 1;

        /** Adds a widget at {@code offsetX} pixels from the left of the row. */
        public Row add(AbstractWidget w, int offsetX) {
            return add(w, offsetX, 0);
        }

        /** Adds a widget at ({@code offsetX}, {@code offsetY}) from the top left corner of the row. */
        public Row add(AbstractWidget w, int offsetX, int offsetY) {
            widgets.add(w);
            offsets.add(new int[]{offsetX, offsetY});
            return this;
        }

        /** Adds a text (§ codes and hex colors kept) at ({@code offsetX}, {@code offsetY}). */
        public Row text(String text, int offsetX, int offsetY) {
            return text(ColorHelper.parseSection(text).getVisualOrderText(), offsetX, offsetY);
        }

        public Row text(FormattedCharSequence text, int offsetX, int offsetY) {
            texts.add(text);
            textPos.add(new int[]{offsetX, offsetY});
            return this;
        }

        /** Adds an item icon (16×16) at ({@code offsetX}, {@code offsetY}). Empty stacks are ignored. */
        public Row item(net.minecraft.world.item.ItemStack stack, int offsetX, int offsetY) {
            if (stack != null && !stack.isEmpty()) {
                items.add(stack);
                itemPos.add(new int[]{offsetX, offsetY});
            }
            return this;
        }

        /**
         * Background of a block of {@code size} rows; this row is number {@code index} (0 = first).
         * With the drawn interface, {@code sprite} is drawn once over the whole block; otherwise each
         * row fills its part with {@code legacyColor}.
         */
        public Row block(String sprite, int legacyColor, int index, int size) {
            this.blockSprite = sprite;
            this.blockColor = legacyColor;
            this.blockIndex = index;
            this.blockSize = Math.max(1, size);
            return this;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int rowH = ((ButtonRowList) list).itemHeight;
            if (blockColor != 0 || blockSprite != null) {
                boolean sprite = blockSprite != null && GuiSkin.active(blockSprite);
                if (sprite) {
                    // Drawn once, by the first visible row of the block.
                    if (blockIndex == 0 || top < list.getY()) {
                        int blockTop = top - blockIndex * rowH;
                        GuiSkin.draw(g, blockSprite, left, blockTop, width, blockSize * rowH - 2);
                    }
                } else {
                    int bottom = blockIndex == blockSize - 1 ? top + rowH - 2 : top + rowH;
                    g.fill(left, top, left + width, bottom, blockColor);
                }
            }
            for (int i = 0; i < items.size(); i++) {
                g.renderItem(items.get(i), left + itemPos.get(i)[0], top + itemPos.get(i)[1]);
            }
            Font font = Minecraft.getInstance().font;
            for (int i = 0; i < texts.size(); i++) {
                g.drawString(font, texts.get(i), left + textPos.get(i)[0], top + textPos.get(i)[1], 0xFFFFFF, false);
            }
            for (int i = 0; i < widgets.size(); i++) {
                AbstractWidget w = widgets.get(i);
                w.setPosition(left + offsets.get(i)[0], top + offsets.get(i)[1]);
                w.render(g, mouseX, mouseY, partialTick);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }
}
