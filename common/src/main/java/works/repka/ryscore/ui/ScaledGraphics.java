package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Холст окна семьи: матрица уже приведена к единицам интерфейса, размеры экрана отвечают в них же.
 * {@link #enableScissor} принимает единицы GUI игры, как обычно: виджеты JEI считают область через матрицу.
 * Наш код задаёт область в единицах интерфейса через {@link Draw#scissor}.
 */
public final class ScaledGraphics extends GuiGraphics {
    private final double pixels;
    private final Deque<int[]> scissors = new ArrayDeque<>();

    ScaledGraphics(GuiGraphics parent, double pixels) {
        super(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
        this.pixels = pixels;
        pose().last().pose().set(parent.pose().last().pose());
        pose().last().normal().set(parent.pose().last().normal());
        float k = (float) (pixels / Minecraft.getInstance().getWindow().getGuiScale());
        pose().scale(k, k, 1f);
    }

    @Override
    public int guiWidth() {
        return UiScale.width();
    }

    @Override
    public int guiHeight() {
        return UiScale.height();
    }

    @Override
    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        push(minX * scale, minY * scale, maxX * scale, maxY * scale);
    }

    /** Область в единицах интерфейса. */
    void scissorUi(float minX, float minY, float maxX, float maxY) {
        push(minX * pixels, minY * pixels, maxX * pixels, maxY * pixels);
    }

    private void push(double minX, double minY, double maxX, double maxY) {
        int[] rect = {(int) Math.round(minX), (int) Math.round(minY), (int) Math.round(maxX), (int) Math.round(maxY)};
        int[] outer = scissors.peek();
        if (outer != null) {
            rect[0] = Math.max(rect[0], outer[0]);
            rect[1] = Math.max(rect[1], outer[1]);
            rect[2] = Math.min(rect[2], outer[2]);
            rect[3] = Math.min(rect[3], outer[3]);
        }
        scissors.push(rect);
        apply(rect);
    }

    @Override
    public void disableScissor() {
        if (scissors.isEmpty()) return;
        scissors.pop();
        int[] outer = scissors.peek();
        if (outer != null) {
            apply(outer);
        } else {
            flush();
            RenderSystem.disableScissor();
        }
    }

    private void apply(int[] rect) {
        flush();
        Window window = Minecraft.getInstance().getWindow();
        RenderSystem.enableScissor(rect[0], window.getHeight() - rect[3], Math.max(0, rect[2] - rect[0]), Math.max(0, rect[3] - rect[1]));
    }
}
