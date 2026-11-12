package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.Window;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Свой масштаб окон семьи, независимый от GUI Scale игры. Единица интерфейса при 100 % равна двум пикселям экрана,
 * как в макетах. Авто подбирает наибольший масштаб с шагом 25 %, при котором базовая раскладка 640×400 помещается
 * на экране; выбранный вручную процент тоже не выходит за этот предел, поэтому окно никогда не обрезается.
 */
public final class UiScale {
    public static final int AUTO = 0;
    private static final int[] OPTIONS = {AUTO, 75, 100, 125, 150};
    private static final double BASE_PIXELS = 2.0;
    private static final double MIN_FACTOR = 0.5;
    private static final int BASE_WIDTH = 640, BASE_HEIGHT = 400;

    private UiScale() {}

    /** Значение настройки: {@link #AUTO} или проценты. */
    public static int setting() {
        return UiConfig.get().scale;
    }

    public static void set(int value) {
        UiConfig.get().scale = value;
        UiConfig.save();
    }

    /** Следующее значение настройки по кругу. */
    public static void cycle() {
        int current = setting();
        for (int i = 0; i < OPTIONS.length; i++) {
            if (OPTIONS[i] == current) {
                set(OPTIONS[(i + 1) % OPTIONS.length]);
                return;
            }
        }
        set(AUTO);
    }

    /** Предыдущий размер по кругу: правая кнопка в настройках листает назад. */
    public static void cycleBack() {
        int current = setting();
        for (int i = 0; i < OPTIONS.length; i++) {
            if (OPTIONS[i] == current) {
                set(OPTIONS[(i - 1 + OPTIONS.length) % OPTIONS.length]);
                return;
            }
        }
        set(AUTO);
    }

    private static double fit(Window window) {
        double most = Math.min((double) window.getWidth() / BASE_WIDTH, (double) window.getHeight() / BASE_HEIGHT) / BASE_PIXELS;
        return Math.max(MIN_FACTOR, Math.floor(most * 4 + 1e-6) / 4);
    }

    /** Пикселей экрана в одной единице интерфейса. */
    public static double pixels() {
        Window window = Minecraft.getInstance().getWindow();
        double limit = fit(window);
        int value = setting();
        return BASE_PIXELS * (value == AUTO ? limit : Math.min(limit, value / 100.0));
    }

    /** Во сколько раз единица интерфейса больше единицы GUI игры. */
    public static double factor() {
        return pixels() / Minecraft.getInstance().getWindow().getGuiScale();
    }

    /** Ширина экрана в единицах интерфейса. */
    public static int width() {
        return (int) Math.floor(Minecraft.getInstance().getWindow().getWidth() / pixels());
    }

    public static int height() {
        return (int) Math.floor(Minecraft.getInstance().getWindow().getHeight() / pixels());
    }

    /** Курсор в единицах интерфейса. */
    public static double mouseX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos() * mc.getWindow().getWidth() / mc.getWindow().getScreenWidth() / pixels();
    }

    public static double mouseY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos() * mc.getWindow().getHeight() / mc.getWindow().getScreenHeight() / pixels();
    }

    /** Перевод из единиц GUI игры в единицы интерфейса: для координат чужих экранов и событий мыши. */
    public static double fromGui(double value) {
        return value / factor();
    }

    /** Обратный перевод: для запросов к чужому коду, который считает в единицах GUI игры. */
    public static double toGui(double value) {
        return value * factor();
    }

    /** Рисует окно или элемент HUD в единицах интерфейса. */
    public static void render(GuiGraphics parent, Consumer<GuiGraphics> body) {
        double pixels = pixels();
        parent.flush();
        ScaledGraphics scaled = new ScaledGraphics(parent, pixels);
        double saved = Draw.pushScale(pixels);
        try {
            body.accept(scaled);
            scaled.flush();
        } finally {
            Draw.popScale(saved);
        }
    }
}
