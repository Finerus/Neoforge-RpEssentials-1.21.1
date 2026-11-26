package net.rp.rpessentials.client.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.client.RpKeyBindings;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes a texture template of every element of the open RpEssentials screen, at its exact size in GUI pixels.
 * <p>
 * Press the "Export GUI templates" key (F8 by default) on a screen (and on each tab). The files go to
 * {@code <game dir>/rpessentials/gui_templates/}, laid out like the mod resources
 * ({@code assets/rpessentials/textures/gui/sprites/<screen>/<tab>/...}), each PNG prefilled with the texture
 * used today and with its {@code .mcmeta}. {@code layout/} holds a screenshot with every element outlined
 * and the list of elements. Existing files are never overwritten (delete them to export again).
 */
@EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT)
public final class GuiTemplateExporter {

    private GuiTemplateExporter() {}

    /** Element seen during the capture. {@code variable}: drawn at several sizes (kept in nine-slice). */
    private record Rec(String scope, String base, String id, int x, int y, int w, int h, boolean variable, boolean tab) {
        String name() { return scope + base + (id != null ? "_" + id : ""); }
    }

    /** Elements whose height follows their content (notes, reports): always nine-slice. */
    private static final java.util.Set<String> VARIABLE = java.util.Set.of(GuiSkin.NOTE_ROW);

    private static boolean pending = false;
    private static boolean capturing = false;
    private static final Map<String, Rec> recs = new LinkedHashMap<>();

    /**
     * Texture-making tool, not for players: on in the development environment (runClient), or when the game
     * is started with the Java argument {@code -Drpessentials.guiExport=true}. Off in the published mod
     * (the key is not even registered).
     */
    public static boolean enabled() {
        return !net.neoforged.fml.loading.FMLEnvironment.production || Boolean.getBoolean("rpessentials.guiExport");
    }

    /** True during the frame captured by F8. */
    public static boolean capturing() {
        return capturing;
    }

    /** Called by GuiSkin / SkinnedButton for every element drawn. */
    public static void record(String base, String id, String state, int x, int y, int w, int h) {
        if (!capturing || w <= 0 || h <= 0) return;
        // Sorted by category: tab content in the tab folder, shared elements in the screen folder.
        String scope = GuiSkin.scopes().get(0);
        Rec r = new Rec(scope, base, id, x, y, w, h, VARIABLE.contains(base), GuiSkin.inTab());
        Rec old = recs.get(r.name());
        if (old == null) recs.put(r.name(), r);
        else if (old.w != w || old.h != h) recs.put(r.name(), new Rec(scope, base, id, old.x, old.y, Math.max(old.w, w), Math.max(old.h, h), true, old.tab));
    }

    // ── Events ───────────────────────────────────────────────────────────────

