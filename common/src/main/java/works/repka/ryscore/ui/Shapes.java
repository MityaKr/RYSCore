package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Скруглённые фигуры из заготовок: фигура один раз растеризуется в маленькую белую текстуру
 * и рисуется одним вызовом из девяти кусков, окрашенных цветом. Пиксели те же, что при построчной заливке.
 */
final class Shapes {
    private static final int LIMIT = 192;
    private static final Map<ShapeRaster.Plan, ResourceLocation> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int created;

    private Shapes() {}

    /** Рисует заготовку в прямоугольнике (x, y, w, h) пикселей экрана; матрица уже переведена в пиксели. */
    static void draw(GuiGraphics g, ShapeRaster.Plan plan, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        ResourceLocation texture = texture(plan);
        int[] sx = ShapeRaster.slices(w, plan.width, plan.stretchX);
        int[] sy = ShapeRaster.slices(h, plan.height, plan.stretchY);
        g.flush();
        // те же настройки смешивания и глубины, что у обычной заливки GUI
        RenderType.gui().setupRenderState();
        // position_tex_color отбрасывает пиксели прозрачнее 0,1 и съел бы края теней и сглаживание:
        // position_tex отбрасывает только полностью прозрачные, цвет идёт через множитель, как у заливок
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        float[] saved = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(saved[0] * (argb >> 16 & 255) / 255f, saved[1] * (argb >> 8 & 255) / 255f,
            saved[2] * (argb & 255) / 255f, saved[3] * (argb >>> 24) / 255f);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float cw = plan.width, ch = plan.height;
        boolean any = false;
        for (int j = 0; j < sy.length; j += 4) {
            if (sy[j + 1] <= 0) continue;
            float y0 = y + sy[j], y1 = y0 + sy[j + 1], v0 = sy[j + 2] / ch, v1 = (sy[j + 2] + sy[j + 3]) / ch;
            for (int i = 0; i < sx.length; i += 4) {
                if (sx[i + 1] <= 0) continue;
                float x0 = x + sx[i], x1 = x0 + sx[i + 1], u0 = sx[i + 2] / cw, u1 = (sx[i + 2] + sx[i + 3]) / cw;
                b.addVertex(m, x0, y0, 0).setUv(u0, v0);
                b.addVertex(m, x0, y1, 0).setUv(u0, v1);
                b.addVertex(m, x1, y1, 0).setUv(u1, v1);
                b.addVertex(m, x1, y0, 0).setUv(u1, v0);
                any = true;
            }
        }
        if (any) BufferUploader.drawWithShader(b.buildOrThrow());
        else b.build();
        RenderSystem.setShaderColor(saved[0], saved[1], saved[2], saved[3]);
        RenderType.gui().clearRenderState();
    }

    private static ResourceLocation texture(ShapeRaster.Plan plan) {
        ResourceLocation cached = CACHE.get(plan);
        if (cached != null) return cached;
        ShapeRaster.Canvas canvas = plan.paint();
        NativeImage image = new NativeImage(canvas.width, canvas.height, true);
        for (int y = 0; y < canvas.height; y++) {
            for (int x = 0; x < canvas.width; x++) {
                int a = Math.round(canvas.alpha[y * canvas.width + x] * 255f);
                image.setPixelRGBA(x, y, a << 24 | 0xFFFFFF);
            }
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ryscore", "shape/" + created++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        CACHE.put(plan, id);
        if (CACHE.size() > LIMIT) {
            Iterator<ResourceLocation> eldest = CACHE.values().iterator();
            Minecraft.getInstance().getTextureManager().release(eldest.next());
            eldest.remove();
        }
        return id;
    }
}
