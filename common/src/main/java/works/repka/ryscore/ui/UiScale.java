package works.repka.ryscore.ui;

import com.mojang.blaze3d.platform.Window;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Свой масштаб окон семьи, независимый от GUI Scale игры. Единица интерфейса при 100 % равна двум пикселям экрана,
 * как в макетах. Авто подбирает наибольший масштаб с шагом 25 %, при котором базовая раскладка 640×400 помещается
 * на экране; выбранный вручную процент тоже не выходит за этот предел, поэтому окно никогда не обрезается.
 *
 * <p>Экран может объявить свою базу ({@link #fit(int, int)}, {@link ScaledScreen#setBase}): она действует только
 * пока этот экран рисуется или обрабатывает мышь. HUD и чужие окна считают масштаб от базы 640×400.
 * Правило семьи для тесных экранов: если 640×400 не помещается при 100 %, окно объявляет сжатую базу 480×270
 * ({@link #compactNeeded()}, {@link ScaledScreen#useCompactBase}).
 */
public final class UiScale {
    public static final int AUTO = 0;
    private static final int[] OPTIONS = {AUTO, 75, 100, 125, 150};
    private static final double BASE_PIXELS = 2.0;
    private static final double MIN_FACTOR = 0.5;
    private static final int BASE_WIDTH = 640, BASE_HEIGHT = 400;
    /** Обычная база окон семьи. */
    public static final int BASE_W = BASE_WIDTH, BASE_H = BASE_HEIGHT;
    /** Сжатая база для тесных экранов: правило семьи (окно уже 1280 или ниже 800 пикселей). */
    public static final int COMPACT_W = 480, COMPACT_H = 270;
    /** Пикселей в единице у экрана, который сейчас рисуется или обрабатывает мышь со своей базой; 0 — база 640×400. */
    private static double scoped;

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

    private static double limit(Window window, int baseW, int baseH) {
        double most = Math.min((double) window.getWidth() / baseW, (double) window.getHeight() / baseH) / BASE_PIXELS;
        return Math.max(MIN_FACTOR, Math.floor(most * 4 + 1e-6) / 4);
    }

    /**
     * Пикселей экрана в единице для раскладки с базой {@code baseW}×{@code baseH} единиц: наибольший шаг 25 %,
     * при котором база помещается на экране (не ниже 50 %). Ручной размер ограничен тем же пределом этой базы.
     * Сам вызов ничего не меняет: базу экрана объявляет {@link ScaledScreen#setBase}.
     */
    public static double fit(int baseW, int baseH) {
        Window window = Minecraft.getInstance().getWindow();
        double limit = limit(window, Math.max(1, baseW), Math.max(1, baseH));
        int value = setting();
        return BASE_PIXELS * (value == AUTO ? limit : Math.min(limit, value / 100.0));
    }

    /** Не помещается ли база 640×400 при 100 %: тогда окну семьи положена сжатая база 480×270. */
    public static boolean compactNeeded() {
        Window window = Minecraft.getInstance().getWindow();
        return window.getWidth() < BASE_WIDTH * BASE_PIXELS || window.getHeight() < BASE_HEIGHT * BASE_PIXELS;
    }

    /** Пикселей экрана в одной единице интерфейса. Внутри экрана со своей базой это масштаб его базы. */
    public static double pixels() {
        if (scoped > 0) return scoped;
        return fit(BASE_WIDTH, BASE_HEIGHT);
    }

    /** Пока идёт {@code body}, масштаб считается от базы экрана ({@code pixels} пикселей в единице). */
    static void within(double pixels, Runnable body) {
        double saved = scoped;
        scoped = pixels;
        try {
            body.run();
        } finally {
            scoped = saved;
        }
    }

    static <T> T within(double pixels, java.util.function.Supplier<T> body) {
        double saved = scoped;
        scoped = pixels;
        try {
            return body.get();
        } finally {
            scoped = saved;
        }
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
