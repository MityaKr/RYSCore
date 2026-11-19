package works.repka.ryscore.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Правки, которые ждут ответа сервера (1.15.3). Окно сразу показывает новое значение (оптимистично), элемент
 * через {@link #SPIN_DELAY} мс без ответа рисует крутилку ({@link Kit#spinner}), ответ снимает ожидание, ошибка или
 * молчание дольше {@link #TIMEOUT} мс откатывает к значению сервера.
 *
 * <p>Ключ это место в окне ({@code "settings.antiSpam"}, {@code "wl.remove.Steve"}). Повторное нажатие того же места
 * до ответа заменяет ожидание: номер прежнего запроса больше ничего не снимает, побеждает последнее нажатие. Счётчик
 * «+» по оптимистичному значению поэтому прибавляет каждое нажатие, а не одно на всю серию.
 *
 * <p>Состояние без игры, только из главного потока клиента. Мод держит один {@code Pending} на свой канал: ответ может
 * прийти и после закрытия окна.
 */
public final class Pending {
    /** Через столько мс без ответа элемент показывает крутилку: быстрый ответ не мигает. */
    public static final long SPIN_DELAY = 150;
    /** Столько мс ждём ответа, дальше откат. */
    public static final long TIMEOUT = 6000;

    private record Entry(int id, @Nullable Object value, long since) {}

    private final Map<String, Entry> byKey = new HashMap<>();
    private int next = 1;

    /**
     * Начать ожидание. {@code optimistic} показывается вместо значения сервера, пока нет ответа ({@code null}: только
     * крутилка). Возвращает номер запроса, его сервер присылает в ответе.
     */
    public int start(String key, @Nullable Object optimistic, long now) {
        int id = next++;
        if (next <= 0) next = 1;
        byKey.put(key, new Entry(id, optimistic, now));
        return id;
    }

    public int start(String key, @Nullable Object optimistic) {
        return start(key, optimistic, System.currentTimeMillis());
    }

    /** Место ждёт ответа. */
    public boolean busy(String key) {
        return byKey.containsKey(key);
    }

    /** Место ждёт дольше {@link #SPIN_DELAY}: пора показать крутилку. */
    public boolean spinning(String key, long now) {
        Entry e = byKey.get(key);
        return e != null && now - e.since >= SPIN_DELAY;
    }

    public boolean spinning(String key) {
        return spinning(key, System.currentTimeMillis());
    }

    /** Значение для показа: оптимистичное, пока место ждёт и тип совпадает, иначе присланное сервером. */
    @SuppressWarnings("unchecked")
    public <T> T value(String key, T server) {
        Entry e = byKey.get(key);
        if (e == null || e.value == null || server == null || !server.getClass().isInstance(e.value)) return server;
        return (T) e.value;
    }

    /**
     * Ответ сервера на запрос {@code id}: снимает ожидание, если это последний запрос своего места. Возвращает ключ
     * места или {@code null}, если запрос уже заменён более новым или забыт.
     */
    public @Nullable String done(int id) {
        for (var it = byKey.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().id == id) {
                it.remove();
                return e.getKey();
            }
        }
        return null;
    }

    /** Снять ожидание места без ответа (окно передумало). */
    public void cancel(String key) {
        byKey.remove(key);
    }

    /** Места, которые ждут дольше {@link #TIMEOUT}: снимаются и возвращаются, чтобы мод сказал игроку. */
    public List<String> expire(long now) {
        List<String> out = new ArrayList<>();
        for (var it = byKey.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getValue().since >= TIMEOUT) {
                out.add(e.getKey());
                it.remove();
            }
        }
        return out;
    }

    public boolean any() {
        return !byKey.isEmpty();
    }

    /** Выход с сервера: ждать больше нечего. */
    public void clear() {
        byKey.clear();
    }
}
