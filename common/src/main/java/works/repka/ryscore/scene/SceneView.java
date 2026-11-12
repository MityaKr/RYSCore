package works.repka.ryscore.scene;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import works.repka.ryscore.RYSCore;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Draw;

/**
 * Вид блоковой структуры внутри прямоугольника окна. Один экземпляр на виджет, вызывать на потоке отрисовки,
 * по окончании вызвать {@link #close}. Рисует только блоки: плашку под ними ставит виджет из токенов темы.
 */
public final class SceneView implements AutoCloseable {
    private static final float START_PITCH = 30f, START_YAW = -135f;
    private static final float DEGREES_PER_UNIT = 1f, ZOOM_STEP = 1.12f, ZOOM_MIN = 0.5f, ZOOM_MAX = 8f;
    /** Прозрачные грани пересортировываются, когда взгляд повернулся на столько градусов. */
    private static final float SORT_STEP = 8f;
    private static final float MARGIN = 1.06f;

    private static final class Slot {
        @Nullable
        CompletableFuture<SceneMesh.Compiled> pending;
        /** Для какой сцены строится {@link #pending}: сцена могла смениться, пока шла сборка. */
        @Nullable
        BlockScene pendingFor;
        @Nullable
        SceneMesh mesh;
        /** Сетка от прежней сцены: рисуется, пока новая не готова, чтобы смена шла без мигания. */
        boolean stale;
        boolean failed;
        float sortedYaw, sortedPitch;
    }

    private BlockScene scene;
    private final Map<Integer, Slot> slots = new HashMap<>();
    private float pitch = START_PITCH, yaw = START_YAW, zoom = 1f;
    @Nullable
    private Integer layer;
    private int generation = SceneHooks.generation();
    private boolean closed;

    public SceneView(BlockScene scene) {
        this.scene = scene;
    }

    /**
     * Сменить сцену того же размера (например, следующий вариант блоков структуры). Камера и слой сохраняются,
     * старая картинка показывается, пока новая строится в фоне.
     */
    public void setScene(BlockScene next) {
        if (next == scene) return;
        scene = next;
        for (Slot slot : slots.values()) {
            slot.stale = true;
            slot.failed = false;
        }
    }

