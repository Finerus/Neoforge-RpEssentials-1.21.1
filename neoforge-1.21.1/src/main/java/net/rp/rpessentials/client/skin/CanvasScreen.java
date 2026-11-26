package net.rp.rpessentials.client.skin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Screen laid out on a fixed canvas of {@value #W}x{@value #H} GUI pixels (16:9) in the textured look.
 * <p>
 * The canvas is enlarged by a whole number of real pixels (x2, x3, x4...) and centered, so a texture drawn at
 * the canvas size is shown pixel for pixel, whatever the window size: no nine-slice needed. When a whole number
 * would leave more than 20% of the window empty (small windows), the canvas is enlarged to fill it instead.
 * In the legacy look the screen uses the whole window, as before.
 * <p>
 * Subclasses implement {@link #initContent()}, {@link #renderContent} and the {@code ...Canvas} mouse methods;
 * every coordinate they get is a canvas coordinate. {@link #renderScreen} draws the widgets (vanilla render).
 */
public abstract class CanvasScreen extends Screen {

    public static final int W = 480;
    public static final int H = 270;

    /** Screen being drawn on its canvas right now (for the scissor mixin), or null. */
    private static CanvasScreen rendering;

    /** Real pixels per canvas pixel, and canvas position in real pixels. */
    private double realScale = 1;
    private int realOffX, realOffY;

    protected CanvasScreen(Component title) {
        super(title);
    }

    /** True when this screen is laid out on the canvas (textured look). */
    public boolean canvasActive() {
        return GuiSkin.textured();
    }

    public static CanvasScreen rendering() {
        return rendering;
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    @Override
    protected final void init() {
        Window win = Minecraft.getInstance().getWindow();
        if (canvasActive()) {
            this.width = W;
            this.height = H;
            computeTransform();
        } else {
            this.width = win.getGuiScaledWidth();
            this.height = win.getGuiScaledHeight();
        }
        initContent();
    }

    /** Builds the widgets (former init()). {@code width} / {@code height} are the canvas size in the textured look. */
    protected abstract void initContent();

    private void computeTransform() {
        Window win = Minecraft.getInstance().getWindow();
        int fbW = win.getWidth(), fbH = win.getHeight();
        double fit = Math.min(fbW / (double) W, fbH / (double) H);
        double whole = Math.floor(fit);
        realScale = whole >= 1 && whole / fit >= 0.8 ? whole : fit;
        realOffX = (int) Math.floor((fbW - W * realScale) / 2);
        realOffY = (int) Math.floor((fbH - H * realScale) / 2);
    }

    private double guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    /** Canvas pixel → GUI pixel scale. */
    private double scale() { return realScale / guiScale(); }
    private double offX() { return realOffX / guiScale(); }
    private double offY() { return realOffY / guiScale(); }

    private double toCanvasX(double mx) { return canvasActive() ? (mx - offX()) / scale() : mx; }
    private double toCanvasY(double my) { return canvasActive() ? (my - offY()) / scale() : my; }

    /** Real pixels per canvas pixel (1 GUI pixel in the legacy look = the GUI scale). */
    public double realScale() { return canvasActive() ? realScale : guiScale(); }
    public int realOffsetX() { return canvasActive() ? realOffX : 0; }
    public int realOffsetY() { return canvasActive() ? realOffY : 0; }

    /** Scissor rectangle (canvas pixels) in real pixels, OpenGL style (x, y from the bottom, w, h). */
    public int[] scissorToReal(ScreenRectangle r) {
        int fbH = Minecraft.getInstance().getWindow().getHeight();
        int x = (int) Math.round(realOffX + r.left() * realScale);
        int top = (int) Math.round(realOffY + r.top() * realScale);
        int right = (int) Math.round(realOffX + r.right() * realScale);
        int bottom = (int) Math.round(realOffY + r.bottom() * realScale);
        return new int[] { x, fbH - bottom, Math.max(0, right - x), Math.max(0, bottom - top) };
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!canvasActive()) {
            renderContent(g, mouseX, mouseY, partialTick);
            return;
        }
        computeTransform();
        // Whole window: the vanilla darkening (also around the canvas, for other window ratios).
        GuiSkin.vanillaDarken(g, g.guiWidth(), g.guiHeight());
        g.flush();
        g.pose().pushPose();
        g.pose().translate(offX(), offY(), 0);
        g.pose().scale((float) scale(), (float) scale(), 1f);
        rendering = this;
        try {
            renderContent(g, (int) Math.floor(toCanvasX(mouseX)), (int) Math.floor(toCanvasY(mouseY)), partialTick);
            g.flush();
        } finally {
            rendering = null;
            g.pose().popPose();
        }
    }

    /** Draws the screen (former render()), in canvas coordinates. */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    //#if forge
    //? /** Vanilla render: background then every widget (1.20.1 does not draw the background in Screen.render). */
    //#else
    /** Vanilla render: background then every widget. */
    //#endif
    protected void renderScreen(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //#if forge
        //? renderBackground(g);
        //#else
        //#endif
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Tooltips are drawn outside the canvas, at the real mouse position. */
    @Override
    public void setTooltipForNextRenderPass(List<FormattedCharSequence> tooltip, ClientTooltipPositioner positioner, boolean override) {
        super.setTooltipForNextRenderPass(tooltip, canvasActive() ? DefaultTooltipPositioner.INSTANCE : positioner, override);
    }

    // ── Mouse (converted to canvas coordinates) ──────────────────────────────

    @Override
    public final boolean mouseClicked(double mx, double my, int button) {
        return mouseClickedCanvas(toCanvasX(mx), toCanvasY(my), button);
    }

    @Override
    public final boolean mouseReleased(double mx, double my, int button) {
        return mouseReleasedCanvas(toCanvasX(mx), toCanvasY(my), button);
    }

    @Override
    public final boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        double s = canvasActive() ? scale() : 1;
        return mouseDraggedCanvas(toCanvasX(mx), toCanvasY(my), button, dx / s, dy / s);
    }

    @Override
    //#if forge
    //? public final boolean mouseScrolled(double mx, double my, double delta) {
        //? // 1.20.1 has a single (vertical) wheel delta.
        //? return mouseScrolledCanvas(toCanvasX(mx), toCanvasY(my), 0, delta);
    //#else
    public final boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return mouseScrolledCanvas(toCanvasX(mx), toCanvasY(my), scrollX, scrollY);
    //#endif
    }

    @Override
    public final void mouseMoved(double mx, double my) {
        mouseMovedCanvas(toCanvasX(mx), toCanvasY(my));
    }

    @Override
    public final boolean isMouseOver(double mx, double my) {
        return super.isMouseOver(toCanvasX(mx), toCanvasY(my));
    }

    protected boolean mouseClickedCanvas(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
    }

    protected boolean mouseReleasedCanvas(double mx, double my, int button) {
        return super.mouseReleased(mx, my, button);
    }

    protected boolean mouseDraggedCanvas(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    protected boolean mouseScrolledCanvas(double mx, double my, double scrollX, double scrollY) {
        //#if forge
        //? return super.mouseScrolled(mx, my, scrollY);
        //#else
        return super.mouseScrolled(mx, my, scrollX, scrollY);
        //#endif
    }

    protected void mouseMovedCanvas(double mx, double my) {
        super.mouseMoved(mx, my);
    }
}
