package works.repka.ryscore.ui;

import org.lwjgl.opengl.GL11;

/**
 * Состояние обрезки без запроса к драйверу: {@code glIsEnabled} и {@code glGetIntegerv} останавливают конвейер видеокарты.
 * Перехват кора на {@code GlStateManager} записывает включение, выключение и прямоугольник. Пока перехват ни разу
 * не сработал, состояние читается у драйвера, как раньше.
 */
public final class Scissor {
    private static boolean known, enabled;
    private static final int[] BOX = new int[4];

    private Scissor() {}

    /** Вызывает перехват кора. */
    public static void onEnable(boolean on) {
        known = true;
        enabled = on;
    }

    /** Вызывает перехват кора. */
    public static void onBox(int x, int y, int w, int h) {
        BOX[0] = x;
        BOX[1] = y;
        BOX[2] = w;
        BOX[3] = h;
    }

    /** Включена ли обрезка; если да, прямоугольник в пикселях окна (x, y снизу, ширина, высота) кладётся в {@code box}. */
    public static boolean current(int[] box) {
        if (!known) {
            boolean on = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (on) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            return on;
        }
        if (enabled) System.arraycopy(BOX, 0, box, 0, 4);
        return enabled;
    }
}
