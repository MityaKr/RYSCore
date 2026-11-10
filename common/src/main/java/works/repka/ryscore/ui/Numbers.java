package works.repka.ryscore.ui;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Числа для игрока: большие значения с приставками, например 2.2к и 10м. */
public final class Numbers {
    private Numbers() {}

    public static String compact(double value) {
        double a = Math.abs(value);
        if (a >= 1_000_000_000) return trim(value / 1_000_000_000) + suffix("giga");
        if (a >= 1_000_000) return trim(value / 1_000_000) + suffix("mega");
        if (a >= 1000) return trim(value / 1000) + suffix("kilo");
        return trim(value);
    }

    private static String suffix(String name) {
        return Component.translatable("ryscore.number." + name).getString();
    }

    /** Без лишних нулей: одна цифра после запятой, у больших значений ни одной. */
    public static String trim(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return Long.toString((long) value);
        if (Math.abs(value) >= 100) return Long.toString(Math.round(value));
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
