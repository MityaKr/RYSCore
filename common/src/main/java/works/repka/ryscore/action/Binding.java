package works.repka.ryscore.action;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Своя клавиша действия (1.14): клавиша или кнопка мыши и модификаторы Ctrl, Shift, Alt. Срабатывает только при точном
 * совпадении модификаторов: Alt+1 не спорит с 1. Хранится строкой {@code key.keyboard.h+ctrl+alt}.
 */
public final class Binding {
    private final InputConstants.Key key;
    private final boolean ctrl, shift, alt;

    private Binding(InputConstants.Key key, boolean ctrl, boolean shift, boolean alt) {
        this.key = Objects.requireNonNull(key);
        this.ctrl = ctrl;
        this.shift = shift;
        this.alt = alt;
    }

    public static Binding of(InputConstants.Key key, boolean ctrl, boolean shift, boolean alt) {
        return new Binding(key, ctrl, shift, alt);
    }

    /** Из флагов GLFW ({@code GLFW_MOD_*}). */
    public static Binding of(InputConstants.Key key, int mods) {
        return new Binding(key, (mods & GLFW.GLFW_MOD_CONTROL) != 0, (mods & GLFW.GLFW_MOD_SHIFT) != 0, (mods & GLFW.GLFW_MOD_ALT) != 0);
    }

    public static @Nullable Binding parse(@Nullable String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.split("\\+");
        try {
            InputConstants.Key key = InputConstants.getKey(parts[0]);
            if (key == InputConstants.UNKNOWN) return null;
            boolean c = false, s = false, a = false;
            for (int i = 1; i < parts.length; i++) {
                switch (parts[i]) {
                    case "ctrl" -> c = true;
                    case "shift" -> s = true;
                    case "alt" -> a = true;
                    default -> {
                    }
                }
            }
            return new Binding(key, c, s, a);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Клавиша, на которой стоит клавиша игры (в чистой игре у KeyMapping нет открытого getKey). */
    public static InputConstants.Key keyOf(net.minecraft.client.KeyMapping mapping) {
        return InputConstants.getKey(mapping.saveString());
    }

    public InputConstants.Key key() {
        return key;
    }

    public boolean ctrl() {
        return ctrl;
    }

    public boolean shift() {
        return shift;
    }

    public boolean alt() {
        return alt;
    }

    /** Есть ли хотя бы один модификатор. */
    public boolean modified() {
        return ctrl || shift || alt;
    }

    /** Флаги GLFW модификаторов. */
    public int mods() {
        return (ctrl ? GLFW.GLFW_MOD_CONTROL : 0) | (shift ? GLFW.GLFW_MOD_SHIFT : 0) | (alt ? GLFW.GLFW_MOD_ALT : 0);
    }

    /** Нажатие совпадает: та же клавиша и ровно те же Ctrl, Shift, Alt. */
    public boolean matches(InputConstants.Key pressed, int mods) {
        return key.equals(pressed) && (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT)) == mods();
    }

    public String serialize() {
        return key.getName() + (ctrl ? "+ctrl" : "") + (shift ? "+shift" : "") + (alt ? "+alt" : "");
    }

    /** Подпись для игрока: «Alt+1», «Ctrl+H», «Мышь 4». Буквы латиницей при любой раскладке. */
    public String label() {
        return (ctrl ? "Ctrl+" : "") + (shift ? "Shift+" : "") + (alt ? "Alt+" : "") + keyLabel(key);
    }

    /** Подпись клавиши как на клавиатуре: «Y», а не «Н»; мышь «Мышь 4». */
    public static String keyLabel(InputConstants.Key key) {
        String name = key.getName();
        String keyboard = "key.keyboard.", mouse = "key.mouse.";
        if (name.startsWith(keyboard)) {
            String rest = name.substring(keyboard.length());
            if (rest.length() == 1) return rest.toUpperCase(Locale.ROOT);
            return switch (rest) {
                case "grave.accent" -> "Ё";
                case "semicolon" -> ";";
                case "apostrophe" -> "'";
                case "comma" -> ",";
                case "period" -> ".";
                case "slash" -> "/";
                case "backslash" -> "\\";
                case "minus" -> "-";
                case "equal" -> "=";
                case "left.bracket" -> "[";
                case "right.bracket" -> "]";
                case "insert" -> "Insert";
                case "home" -> "Home";
                case "end" -> "End";
                case "page.up" -> "PgUp";
                case "page.down" -> "PgDn";
                case "delete" -> "Delete";
                default -> {
                    if (rest.matches("f\\d+")) yield rest.toUpperCase(Locale.ROOT);
                    if (rest.startsWith("keypad.")) yield "Num " + rest.substring(7);
                    yield key.getDisplayName().getString();
                }
            };
        }
        if (name.startsWith(mouse)) {
            String rest = name.substring(mouse.length());
            return switch (rest) {
                case "left" -> "ЛКМ";
                case "right" -> "ПКМ";
                case "middle" -> "СКМ";
                default -> "Мышь " + rest;
            };
        }
        return key.getDisplayName().getString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Binding b && b.key.equals(key) && b.ctrl == ctrl && b.shift == shift && b.alt == alt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, ctrl, shift, alt);
    }

    @Override
    public String toString() {
        return serialize();
    }
}
