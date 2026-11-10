package works.repka.ryscore.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран семьи в собственном масштабе {@link UiScale}. Поля {@code width} и {@code height}, холст и координаты мыши
 * здесь в единицах интерфейса, поэтому размер окна не зависит от GUI Scale игры. Вместо {@code render} и событий мыши
 * наследник переопределяет {@link #draw} и методы {@code on...}.
 */
public abstract class ScaledScreen extends Screen {
    protected ScaledScreen(Component title) {
        super(title);
    }

    private void syncSize() {
        width = UiScale.width();
        height = UiScale.height();
    }

    @Override
    protected void init() {
        syncSize();
    }

    protected abstract void draw(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        syncSize();
        int mx = (int) Math.floor(UiScale.mouseX()), my = (int) Math.floor(UiScale.mouseY());
        UiScale.render(g, scaled -> draw(scaled, mx, my, partialTick));
    }

    protected boolean onClick(double x, double y, int button) {
        return super.mouseClicked(x, y, button);
    }

    protected boolean onRelease(double x, double y, int button) {
        return super.mouseReleased(x, y, button);
    }

    protected boolean onDrag(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x, y, button, dx, dy);
    }

    protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public final boolean mouseClicked(double x, double y, int button) {
        return onClick(UiScale.fromGui(x), UiScale.fromGui(y), button);
    }

    @Override
    public final boolean mouseReleased(double x, double y, int button) {
        return onRelease(UiScale.fromGui(x), UiScale.fromGui(y), button);
    }

    @Override
    public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return onDrag(UiScale.fromGui(x), UiScale.fromGui(y), button, UiScale.fromGui(dx), UiScale.fromGui(dy));
    }

    @Override
    public final boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return onScroll(UiScale.fromGui(x), UiScale.fromGui(y), scrollX, scrollY);
    }

    @Override
    public final void mouseMoved(double x, double y) {
        super.mouseMoved(UiScale.fromGui(x), UiScale.fromGui(y));
    }
}