    private static boolean ours(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("net.rp.rpessentials.");
    }

    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!enabled() || RpKeyBindings.EXPORT_GUI_TEMPLATES == null || !ours(event.getScreen())) return;
        if (RpKeyBindings.EXPORT_GUI_TEMPLATES.matches(event.getKeyCode(), event.getScanCode())) {
            pending = true;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        GuiSkin.beginFrame();
        if (pending && ours(event.getScreen())) {
            pending = false;
            recs.clear();
            capturing = true;
        }
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!capturing) return;
        capturing = false;
        event.getGuiGraphics().flush();
        try {
            export(event.getScreen());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[GuiTemplates] Export failed", e);
            toast("rpessentials.gui.templates.failed", e.getMessage() == null ? e.toString() : e.getMessage());
        }
        recs.clear();
    }

    // ── Export ───────────────────────────────────────────────────────────────

    private static void export(Screen screen) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        Path root = mc.gameDirectory.toPath().resolve("rpessentials").resolve("gui_templates");
        Path sprites = root.resolve("assets").resolve(RpEssentials.MODID).resolve("textures").resolve("gui").resolve("sprites");
        String scope = GuiSkin.scopes().get(0);
        String layoutName = scope.isEmpty() ? "root" : scope.substring(0, scope.length() - 1).replace('/', '.');
        if (screen instanceof SkinScope s && s.skinTab() != null && !s.skinTab().isEmpty()) {
            layoutName += "." + s.skinTab();
        }

        // Canvas screens have a fixed size: textures are drawn as they are (no .mcmeta, no nine-slice).
        CanvasScreen canvas = screen instanceof CanvasScreen c && c.canvasActive() ? c : null;
        int written = 0, kept = 0;
        StringBuilder list = new StringBuilder();
        list.append("Screen: ").append(scope).append("   (").append(canvas != null ? "fixed canvas " : "GUI size ")
                .append(screen.width).append("x").append(screen.height).append(")\n");
        list.append(canvas != null
                ? "Fixed canvas: each PNG is shown pixel for pixel, at the size below.\n\n"
                : "Sizes are in GUI pixels (1 texture pixel = 1 GUI pixel). Legacy look: sizes follow the window size.\n\n");
        // An element alone of its kind in its folder gets the short name (list_row.png, not list_row_players.png).
        Map<String, Integer> kinds = new java.util.HashMap<>();
        for (Rec r : recs.values()) kinds.merge(r.scope + r.base, 1, Integer::sum);
        int n = 0;
        for (Rec r : recs.values()) {
            n++;
            String file = kinds.get(r.scope + r.base) == 1 ? r.scope + r.base : r.name();
            boolean oldTab = GuiSkin.setTab(r.tab);
            try {
            list.append(String.format("%2d  %-55s x=%-4d y=%-4d %dx%d%s%n", n, file + ".png", r.x, r.y, r.w, r.h,
                    r.variable ? "   (size varies: nine-slice, see .mcmeta)" : ""));
            for (String state : states(r.base)) {
                Path png = sprites.resolve(file + state + ".png");
                if (Files.exists(png)) { kept++; continue; }
                Files.createDirectories(png.getParent());
                Source src = source(r.base, r.id, state);
                int border = src.border >= 0 ? src.border : 4;
                border = Math.max(0, Math.min(border, Math.min(r.w, r.h) / 2));
                try (NativeImage img = render(src, r.w, r.h, border)) {
                    img.writeToFile(png);
                }
                if (canvas == null || r.variable) {
                    Files.writeString(png.resolveSibling(png.getFileName() + ".mcmeta"), mcmeta(r.w, r.h, border), StandardCharsets.UTF_8);
                }
                written++;
            }
            } finally {
                GuiSkin.setTab(oldTab);
            }
        }

        // Layout: screenshot with every element outlined, and the list.
        Path layout = root.resolve("layout");
        Files.createDirectories(layout);
        Files.writeString(layout.resolve(layoutName + ".txt"), list.toString(), StandardCharsets.UTF_8);
        int[] colors = { 0xFF0000FF, 0xFF00FF00, 0xFFFF8800, 0xFF00FFFF, 0xFFFF00FF, 0xFF00AAFF }; // ABGR
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            if (canvas != null) {
                // The canvas at its real size (1 pixel = 1 canvas pixel): a template to draw over, clean and outlined.
                try (NativeImage img = new NativeImage(screen.width, screen.height, true)) {
                    double k = canvas.realScale();
                    for (int y = 0; y < screen.height; y++) for (int x = 0; x < screen.width; x++) {
                        int sx = canvas.realOffsetX() + (int) Math.floor((x + 0.5) * k);
                        int sy = canvas.realOffsetY() + (int) Math.floor((y + 0.5) * k);
                        if (sx >= 0 && sy >= 0 && sx < shot.getWidth() && sy < shot.getHeight()) img.setPixelRGBA(x, y, shot.getPixelRGBA(sx, sy));
                    }
                    img.writeToFile(layout.resolve(layoutName + ".png"));
                    int c = 0;
                    for (Rec r : recs.values()) outline(img, r.x, r.y, r.w, r.h, colors[c++ % colors.length]);
                    img.writeToFile(layout.resolve(layoutName + "_outlined.png"));
                }
            } else {
                double scale = mc.getWindow().getGuiScale();
                int c = 0;
                for (Rec r : recs.values()) {
                    outline(shot, (int) (r.x * scale), (int) (r.y * scale), (int) (r.w * scale), (int) (r.h * scale), colors[c++ % colors.length]);
                }
                shot.writeToFile(layout.resolve(layoutName + ".png"));
            }
        }
        writeReadme(root);

        RpEssentials.LOGGER.info("[GuiTemplates] {}: {} files written, {} already there, in {}", scope, written, kept, root);
        toast("rpessentials.gui.templates.done", I18nArgs.of(written, kept, layoutName));
    }

    /** Every state of an element, so hovered / disabled / selected variants are exported too. */
    private static List<String> states(String base) {
        return switch (base) {
            case GuiSkin.BUTTON -> List.of("", "_hover", "_disabled");
            case GuiSkin.LIST_ROW -> List.of("", "_hover", "_selected");
            case GuiSkin.TAB -> List.of("_active", "_inactive");
            default -> List.of("");
        };
    }

    private record Source(NativeImage image, int border, boolean nineSlice) {}

    /** Texture used today for this element (most specific sprite found), with its nine-slice border. */
    private static Source source(String base, String id, String state) {
        for (String name : GuiSkin.candidates(base, id, state)) {
            ResourceLocation png = ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "textures/gui/sprites/" + name + ".png");
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(png);
            if (res.isEmpty()) continue;
            try (InputStream in = res.get().open()) {
                NativeImage img = NativeImage.read(in);
                int border = -1;
                boolean nine = false;
                ResourceLocation meta = ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "textures/gui/sprites/" + name + ".png.mcmeta");
                Optional<Resource> m = Minecraft.getInstance().getResourceManager().getResource(meta);
                if (m.isPresent()) {
                    try (Reader rd = m.get().openAsReader()) {
                        JsonObject scaling = JsonParser.parseReader(rd).getAsJsonObject()
                                .getAsJsonObject("gui").getAsJsonObject("scaling");
                        nine = "nine_slice".equals(scaling.get("type").getAsString());
                        if (nine && scaling.get("border").isJsonPrimitive()) border = scaling.get("border").getAsInt();
                    } catch (Exception ignored) {}
                }
                return new Source(img, border, nine);
            } catch (Exception e) {
                RpEssentials.LOGGER.warn("[GuiTemplates] Cannot read {}", png, e);
            }
        }
        return new Source(null, -1, false);
    }

    /** The source texture drawn at w x h (nine-slice or stretched), or a grey frame when there is none. */
    private static NativeImage render(Source src, int w, int h, int border) {
        NativeImage out = new NativeImage(w, h, true);
        NativeImage in = src.image;
        try {
            if (in == null) {
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                    out.setPixelRGBA(x, y, edge ? 0xFF555555 : 0xCC222222);
                }
                return out;
            }
            int sw = in.getWidth(), sh = in.getHeight();
            for (int y = 0; y < h; y++) {
                int sy = src.nineSlice ? slice(y, h, sh, Math.min(border, sh / 2)) : y * sh / h;
                for (int x = 0; x < w; x++) {
                    int sx = src.nineSlice ? slice(x, w, sw, Math.min(border, sw / 2)) : x * sw / w;
                    out.setPixelRGBA(x, y, in.getPixelRGBA(sx, sy));
                }
            }
            return out;
        } finally {
            if (in != null) in.close();
        }
    }

    /** Source coordinate of a nine-slice: borders copied, middle repeated. */
    private static int slice(int d, int size, int srcSize, int b) {
        if (d < b) return d;
        if (d >= size - b) return srcSize - (size - d);
        int mid = srcSize - 2 * b;
        return mid <= 0 ? Math.min(b, srcSize - 1) : b + (d - b) % mid;
    }

    private static String mcmeta(int w, int h, int border) {
        return "{\n  \"gui\": {\n    \"scaling\": {\n      \"type\": \"nine_slice\",\n"
                + "      \"width\": " + w + ",\n      \"height\": " + h + ",\n      \"border\": " + border + "\n    }\n  }\n}\n";
    }

    private static void outline(NativeImage img, int x, int y, int w, int h, int abgr) {
        for (int i = 0; i < w; i++) { px(img, x + i, y, abgr); px(img, x + i, y + h - 1, abgr); }
        for (int j = 0; j < h; j++) { px(img, x, y + j, abgr); px(img, x + w - 1, y + j, abgr); }
    }

    private static void px(NativeImage img, int x, int y, int abgr) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setPixelRGBA(x, y, abgr);
    }

    private static void writeReadme(Path root) throws Exception {
        Path readme = root.resolve("README.txt");
        if (Files.exists(readme)) return;
        Files.writeString(readme, """
                RpEssentials - GUI templates
                ============================
                ONE FILE = ONE ELEMENT. Every PNG exported here is the file the game uses for that element,
                at its exact size, prefilled with the current look. Draw over it without changing its size.

                Files are sorted by category:
                  player_profile/              shared by every tab: player list panel and its lines,
                                               filter / refresh / add buttons, tab buttons, right panel, Close...
                  player_profile/<tab>/        only on that tab (profile, stats, notes, manage, history, reports):
                                               e.g. player_profile/profile/button_apply.png,
                                                    player_profile/notes/text_field_note.png
                Each element has its normal state and, for buttons, _hover / _disabled (lines: _hover / _selected,
                tabs: _active / _inactive).
                The full list, with positions and sizes: layout/<screen>.<tab>.txt.
                layout/<screen>.<tab>_outlined.png shows where each element is.

                Press F8 on each tab: the shared files are written once, each tab adds its own folder.
                F8 writes every file missing from this folder; existing files are never overwritten.
                An element alone of its kind in its folder has the short name (list_row.png, text_field.png);
                when there are several (buttons), each one has its own name (button_apply.png...).

                Then copy the "assets" folder into a resource pack (or into src/main/resources of the mod).
                You only need to keep the files you changed: a missing file falls back to the generic one.

                Fixed-size screens (480x270 canvas, textured look): no .mcmeta, each PNG is shown pixel for pixel.
                Exception: elements whose height follows their content (a note, a report) keep a .mcmeta in
                "nine_slice": their borders (border = N pixels) stay, the middle is repeated.
                """, StandardCharsets.UTF_8);
    }

    // ── Toast ────────────────────────────────────────────────────────────────

    private record I18nArgs(Object[] args) {
        static I18nArgs of(Object... a) { return new I18nArgs(a); }
    }

    private static void toast(String key, Object detail) {
        Minecraft mc = Minecraft.getInstance();
        Component title = Component.translatable(key + ".title");
        Component body = detail instanceof I18nArgs a
                ? Component.translatable(key, a.args())
                : Component.literal(String.valueOf(detail));
        //#if forge
        //? SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, body);
        //#else
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, body);
        //#endif
    }
}
