package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Много одинаковых ячеек за один вызов отрисовки. Ячейка один раз рисуется в текстуру в пикселях экрана,
 * дальше на каждую тратится один прямоугольник: длинный список остаётся таким же быстрым, как без подложек.
 */
public final class Tiles {
    /** Внешний вид ячейки. light и dark заданы только у ванильного бевеля. */
    public record Style(float radius, int fill, int border, int light, int dark, boolean beveled) {}

    private record Key(int pixels, Style style) {}

    private static final int MAX_CACHED = 32;
    private static final Map<Key, ResourceLocation> CACHE = new HashMap<>();
    private static int counter;

    private Tiles() {}

    /** @param xy координаты левых верхних углов подряд: x0, y0, x1, y1… в единицах GUI */
    public static void draw(GuiGraphics g, float size, Style style, int[] xy, int count) {
        if (count <= 0) return;
        int pixels = Math.round(size * (float) Draw.scale());
        ResourceLocation texture = texture(pixels, style);
        g.flush();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < count; i++) {
            float x = xy[i * 2], y = xy[i * 2 + 1];
            buffer.addVertex(matrix, x, y, 0).setUv(0, 0);
            buffer.addVertex(matrix, x, y + size, 0).setUv(0, 1);
            buffer.addVertex(matrix, x + size, y + size, 0).setUv(1, 1);
            buffer.addVertex(matrix, x + size, y, 0).setUv(1, 0);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static ResourceLocation texture(int pixels, Style style) {
        Key key = new Key(pixels, style);
        ResourceLocation known = CACHE.get(key);
        if (known != null) return known;
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        if (CACHE.size() >= MAX_CACHED) {
            // размер окна и тема меняются редко: проще забыть всё и нарисовать заново
            CACHE.values().forEach(textures::release);
            CACHE.clear();
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ryscore", "tile/" + counter++);
        textures.register(id, new DynamicTexture(paint(pixels, style)));
        CACHE.put(key, id);
        return id;
    }

    private static NativeImage paint(int n, Style s) {
        NativeImage image = new NativeImage(n, n, true);
        int t = Math.max(1, (int) Math.round(Draw.scale() * (s.beveled ? 1 : 0.5)));
        float r = Math.min(s.radius * (float) Draw.scale(), n / 2f);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                image.setPixelRGBA(x, y, abgr(s.beveled ? bevelPixel(x, y, n, t, s) : roundPixel(x, y, n, r, t, s)));
            }
        }
        return image;
    }

    private static int bevelPixel(int x, int y, int n, int t, Style s) {
        if (x < t && y < n - t || y < t && x < n - t) return s.dark;
        if (x >= n - t && y >= t || y >= n - t && x >= t) return s.light;
        return s.fill;
    }

    /** Расстояние до края скруглённого квадрата даёт сглаживание и рамку одной формулой. */
    private static int roundPixel(int x, int y, int n, float r, int t, Style s) {
        float half = n / 2f;
        float qx = Math.abs(x + 0.5f - half) - (half - r);
        float qy = Math.abs(y + 0.5f - half) - (half - r);
        float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
        float d = Math.min(Math.max(qx, qy), 0) + (float) Math.sqrt(ox * ox + oy * oy) - r;
        float outer = clamp(0.5f - d), inner = clamp(0.5f - (d + t));
        return Draw.alpha(Draw.mix(s.border, s.fill, inner), outer);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
    }
}
