package works.repka.ryscore.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран семьи в собственном масштабе {@link UiScale}. Поля {@code width} и {@code height}, холст и координаты мыши
 * здесь в единицах интерфейса, поэтому размер окна не зависит от GUI Scale игры. Вместо {@code render} и событий мыши
 * наследник переопределяет {@link #draw} и методы {@code on...}.
 *
 * <p>База масштаба по умолчанию 640×400. Экран может объявить свою ({@link #setBase}) или правило семьи для тесных
 * экранов ({@link #useCompactBase}): она действует только на этот экран, пока он рисуется, создаётся и получает мышь.
 * В обработчиках клавиш масштаб экрана даёт {@link #pixels()}.
 */
public abstract class ScaledScreen extends Screen {
    private int baseW = UiScale.BASE_W, baseH = UiScale.BASE_H;
    private boolean compactRule;

    protected ScaledScreen(Component title) {
        super(title);
    }

    /** База масштаба этого экрана в единицах: наибольший шаг 25 %, при котором она помещается на экране. */
    protected final void setBase(int w, int h) {
        baseW = Math.max(1, w);
        baseH = Math.max(1, h);
        compactRule = false;
    }

    /** Правило семьи: база 480×270, когда 640×400 не помещается при 100 % ({@link UiScale#compactNeeded()}), иначе 640×400. */
    protected final void useCompactBase() {
        compactRule = true;
    }

    /** Работает ли сейчас сжатая база: экран переключает раскладку по этому признаку. */
    public final boolean compact() {
        return compactRule ? UiScale.compactNeeded() : baseW < UiScale.BASE_W || baseH < UiScale.BASE_H;
    }

    public final int baseWidth() {
        return compactRule && UiScale.compactNeeded() ? UiScale.COMPACT_W : compactRule ? UiScale.BASE_W : baseW;
    }

    public final int baseHeight() {
        return compactRule && UiScale.compactNeeded() ? UiScale.COMPACT_H : compactRule ? UiScale.BASE_H : baseH;
    }

    /** Пикселей экрана в единице этого экрана. */
    public final double pixels() {
        return UiScale.fit(baseWidth(), baseHeight());
    }

    private boolean ownBase() {
        return baseWidth() != UiScale.BASE_W || baseHeight() != UiScale.BASE_H;
    }

    /** Код экрана в его масштабе: при базе 640×400 вызывается как есть. */
    protected final void inScale(Runnable body) {
        if (ownBase()) UiScale.within(pixels(), body);
        else body.run();
    }

    private boolean inScale(java.util.function.BooleanSupplier body) {
        if (!ownBase()) return body.getAsBoolean();
        return UiScale.within(pixels(), () -> body.getAsBoolean());
    }

    private void syncSize() {
        width = UiScale.width();
        height = UiScale.height();
    }

    @Override
    protected void init() {
        inScale((Runnable) this::syncSize);
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        inScale(() -> super.resize(minecraft, width, height));
    }

    protected abstract void draw(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        inScale(() -> {
            syncSize();
            int mx = (int) Math.floor(UiScale.mouseX()), my = (int) Math.floor(UiScale.mouseY());
            UiScale.render(g, scaled -> draw(scaled, mx, my, partialTick));
        });
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
        return inScale(() -> onClick(UiScale.fromGui(x), UiScale.fromGui(y), button));
    }

    @Override
    public final boolean mouseReleased(double x, double y, int button) {
        return inScale(() -> onRelease(UiScale.fromGui(x), UiScale.fromGui(y), button));
    }

    @Override
    public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return inScale(() -> onDrag(UiScale.fromGui(x), UiScale.fromGui(y), button, UiScale.fromGui(dx), UiScale.fromGui(dy)));
    }

    @Override
    public final boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return inScale(() -> onScroll(UiScale.fromGui(x), UiScale.fromGui(y), scrollX, scrollY));
    }

    @Override
    public final void mouseMoved(double x, double y) {
        inScale(() -> super.mouseMoved(UiScale.fromGui(x), UiScale.fromGui(y)));
    }
}
