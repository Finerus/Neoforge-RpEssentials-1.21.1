package net.rp.rpessentials.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.rp.rpessentials.util.ColorHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only list of colored text lines, built on the Minecraft list widget: smooth scrolling,
 * mouse wheel, draggable vanilla scroll bar. Long lines wrap; the following parts are indented.
 * <p>
 * The screen keeps one instance and adds it again after each rebuild, so the scroll position is kept.
 */
public class TextLinesList extends ObjectSelectionList<TextLinesList.Row> {

    private static final int ROW_H = 11;
    private static final int INDENT = 8;

    private final Font font;
    private Object owner;
    private List<String> source = List.of();
    private int wrappedFor = -1;
    /** Light background on every other source line (easier to follow long lists). */
    private boolean striped = true;

    public void setStriped(boolean striped) {
        this.striped = striped;
    }

    public TextLinesList(Minecraft mc) {
        super(mc, 100, 100, 0, ROW_H);
        this.font = mc.font;
    }

    /** Places the list. Lines are wrapped again only when the width changes. */
    public void layout(int x, int y, int w, int h) {
        // Not updateSizeAndPosition(): in 1.21.1 it also moves the list back to x = 0.
        setSize(w, h);
        setPosition(x, y);
        clampScrollAmount();
        rewrap(false);
    }

    /**
     * Sets the lines to show. {@code owner} identifies what they belong to (a player for example):
     * a new owner goes back to the top, the same owner keeps the scroll position.
     */
    public void setLines(Object owner, List<String> lines) {
        boolean newOwner = !Objects.equals(this.owner, owner);
        if (!newOwner && lines.equals(source)) return;
        this.owner = owner;
        this.source = List.copyOf(lines);
        rewrap(true);
        if (newOwner) setScrollAmount(0);
    }

    private void rewrap(boolean force) {
        int w = getRowWidth();
        if (!force && w == wrappedFor) return;
        wrappedFor = w;
        double keep = getScrollAmount();
        List<Row> rows = new ArrayList<>();
        int block = 0;
        for (String line : source) {
            block++;
            if (line.isEmpty()) {
                rows.add(new Row(FormattedCharSequence.EMPTY, 0, Component.empty(), block));
                continue;
            }
            Component c = ColorHelper.parseSection(line);
            List<FormattedCharSequence> parts = font.split(c, Math.max(20, w - 6));
            for (int i = 0; i < parts.size(); i++) {
                rows.add(new Row(parts.get(i), i == 0 ? 0 : INDENT, i == 0 ? c : Component.empty(), block));
            }
        }
        replaceEntries(rows);
        setScrollAmount(keep);
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 10;
    }

    @Override
    public int getRowLeft() {
        return getX();
    }

    @Override
    protected int getScrollbarPosition() {
        return getRight() - 6;
    }

    /** The list sits inside a panel of the screen: no vanilla background nor separators. */
    @Override
    protected void renderListBackground(GuiGraphics g) {}

    @Override
    protected void renderListSeparators(GuiGraphics g) {}

    public class Row extends ObjectSelectionList.Entry<Row> {
        private final FormattedCharSequence text;
        private final int indent;
        private final Component narration;
        /** Index of the source line: every other line gets a light background, wrapped parts included. */
        private final int block;

        Row(FormattedCharSequence text, int indent, Component narration, int block) {
            this.text = text;
            this.indent = indent;
            this.narration = narration;
            this.block = block;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            if (striped && block % 2 == 0) g.fill(left, top - 1, left + width, top - 1 + ROW_H, 0x18FFFFFF); // full row height, no gap between wrapped parts
            g.drawString(font, text, left + 3 + indent, top + 1, 0xFFFFFF, false);
        }

        /** Rows are not selectable: no highlight frame on click. */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }

        @Override
        public Component getNarration() {
            return narration;
        }
    }
}
