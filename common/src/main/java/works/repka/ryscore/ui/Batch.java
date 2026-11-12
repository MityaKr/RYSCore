package works.repka.ryscore.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Пакетная отрисовка кора. Фигуры, текст, значки, ячейки и заливки копятся квадами со страницы атласа
 * и уходят одним вызовом, пока не сменится страница или не начнёт рисовать кто-то ещё.
 *
 * <p>Порядок наложения сохраняется: перехват кора сбрасывает пачку перед любой отрисовкой {@link GuiGraphics}
 * (заливки, текст игры, blit, предметы, ножницы, {@code flush}, выдача {@code bufferSource}). Код, который рисует
 * напрямую через {@code RenderSystem} или {@code Tesselator}, должен сначала вызвать {@link #flush()}.
 * Пока перехват не сработал или шейдер не загружен, каждый квад рисуется сразу, как раньше.
 */
public final class Batch {
    private static final int MAX_QUADS = 8192;

    private static final float[] XYZ = new float[MAX_QUADS * 12];
    private static final float[] UV = new float[MAX_QUADS * 8];
    private static final int[] COLOR = new int[MAX_QUADS * 4];
    private static final Vector3f TMP = new Vector3f();
    private static int quads;
    private static int page = -1;
    private static boolean hooked;
    @Nullable
    private static ShaderInstance shader;
    private static int drawCalls;
    /** Для замеров: рисовать каждый квад сразу, как до пачки. */
    public static boolean immediate;

    private Batch() {}

    /** Шейдер GUI с цветом в вершинах, отбрасывает только полностью прозрачные пиксели (сглаживание и тени живы). */
    public static void setShader(@Nullable ShaderInstance instance) {
        shader = instance;
    }

    /** Вызывает перехват кора перед ванильной отрисовкой: накопленное рисуется первым. */
    public static void beforeVanilla() {
        hooked = true;
        flush();
    }

    /** Пачка работает: перехват на месте и шейдер загружен. Иначе всё рисуется сразу. */
    static boolean active() {
        return hooked && shader != null && !immediate;
    }

    /** Сколько вызовов отрисовки сделал кор с прошлого опроса: для замеров. */
    public static int takeDrawCalls() {
        int n = drawCalls;
        drawCalls = 0;
        return n;
    }

    /**
     * Квад со страницы атласа. Координаты в единицах текущей матрицы, UV в долях страницы.
     * Цвет умножается на цвет шейдера в момент вызова, как раньше делал множитель.
     */
    static void quad(GuiGraphics g, Matrix4f m, float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1, int argb, int atlasPage) {
        boolean batching = active();
        if (!batching) g.flush();
        else if (g instanceof BatchTarget target && target.ryscore$vanillaPending()) g.flush();
        if (quads > 0 && (atlasPage != page || quads == MAX_QUADS)) flush();
        page = atlasPage;
        int c = tint(argb);
        int i = quads * 4;
        put(i, m, x0, y0, u0, v0, c);
        put(i + 1, m, x0, y1, u0, v1, c);
        put(i + 2, m, x1, y1, u1, v1, c);
        put(i + 3, m, x1, y0, u1, v0, c);
        quads++;
        if (!batching) flush();
    }

    private static void put(int vertex, Matrix4f m, float x, float y, float u, float v, int color) {
        m.transformPosition(x, y, 0f, TMP);
        XYZ[vertex * 3] = TMP.x;
        XYZ[vertex * 3 + 1] = TMP.y;
        XYZ[vertex * 3 + 2] = TMP.z;
        UV[vertex * 2] = u;
        UV[vertex * 2 + 1] = v;
        COLOR[vertex] = color;
    }

    private static int tint(int argb) {
        float[] s = RenderSystem.getShaderColor();
        if (s[0] == 1f && s[1] == 1f && s[2] == 1f && s[3] == 1f) return argb;
        int a = Math.round((argb >>> 24) * s[3]), r = Math.round((argb >> 16 & 255) * s[0]);
        int gg = Math.round((argb >> 8 & 255) * s[1]), b = Math.round((argb & 255) * s[2]);
        return Math.min(255, a) << 24 | Math.min(255, r) << 16 | Math.min(255, gg) << 8 | Math.min(255, b);
    }

    /** Нарисовать накопленное. Нужен перед своей отрисовкой мимо {@link GuiGraphics}. */
    public static void flush() {
        if (quads == 0) return;
        int count = quads;
        quads = 0;
        Atlas.upload();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < count * 4; i++) {
            buffer.addVertex(XYZ[i * 3], XYZ[i * 3 + 1], XYZ[i * 3 + 2]).setUv(UV[i * 2], UV[i * 2 + 1]).setColor(COLOR[i]);
        }
        // те же смешивание и глубина, что у обычной заливки GUI
        RenderType.gui().setupRenderState();
        ShaderInstance own = shader;
        RenderSystem.setShader(own != null ? () -> own : GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, Atlas.id(page));
        float[] saved = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.setShaderColor(saved[0], saved[1], saved[2], saved[3]);
        RenderType.gui().clearRenderState();
        drawCalls++;
    }
}