    /** Поворот мышью; единицы те же, что у координат окна. */
    public void drag(double dx, double dy) {
        yaw += (float) dx * DEGREES_PER_UNIT;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * DEGREES_PER_UNIT));
    }

    public void scroll(double delta) {
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom * (float) Math.pow(ZOOM_STEP, delta)));
    }

    /** Показать один слой, считая от низа сцены, или всю сцену при {@code null}. */
    public void setLayer(@Nullable Integer y) {
        layer = y == null ? null : Integer.valueOf(Math.max(0, Math.min(Math.max(0, scene.size().getY() - 1), y)));
    }

    @Nullable
    public Integer layer() {
        return layer;
    }

    public void resetView() {
        pitch = START_PITCH;
        yaw = START_YAW;
        zoom = 1f;
    }

    /** Координаты в единицах текущего {@code g}: подходит и обычный экран, и {@code ScaledScreen}. */
    public void render(GuiGraphics g, int x, int y, int w, int h) {
        if (closed || w <= 0 || h <= 0) return;
        RenderSystem.assertOnRenderThread();
        if (generation != SceneHooks.generation()) {
            freeSlots();
            generation = SceneHooks.generation();
        }
        Slot slot = slot();
        poll(slot);
        if (slot.failed) return;
        if (slot.mesh == null) {
            drawWaiting(g, x, y, w, h);
            return;
        }
        g.flush();
        drawMesh(g, slot, x, y, w, h);
    }

    private Slot slot() {
        Integer key = layer;
        Slot slot = slots.computeIfAbsent(key, k -> new Slot());
        if ((slot.mesh == null || slot.stale) && slot.pending == null && !slot.failed) {
            VertexSorting sorting = sorting(yaw, pitch);
            BlockScene building = scene;
            slot.sortedYaw = yaw;
            slot.sortedPitch = pitch;
            slot.pendingFor = building;
            slot.stale = false;
            slot.pending = CompletableFuture.supplyAsync(() -> SceneMesh.compile(building, key, sorting), Util.backgroundExecutor());
        }
        return slot;
    }

    private void poll(Slot slot) {
        if (slot.pending == null || !slot.pending.isDone()) return;
        CompletableFuture<SceneMesh.Compiled> done = slot.pending;
        boolean current = slot.pendingFor == scene;
        slot.pending = null;
        slot.pendingFor = null;
        try {
            SceneMesh.Compiled compiled = done.join();
            if (!current) {
                // сцена сменилась, пока шла сборка: эта сетка уже не нужна, соберётся следующая
                compiled.release();
                slot.stale = true;
                return;
            }
            if (slot.mesh != null) slot.mesh.close();
            slot.mesh = new SceneMesh(compiled);
        } catch (RuntimeException e) {
            slot.failed = true;
            RYSCore.LOG.warn("Сцена не построена: {}", e.toString());
        }
    }

    private void drawMesh(GuiGraphics g, Slot slot, int x, int y, int w, int h) {
        Window window = Minecraft.getInstance().getWindow();
        double gui = window.getGuiScale();
        Matrix4f pose = g.pose().last().pose();
        Vector3f a = pose.transformPosition(new Vector3f(x, y, 0)), b = pose.transformPosition(new Vector3f(x + w, y + h, 0));
        int left = (int) Math.round(a.x * gui), right = (int) Math.round(b.x * gui);
        int top = (int) Math.round(a.y * gui), bottom = (int) Math.round(b.y * gui);
        int deviceW = right - left, deviceH = bottom - top, deviceY = window.getHeight() - bottom;
        if (deviceW <= 0 || deviceH <= 0) return;

        int[] box = new int[4];
        boolean scissored = works.repka.ryscore.ui.Scissor.current(box);
        int clipX = scissored ? Math.max(left, box[0]) : left, clipY = scissored ? Math.max(deviceY, box[1]) : deviceY;
        int clipR = scissored ? Math.min(left + deviceW, box[0] + box[2]) : left + deviceW;
        int clipT = scissored ? Math.min(deviceY + deviceH, box[1] + box[3]) : deviceY + deviceH;
        if (clipR <= clipX || clipT <= clipY) return;

        if (Math.abs(yaw - slot.sortedYaw) >= SORT_STEP || Math.abs(pitch - slot.sortedPitch) >= SORT_STEP) {
            slot.mesh.resort(sorting(yaw, pitch));
            slot.sortedYaw = yaw;
            slot.sortedPitch = pitch;
        }

        Vec3i size = scene.size();
        float radius = Math.max(1f, (float) Math.sqrt(size.getX() * size.getX() + size.getY() * size.getY() + size.getZ() * size.getZ()) / 2f);
        float scale = Math.min(deviceW, deviceH) / (2f * radius * MARGIN) * zoom;
        float depth = radius * scale * 2f + 16f;
        Matrix4f projection = new Matrix4f().setOrtho(-deviceW / 2f, deviceW / 2f, -deviceH / 2f, deviceH / 2f, -depth, depth);
        Matrix4f modelView = new Matrix4f().scale(scale)
            .rotateX((float) Math.toRadians(pitch))
            .rotateY((float) Math.toRadians(yaw))
            .translate(-size.getX() / 2f, -size.getY() / 2f, -size.getZ() / 2f);

        RenderSystem.enableScissor(clipX, clipY, clipR - clipX, clipT - clipY);
        RenderSystem.viewport(left, deviceY, deviceW, deviceH);
        RenderSystem.depthMask(true);
        // глубина чистится только внутри обрезки: чужой кадр и окно вокруг не трогаем
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        FogRenderer.setupNoFog();
        slot.mesh.draw(modelView, projection);
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        if (scissored) RenderSystem.enableScissor(box[0], box[1], box[2], box[3]);
        else RenderSystem.disableScissor();
    }

    private VertexSorting sorting(float atYaw, float atPitch) {
        Vec3i size = scene.size();
        Vector3f eye = new Matrix4f()
            .rotateY((float) Math.toRadians(-atYaw))
            .rotateX((float) Math.toRadians(-atPitch))
            .transformPosition(new Vector3f(0, 0, 1000f))
            .add(size.getX() / 2f, size.getY() / 2f, size.getZ() / 2f);
        return VertexSorting.byDistance(eye);
    }

    private static void drawWaiting(GuiGraphics g, int x, int y, int w, int h) {
        Theme t = ThemeManager.get();
        int bw = Math.min(64, w - 8);
        if (bw <= 0) return;
        int bx = x + (w - bw) / 2, by = y + h / 2 - 1;
        Draw.round(g, bx, by, bw, 2, 1f, t.c("slot.bg"));
        float phase = (System.currentTimeMillis() % 1200L) / 1200f;
        int segment = bw / 4;
        Draw.round(g, bx + Math.round((bw - segment) * (phase < 0.5f ? phase * 2 : 2 - phase * 2)), by, segment, 2, 1f, t.c("accent"));
    }

    private void freeSlots() {
        for (Slot slot : slots.values()) {
            if (slot.mesh != null) slot.mesh.close();
            if (slot.pending != null) {
                slot.pending.whenComplete((compiled, error) -> {
                    if (compiled != null) compiled.release();
                });
            }
        }
        slots.clear();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        freeSlots();
    }
}
