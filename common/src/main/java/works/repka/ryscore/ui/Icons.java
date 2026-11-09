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
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Значки из макетов: фигура задаётся формулой и рисуется в пикселях экрана, поэтому гладкая на любом масштабе. */
public final class Icons {
    public enum Shape { STAR, CROSS, MAGNIFIER, GEAR, ARROW, CHECK }

    private record Key(Shape shape, int pixels) {}

    private static final Map<Key, ResourceLocation> CACHE = new HashMap<>();
    private static final int SUPERSAMPLE = 4;

    private Icons() {}

    public static void draw(GuiGraphics g, Shape shape, float x, float y, float size, int argb) {
        int pixels = Math.max(1, Math.round(size * (float) Draw.scale()));
        ResourceLocation texture = CACHE.computeIfAbsent(new Key(shape, pixels), Icons::paint);
        g.flush();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(m, x, y, 0).setUv(0, 0).setColor(argb);
        b.addVertex(m, x, y + size, 0).setUv(0, 1).setColor(argb);
        b.addVertex(m, x + size, y + size, 0).setUv(1, 1).setColor(argb);
        b.addVertex(m, x + size, y, 0).setUv(1, 0).setColor(argb);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /** Значок, повёрнутый вокруг центра на {@code degrees}: стрелка направления. */
    public static void drawRotated(GuiGraphics g, Shape shape, float x, float y, float size, float degrees, int argb) {
        g.pose().pushPose();
        g.pose().translate(x + size / 2f, y + size / 2f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        draw(g, shape, -size / 2f, -size / 2f, size, argb);
        g.pose().popPose();
    }

    private static ResourceLocation paint(Key key) {
        int n = key.pixels;
        NativeImage image = new NativeImage(n, n, true);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int hits = 0;
                for (int sy = 0; sy < SUPERSAMPLE; sy++) {
                    for (int sx = 0; sx < SUPERSAMPLE; sx++) {
                        double u = (x + (sx + 0.5) / SUPERSAMPLE) / n, v = (y + (sy + 0.5) / SUPERSAMPLE) / n;
                        if (inside(key.shape, u, v)) hits++;
                    }
                }
                image.setPixelRGBA(x, y, (hits * 255 / (SUPERSAMPLE * SUPERSAMPLE)) << 24 | 0xFFFFFF);
            }
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ryscore", "icon/" + key.shape.name().toLowerCase(java.util.Locale.ROOT) + "_" + n);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    private static boolean inside(Shape shape, double u, double v) {
        return switch (shape) {
            case STAR -> star(u, v);
            case CHECK -> segment(u, v, 0.18, 0.54, 0.42, 0.78) < 0.08 || segment(u, v, 0.42, 0.78, 0.84, 0.26) < 0.08;
            case ARROW -> segment(u, v, 0.14, 0.5, 0.86, 0.5) < 0.04 || segment(u, v, 0.62, 0.27, 0.86, 0.5) < 0.04 || segment(u, v, 0.62, 0.73, 0.86, 0.5) < 0.04;
            case CROSS -> segment(u, v, 0.2, 0.2, 0.8, 0.8) < 0.055 || segment(u, v, 0.8, 0.2, 0.2, 0.8) < 0.055;
            case MAGNIFIER -> {
                double d = Math.hypot(u - 0.42, v - 0.42);
                yield (d < 0.31 && d > 0.22) || segment(u, v, 0.64, 0.64, 0.88, 0.88) < 0.06;
            }
            case GEAR -> {
                double dx = u - 0.5, dy = v - 0.5, d = Math.hypot(dx, dy);
                double angle = Math.atan2(dy, dx) + Math.PI / 8;
                boolean tooth = ((angle + 2 * Math.PI) % (Math.PI / 4)) < Math.PI / 8;
                yield d > 0.15 && d < (tooth ? 0.43 : 0.32);
            }
        };
    }

    private static double segment(double u, double v, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double t = Math.max(0, Math.min(1, ((u - x0) * dx + (v - y0) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(u - x0 - t * dx, v - y0 - t * dy);
    }

    private static boolean star(double u, double v) {
        boolean in = false;
        double px = 0, py = 0, firstX = 0, firstY = 0;
        for (int i = 0; i <= 10; i++) {
            double r = i % 2 == 0 ? 0.5 : 0.21;
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double x = 0.5 + r * Math.cos(a), y = 0.53 + r * Math.sin(a);
            if (i == 0) {
                firstX = x;
                firstY = y;
            } else if (((y > v) != (py > v)) && (u < (px - x) * (v - y) / (py - y) + x)) {
                in = !in;
            }
            px = x;
            py = y;
        }
        return in;
    }
}
