package works.repka.ryscore.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Читатель вольного SNBT, которым пишет FTB Library (1.11): запятые необязательны, ключи бывают без кавычек (в том числе
 * UUID с дефисами), в файлах настроек есть комментарии {@code #} до конца строки. Разбор игры требует запятых, поэтому свой.
 *
 * <p>Значения становятся элементами Gson. {@code 0b} и {@code 1b} читаются как логические (так FTB пишет флаги),
 * прочие числа с суффиксами {@code b s l f d} как числа, типизированные массивы {@code [I; ...]} как обычные массивы.
 * Помощники {@link #bool}, {@link #integer}, {@link #longValue} принимают и число, и логическое.
 *
 * <p>Без зависимостей от игры, кроме Gson. Ошибка разбора это {@link SyntaxError} с файлом, строкой и столбцом.
 */
public final class Snbt {
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+[bBsSlL]?");
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?[fFdD]?");

    private final String text;
    private int pos;

    private Snbt(String text) {
        this.text = text;
    }

    /** Разбирает текст целиком. */
    public static JsonElement parse(String text) {
        Snbt parser = new Snbt(text);
        JsonElement value = parser.value();
        parser.skipSeparators();
        if (parser.pos < text.length()) throw parser.error("extra text after the value");
        return value;
    }

    /** Читает файл в UTF-8. Ошибка разбора называет файл. */
    public static JsonElement read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            return parse(text);
        } catch (SyntaxError e) {
            throw e.in(file);
        }
    }

    /** Читает файл, в котором ожидается объект. */
    public static JsonObject readObject(Path file) throws IOException {
        JsonElement e = read(file);
        if (!e.isJsonObject()) throw new SyntaxError("the file is not an object", 1, 1).in(file);
        return e.getAsJsonObject();
    }

    // ---- помощники чтения ----

    /** Объект по ключу или пустой объект. */
    public static JsonObject object(@Nullable JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    /** Массив по ключу или пустой массив. */
    public static JsonArray array(@Nullable JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    public static String string(@Nullable JsonObject o, String key, String fallback) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    /** Логическое: {@code 1b}, {@code true} или ненулевое число. */
    public static boolean bool(@Nullable JsonObject o, String key, boolean fallback) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return p.getAsDouble() != 0;
        return fallback;
    }

    public static long longValue(@Nullable JsonObject o, String key, long fallback) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean() ? 1 : 0;
        if (p.isNumber()) return p.getAsLong();
        return fallback;
    }

    public static int integer(@Nullable JsonObject o, String key, int fallback) {
        return (int) longValue(o, key, fallback);
    }

    /** UUID из строки с дефисами или из массива четырёх чисел ({@code [I; a, b, c, d]}, так пишет игра). Null, если не вышло. */
    public static @Nullable UUID uuid(@Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        try {
            if (e.isJsonPrimitive()) return UUID.fromString(e.getAsString());
            if (e.isJsonArray() && e.getAsJsonArray().size() == 4) {
                JsonArray a = e.getAsJsonArray();
                long most = (long) a.get(0).getAsInt() << 32 | a.get(1).getAsInt() & 0xFFFFFFFFL;
                long least = (long) a.get(2).getAsInt() << 32 | a.get(3).getAsInt() & 0xFFFFFFFFL;
                return new UUID(most, least);
            }
        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException ignored) {
            // не UUID
        }
        return null;
    }

    // ---- разбор ----

    private JsonElement value() {
        skipSeparators();
        if (pos >= text.length()) throw error("unexpected end");
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"', '\'' -> new JsonPrimitive(quoted());
            default -> literal(bare());
        };
    }

    private JsonObject object() {
        pos++;
        JsonObject object = new JsonObject();
        while (true) {
            skipSeparators();
            if (pos >= text.length()) throw error("object not closed");
            char c = text.charAt(pos);
            if (c == '}') {
                pos++;
                return object;
            }
            String key = c == '"' || c == '\'' ? quoted() : bare();
            skipSeparators();
            if (pos >= text.length() || text.charAt(pos) != ':') throw error("no \":\" after key " + key);
            pos++;
            object.add(key, value());
        }
    }

    private JsonArray array() {
        pos++;
        // типизированный массив: [B; ...], [I; ...], [L; ...]
        int save = pos;
        skipSpaces();
        if (pos + 1 < text.length() && "BIL".indexOf(text.charAt(pos)) >= 0 && text.charAt(pos + 1) == ';') {
            pos += 2;
        } else {
            pos = save;
        }
        JsonArray array = new JsonArray();
        while (true) {
            skipSeparators();
            if (pos >= text.length()) throw error("array not closed");
            if (text.charAt(pos) == ']') {
                pos++;
                return array;
            }
            array.add(value());
        }
    }

    private String quoted() {
        char quote = text.charAt(pos++);
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == quote) return out.toString();
            if (c == '\\' && pos < text.length()) {
                out.append(unescape(text.charAt(pos++)));
            } else {
                out.append(c);
            }
        }
        throw error("string not closed");
    }

    private static char unescape(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            default -> c;
        };
    }

    private String bare() {
        int start = pos;
        while (pos < text.length() && ":,{}[]".indexOf(text.charAt(pos)) < 0 && !Character.isWhitespace(text.charAt(pos))) pos++;
        if (pos == start) throw error("value expected");
        return text.substring(start, pos);
    }

    private static JsonElement literal(String token) {
        if (token.equals("true") || token.equals("false")) return new JsonPrimitive(Boolean.parseBoolean(token));
        // в NBT нет логического типа: флаги пишутся как 0b и 1b
        if (token.equals("0b") || token.equals("1b") || token.equals("0B") || token.equals("1B")) return new JsonPrimitive(token.charAt(0) == '1');
        if (token.equals("null")) return JsonNull.INSTANCE;
        try {
            if (INTEGER.matcher(token).matches()) return new JsonPrimitive(Long.parseLong(stripSuffix(token)));
            if (DECIMAL.matcher(token).matches()) return new JsonPrimitive(Double.parseDouble(stripSuffix(token)));
        } catch (NumberFormatException ignored) {
            // слишком длинное число остаётся строкой
        }
        return new JsonPrimitive(token);
    }

    private static String stripSuffix(String token) {
        String t = token.startsWith("+") ? token.substring(1) : token;
        return Character.isLetter(t.charAt(t.length() - 1)) ? t.substring(0, t.length() - 1) : t;
    }

    private void skipSpaces() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    /** Пробелы, запятые, метка порядка байтов и комментарии {@code #} до конца строки. */
    private void skipSeparators() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c) || c == ',' || c == '﻿') {
                pos++;
            } else if (c == '#') {
                while (pos < text.length() && text.charAt(pos) != '\n') pos++;
            } else {
                return;
            }
        }
    }

    private SyntaxError error(String message) {
        int line = 1, column = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new SyntaxError(message, line, column);
    }

    /** Где файл не прочитался: строка и столбец с единицы, причина и файл, если он известен. */
    public static final class SyntaxError extends IllegalArgumentException {
        private final String reason;
        private final int line, column;
        private final @Nullable Path file;

        SyntaxError(String reason, int line, int column) {
            this(reason, line, column, null);
        }

        private SyntaxError(String reason, int line, int column, @Nullable Path file) {
            super("SNBT: " + reason + ", line " + line + ", column " + column + (file == null ? "" : ", file " + file));
            this.reason = reason;
            this.line = line;
            this.column = column;
            this.file = file;
        }

        SyntaxError in(Path file) {
            return new SyntaxError(reason, line, column, file);
        }

        public String reason() {
            return reason;
        }

        public int line() {
            return line;
        }

        public int column() {
            return column;
        }

        public @Nullable Path file() {
            return file;
        }
    }
}
