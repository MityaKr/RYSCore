package works.repka.ryscore.ui;

/**
 * Смешивание и глубина в том виде, в каком их выставил код игры и модов, без запросов к драйверу.
 * Пишется перехватом кора на {@code GlStateManager}. Пачка кора рисуется в чужой момент (перед blit, предметом,
 * ножницами) и после себя должна вернуть именно то состояние, которое вызывающий код только что выставил.
 */
public final class GlState {
    private static boolean blendKnown, depthKnown;
    private static boolean blend, depth;
    /** Значения по умолчанию у OpenGL и у кеша игры: ONE, ZERO, LESS. */
    private static int srcRgb = 1, dstRgb = 0, srcAlpha = 1, dstAlpha = 0, depthFunc = 513;

    private GlState() {}

    public static void onBlend(boolean on) {
        blendKnown = true;
        blend = on;
    }

    public static void onBlendFunc(int src, int dst, int srcA, int dstA) {
        srcRgb = src;
        dstRgb = dst;
        srcAlpha = srcA;
        dstAlpha = dstA;
    }

    public static void onDepth(boolean on) {
        depthKnown = true;
        depth = on;
    }

    public static void onDepthFunc(int func) {
        depthFunc = func;
    }

    /** Снимок для {@link #restore}; null, если перехват ещё не видел нужных вызовов. */
    static int[] save() {
        if (!blendKnown || !depthKnown) return null;
        return new int[] {blend ? 1 : 0, srcRgb, dstRgb, srcAlpha, dstAlpha, depth ? 1 : 0, depthFunc};
    }

    static void restore(int[] s) {
        com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(s[1], s[2], s[3], s[4]);
        if (s[0] == 1) com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        else com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(s[6]);
        if (s[5] == 1) com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        else com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
    }
}
