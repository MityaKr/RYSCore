package works.repka.ryscore.ui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;

/**
 * Поле ввода с текстом темы и чипами, как в макете поиска. Логику ввода даёт EditBox игры,
 * здесь отрисовка и перевод клика в позицию курсора.
 */
public class Field extends EditBox {
    /** Часть текста с плашкой: {@code kind} это вид чипа темы (mod, tag, fn). */
    public record Span(int start, int end, String kind) {}

    private static final float CHIP_PAD = 4f;
    private static final float TEXT_PX = 13f, CHIP_PX = 12f;
    private List<Span> spans = List.of();
    private int[] colors = new int[0];
    private Component hintText;
    private float view;
    private long blinkFrom = System.currentTimeMillis();

    public Field(int width, int height) {
        super(Minecraft.getInstance().font, 0, 0, width, height, Component.empty());
        setBordered(false);
    }

    @Override
    public void setHint(Component hint) {
        hintText = hint;
        super.setHint(hint);
    }

    /** Чипы и цвета символов; вызывается перед отрисовкой. */
    public void setSpans(List<Span> spans, int[] colors) {
        this.spans = spans;
        this.colors = colors;
    }

    private Span spanAt(int index) {
        for (Span s : spans) if (index >= s.start && index < s.end) return s;
        return null;
    }

    /** Смещение символов от начала текста: чипы добавляют внутренние поля. */
    private float[] offsets(String value) {
        float[] x = new float[value.length() + 1];
        float pen = 0;
        Span previous = null;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            int n = Character.charCount(cp);
            Span span = spanAt(i);
            if (span != previous) {
                if (previous != null) pen += CHIP_PAD;
                if (span != null) pen += CHIP_PAD;
            }
            previous = span;
            x[i] = pen;
            if (n == 2) x[i + 1] = pen;
            pen += span != null ? Type.advance(cp, CHIP_PX, Type.Weight.MEDIUM) : Type.advance(cp, TEXT_PX, Type.Weight.REGULAR);
            i += n;
        }
        if (previous != null) pen += CHIP_PAD;
        x[value.length()] = pen;
        return x;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme theme = ThemeManager.get();
        if (!theme.usesType()) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        if (!isVisible()) return;
        String value = getValue();
        float left = getX(), cy = getY() + getHeight() / 2f;
        int inner = getInnerWidth();
        if (value.isEmpty() && !isFocused() && hintText != null) {
            Draw.label(g, hintText.getString(), left, cy, TEXT_PX, Type.Weight.REGULAR, theme.c("text.dim"));
            return;
        }
        float[] x = offsets(value);
        int cursor = Math.min(getCursorPosition(), value.length());
        float end = x[cursor];
        if (end - view > inner - 2) view = end - inner + 2;
        if (end - view < 0) view = end;
        if (x[value.length()] - view < inner - 2) view = Math.max(0, x[value.length()] - inner + 2);
        g.enableScissor(getX() - 1, getY() - 3, getX() + inner + 1, getY() + getHeight() + 3);
        float origin = left - view;
        for (Span s : spans) {
            if (s.end > value.length() || s.start >= s.end) continue;
            float x0 = origin + x[s.start] - CHIP_PAD;
            float x1 = origin + x[s.end - 1] + Type.advance(value.codePointBefore(s.end), CHIP_PX, Type.Weight.MEDIUM) + CHIP_PAD;
            Draw.round(g, x0, cy - 4.75f, x1 - x0, 9.5f, theme.m("chip.radius", 6f) / 2f, theme.c("chip." + s.kind + ".bg"));
        }
        String highlighted = getHighlighted();
        if (isFocused() && !highlighted.isEmpty()) {
            boolean forward = cursor + highlighted.length() <= value.length() && value.startsWith(highlighted, cursor);
            int a = forward ? cursor : cursor - highlighted.length(), b = a + highlighted.length();
            if (a >= 0) Draw.round(g, origin + x[a], cy - 5f, x[b] - x[a], 10f, 0.02f, Draw.alpha(theme.c("accent"), 0.35f));
        }
        int base = theme.c("input.fg");
        int i = 0;
        while (i < value.length()) {
            Span span = spanAt(i);
            int color = colorOf(i, base);
            int j = i + 1;
            while (j < value.length() && spanAt(j) == span && colorOf(j, base) == color) j++;
            Draw.label(g, value.substring(i, j), origin + x[i], cy, span != null ? CHIP_PX : TEXT_PX, span != null ? Type.Weight.MEDIUM : Type.Weight.REGULAR, color);
            i = j;
        }
        if (isFocused() && ((System.currentTimeMillis() - blinkFrom) / 500) % 2 == 0) {
            Draw.round(g, origin + end, cy - 4.5f, 0.5f, 9f, 0.02f, theme.c("text.body"));
        }
        g.disableScissor();
    }

    private int colorOf(int index, int base) {
        return index < colors.length && colors[index] != 0 ? colors[index] : base;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!ThemeManager.get().usesType()) {
            super.onClick(mouseX, mouseY);
            return;
        }
        blinkFrom = System.currentTimeMillis();
        float[] x = offsets(getValue());
        double target = mouseX - getX() + view;
        int best = 0;
        for (int i = 0; i < x.length; i++) if (Math.abs(x[i] - target) < Math.abs(x[best] - target)) best = i;
        moveCursorTo(best, Screen.hasShiftDown());
    }
}
