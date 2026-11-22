package net.rp.rpessentials.client.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.rp.rpessentials.RpEssentials;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 1.20.1 backport of the 1.20.2+ GUI sprites ({@code GuiGraphics.blitSprite}).
 * <p>
 * A sprite {@code rpessentials:foo/bar} is the texture {@code assets/rpessentials/textures/gui/sprites/foo/bar.png}.
 * Its {@code .mcmeta} may hold the same {@code "gui": {"scaling": ...}} section as in 1.20.2+
 * ({@code stretch}, {@code tile}, {@code nine_slice}), so the textures and resource packs of the NeoForge version
 * work unchanged.
 */
public final class GuiSprites {

    private GuiSprites() {}

    private sealed interface Scaling permits Stretch, Tile, NineSlice {}
    private record Stretch() implements Scaling {}
    private record Tile(int width, int height) implements Scaling {}
    private record NineSlice(int width, int height, int left, int top, int right, int bottom) implements Scaling {}

    private record Info(ResourceLocation texture, int texW, int texH, Scaling scaling) {}

    private static final Map<ResourceLocation, Optional<Info>> CACHE = new ConcurrentHashMap<>();

    private static final MetadataSectionSerializer<JsonObject> GUI_SECTION = new MetadataSectionSerializer<>() {
        @Override public String getMetadataSectionName() { return "gui"; }
        @Override public JsonObject fromJson(JsonObject json) { return json; }
    };

    public static void clear() {
        CACHE.clear();
    }

    public static ResourceLocation textureOf(ResourceLocation sprite) {
        return new ResourceLocation(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
    }

    /** True when the PNG of the sprite exists (in the mod or a resource pack). */
    public static boolean exists(ResourceLocation sprite) {
        return info(sprite).isPresent();
    }

    private static Optional<Info> info(ResourceLocation sprite) {
        return CACHE.computeIfAbsent(sprite, GuiSprites::load);
    }

    private static Optional<Info> load(ResourceLocation sprite) {
        ResourceLocation tex = textureOf(sprite);
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return Optional.empty();
        try {
            int w, h;
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                w = img.getWidth();
                h = img.getHeight();
            }
            Scaling scaling = new Stretch();
            Optional<JsonObject> gui = res.get().metadata().getSection(GUI_SECTION);
            if (gui.isPresent() && gui.get().has("scaling")) scaling = parse(GsonHelper.getAsJsonObject(gui.get(), "scaling"), w, h);
            return Optional.of(new Info(tex, w, h, scaling));
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[RpEssentials] Could not read GUI sprite {}: {}", tex, e.toString());
            return Optional.empty();
        }
    }

    private static Scaling parse(JsonObject s, int texW, int texH) {
        String type = GsonHelper.getAsString(s, "type", "stretch");
        int width = GsonHelper.getAsInt(s, "width", texW);
        int height = GsonHelper.getAsInt(s, "height", texH);
        switch (type) {
            case "tile":
                return new Tile(width, height);
            case "nine_slice": {
                JsonElement b = s.get("border");
                int l, t, r, bo;
                if (b != null && b.isJsonObject()) {
                    JsonObject o = b.getAsJsonObject();
                    l = GsonHelper.getAsInt(o, "left");
                    t = GsonHelper.getAsInt(o, "top");
                    r = GsonHelper.getAsInt(o, "right");
                    bo = GsonHelper.getAsInt(o, "bottom");
                } else {
                    l = t = r = bo = b == null ? 0 : b.getAsInt();
                }
                return new NineSlice(width, height, l, t, r, bo);
            }
            default:
                return new Stretch();
        }
    }

    /** Same as 1.20.2+ {@code GuiGraphics.blitSprite(sprite, x, y, w, h)}. */
    public static void blit(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        Optional<Info> opt = info(sprite);
        if (opt.isEmpty()) return;
        Info i = opt.get();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (i.scaling() instanceof NineSlice n) {
            nineSlice(g, i, n, x, y, w, h);
        } else if (i.scaling() instanceof Tile t) {
            float sx = i.texW() / (float) t.width(), sy = i.texH() / (float) t.height();
            tile(g, i, sx, sy, x, y, w, h, 0, 0, t.width(), t.height());
        } else {
            g.blit(i.texture(), x, y, w, h, 0, 0, i.texW(), i.texH(), i.texW(), i.texH());
        }
        RenderSystem.disableBlend();
    }

    private static void nineSlice(GuiGraphics g, Info i, NineSlice n, int x, int y, int w, int h) {
        float sx = i.texW() / (float) n.width(), sy = i.texH() / (float) n.height();
        int l = Math.min(n.left(), w / 2), r = Math.min(n.right(), w / 2);
        int t = Math.min(n.top(), h / 2), b = Math.min(n.bottom(), h / 2);
        int sw = n.width(), sh = n.height();
        int cw = sw - n.left() - n.right(), ch = sh - n.top() - n.bottom();

        // Corners
        part(g, i, sx, sy, x, y, l, t, 0, 0, l, t);
        part(g, i, sx, sy, x + w - r, y, r, t, sw - r, 0, r, t);
        part(g, i, sx, sy, x, y + h - b, l, b, 0, sh - b, l, b);
        part(g, i, sx, sy, x + w - r, y + h - b, r, b, sw - r, sh - b, r, b);
        // Edges (tiled, like 1.20.2+)
        tile(g, i, sx, sy, x + l, y, w - l - r, t, n.left(), 0, cw, t);
        tile(g, i, sx, sy, x + l, y + h - b, w - l - r, b, n.left(), sh - b, cw, b);
        tile(g, i, sx, sy, x, y + t, l, h - t - b, 0, n.top(), l, ch);
        tile(g, i, sx, sy, x + w - r, y + t, r, h - t - b, sw - r, n.top(), r, ch);
        // Center
        tile(g, i, sx, sy, x + l, y + t, w - l - r, h - t - b, n.left(), n.top(), cw, ch);
    }

    /** Draws the source rectangle (logical sprite pixels) into the destination rectangle, stretched. */
    private static void part(GuiGraphics g, Info i, float sx, float sy, int x, int y, int w, int h,
                             int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) return;
        g.blit(i.texture(), x, y, w, h, u * sx, v * sy, Math.round(uw * sx), Math.round(vh * sy), i.texW(), i.texH());
    }

    /** Repeats the source rectangle over the destination rectangle (the last copies are cut). */
    private static void tile(GuiGraphics g, Info i, float sx, float sy, int x, int y, int w, int h,
                             int u, int v, int uw, int vh) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) return;
        for (int dy = 0; dy < h; dy += vh) {
            int ph = Math.min(vh, h - dy);
            for (int dx = 0; dx < w; dx += uw) {
                int pw = Math.min(uw, w - dx);
                part(g, i, sx, sy, x + dx, y + dy, pw, ph, u, v, pw, ph);
            }
        }
    }

    /** Forgets the cached sprites when the resource packs are reloaded. */
    @Mod.EventBusSubscriber(modid = RpEssentials.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) rm -> clear());
        }
    }
}
