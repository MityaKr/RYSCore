package works.repka.ryscore.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import works.repka.ryscore.RYSCore;

/**
 * Общие уведомления семьи (1.9.2). Каждое уведомление — настоящий тост игры ({@link Toast}): очередь и места даёт колонка
 * тостов игры, она рисует их поверх мира, открытых окон и чужих панелей и прячет на F1. С 1.17 место выбирает игрок
 * ({@link #place()}): справа (по умолчанию) колонка тостов встаёт под зону карты ядра ({@link Anchors}), по центру и слева
 * уведомления семьи идут своей стопкой (по центру под боссбарами). Уведомление выезжает сверху вниз и уходит вверх. Ядро добавляет к этому вид по теме, паузу при наведении, клик и крестик в открытом окне,
 * клавишу мода в мире ({@link #press}), сводку для серии и свои звуки вместо шороха тоста игры.
 *
 * <p>Моды друг о друге не знают: каждый отдаёт {@link Notice} со своим источником. Правило семьи: своих тостов
 * в других местах экрана моды не рисуют.
 */
public final class Notices {
    /**
     * Где стоят уведомления (1.17, {@code config/ryscore/ui.json}, поле {@code noticePlace}). Справа: колонка тостов игры
     * под зоной карты, если карта в правом верхнем углу. По центру: под боссбарами; подсказка Jade по умолчанию тоже сверху
     * по центру и может лечь рядом. Слева: под зоной карты, если карта в левом верхнем углу.
     */
    public enum Place { RIGHT, CENTER, LEFT }

    /** Сколько карточек источника видно до того, как следующие сложатся в сводку; в низком окне — одна. */
    private static final int CARDS_BEFORE_SUMMARY = 2;
    private static final int LOW_WINDOW = 540;
    /** Звук квеста в серии не чаще раза в 1,5 с. */
    private static final long QUEST_SOUND_GAP = 1500;
    private static final List<NoticeToast> ACTIVE = new ArrayList<>();
    private static final List<String> PLAYED = new ArrayList<>();
    private static long lastQuestSound;

    private Notices() {}

    /** Показывает уведомление или обновляет видимое с тем же источником и id. Из другого потока уходит в поток клиента. */
    public static void show(Notice notice) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> show(notice));
            return;
        }
        prune();
        NoticeToast same = find(notice.source, notice.id);
        if (same != null) {
            if (same.replace(notice)) return;
            // новое содержимое выше прежнего: старая карточка уходит, новая встаёт в колонку заново
            same.dismiss();
        }
        if (notice.kind == Notice.Kind.CARD && notice.summaryTitle != null) {
            int limit = mc.getWindow().getHeight() < LOW_WINDOW ? 1 : CARDS_BEFORE_SUMMARY;
            int cards = 0;
            NoticeToast summary = null;
            for (NoticeToast t : ACTIVE) {
                if (!t.notice.source.equals(notice.source) || t.leaving()) continue;
                if (t.notice.kind == Notice.Kind.SUMMARY) summary = t;
                else if (t.notice.kind == Notice.Kind.CARD && t.notice.summaryTitle != null) cards++;
            }
            if (summary != null) {
                summary.absorb(notice);
                playOnce(notice.sound);
                return;
            }
            if (cards >= limit) {
                Notice sum = Notice.summary(notice.source, "summary");
                sum.summaryTitle = notice.summaryTitle;
                sum.summaryLine = notice.summaryLine;
                sum.key = notice.key;
                sum.hidden = notice.hidden;
                sum.sound = notice.sound;
                NoticeToast toast = new NoticeToast(sum);
                toast.absorb(notice);
                add(mc, toast);
                return;
            }
        }
        add(mc, new NoticeToast(notice));
    }

    private static void add(Minecraft mc, NoticeToast toast) {
        ACTIVE.add(toast);
        mc.getToasts().addToast(toast);
    }

    /** Убирает уведомление, если оно видно или ждёт места. */
    public static void dismiss(String source, String id) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> dismiss(source, id));
            return;
        }
        NoticeToast t = find(source, id);
        if (t != null) t.dismiss();
    }

    /** Видно ли уведомление или ждёт ли оно места в колонке. */
    public static boolean showing(String source, String id) {
        prune();
        NoticeToast t = find(source, id);
        return t != null && !t.leaving();
    }

    /**
     * Клавиша мода в мире: самое новое видимое уведомление источника с действием выполняет его и уходит.
     * @return было ли такое уведомление; если нет, клавиша работает как обычно
     */
    public static boolean press(String source) {
        prune();
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            NoticeToast t = ACTIVE.get(i);
            if (t.notice.source.equals(source) && t.onScreen() && t.notice.action != null) {
                t.notice.action.run();
                t.dismiss();
                return true;
            }
        }
        return false;
    }

    /**
     * Клавиши ядра «Принять» и «Отказать» (1.14): верхнее видимое уведомление, у которого есть кнопка {@link Notice#accept}
     * или {@link Notice#decline}, выполняет её и уходит.
     * @return было ли такое уведомление
     */
    public static boolean answer(boolean yes) {
        prune();
        for (NoticeToast t : List.copyOf(ACTIVE)) {
            if (!t.onScreen() || t.leaving()) continue;
            Notice.Button b = yes ? t.notice.accept : t.notice.decline;
            if (b == null) continue;
            b.action().run();
            t.dismiss();
            return true;
        }
        return false;
    }

    /** Общая настройка семьи «Звук уведомлений». */
    public static boolean soundOn() {
        return UiConfig.get().noticeSound;
    }

    public static void setSoundOn(boolean on) {
        UiConfig.get().noticeSound = on;
        UiConfig.save();
    }

    /** Громкость звуков уведомлений, 0–1. По умолчанию обычная (1). */
    public static float volume() {
        return Math.max(0, Math.min(100, UiConfig.get().noticeVolume)) / 100f;
    }

    /** Место уведомлений, выбранное игроком. */
    public static Place place() {
        Place p = UiConfig.get().noticePlace;
        return p == null ? Place.RIGHT : p;
    }

    public static void setPlace(Place place) {
        if (UiConfig.get().noticePlace == place) return;
        UiConfig.get().noticePlace = java.util.Objects.requireNonNull(place);
        UiConfig.save();
    }

    // --- для загрузчика ---

    private static long bossFrame = -1;
    private static int bossBottom;

    /** Нижний край боссбара, нарисованного в этом кадре, в единицах GUI игры: уведомления по центру встают ниже. */
    public static void bossBar(int bottom) {
        long frame = Minecraft.getInstance().getFrameTimeNs();
        if (frame != bossFrame) {
            bossFrame = frame;
            bossBottom = 0;
        }
        bossBottom = Math.max(bossBottom, bottom);
    }

    private static int bossBottom() {
        return bossFrame == Minecraft.getInstance().getFrameTimeNs() ? bossBottom : 0;
    }

    /**
     * Место тоста игры в колонке: колонка поставила его в {@code (x, y)} (выезд справа, место {@code index}), ядро
     * возвращает, куда его поставить на самом деле. Уведомление семьи стоит по выбору игрока и выезжает сверху; чужой тост
     * при месте «справа» сдвигается под зону карты вместе с колонкой, иначе остаётся где был.
     */
    public static float[] toastAt(Toast toast, int screenW, int index, float x, float y) {
        Place place = place();
        boolean notice = toast instanceof NoticeToast;
        if (place == Place.RIGHT) {
            float base = NoticePlace.base((float) UiScale.toGui(Anchors.topInset(Anchors.Corner.TOP_RIGHT)));
            if (!notice) return new float[] {x, y + base};
            NoticeToast n = (NoticeToast) toast;
            float top = base + index * NoticePlace.SLOT;
            n.placed(index, top);
            return new float[] {NoticePlace.x(place, screenW, toast.width()), NoticePlace.slide(top, toast.height(), NoticePlace.progress(screenW, toast.width(), x))};
        }
        if (!notice) return new float[] {x, y};
        NoticeToast n = (NoticeToast) toast;
        float zone = place == Place.CENTER ? bossBottom() : (float) UiScale.toGui(Anchors.topInset(Anchors.Corner.TOP_LEFT));
        int[] above = ACTIVE.stream().filter(t -> t != n && t.slot >= 0 && t.slot < index && t.onScreen()).mapToInt(NoticeToast::height).toArray();
        float top = n.stackTo(index, NoticePlace.stack(NoticePlace.base(zone), above));
        return new float[] {NoticePlace.x(place, screenW, toast.width()), NoticePlace.slide(top, toast.height(), NoticePlace.progress(screenW, toast.width(), x))};
    }

    /**
     * Нажатие мыши в открытом окне, координаты в единицах GUI игры.
     * @return попало ли нажатие в уведомление; тогда в окно под ним оно не идёт
     */
    public static boolean click(double x, double y, int button) {
        prune();
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            if (ACTIVE.get(i).click(x, y, button)) return true;
        }
        return false;
    }

    /** Лежит ли курсор на уведомлении: отпускание кнопки над ним тоже не уходит в окно. */
    public static boolean over(double x, double y) {
        for (NoticeToast t : ACTIVE) {
            if (t.contains(x, y)) return true;
        }
        return false;
    }

    /** Тост игры, у которого свой звук: шорох появления и ухода тоста игры для него не играет. */
    public static boolean silent(Toast toast) {
        return toast instanceof NoticeToast;
    }

    // --- внутри ядра ---

    static void finished(NoticeToast toast) {
        ACTIVE.remove(toast);
    }

    private static NoticeToast find(String source, String id) {
        for (NoticeToast t : ACTIVE) {
            if (t.notice.source.equals(source) && t.notice.id.equals(id) && !t.leaving()) return t;
        }
        return null;
    }

    /** Колонка тостов игры очищается при смене мира: что игра забыла, забываем и мы. */
    private static void prune() {
        var toasts = Minecraft.getInstance().getToasts();
        ACTIVE.removeIf(t -> toasts.getToast(NoticeToast.class, t.getToken()) == null);
    }

    /** Звук карточки, когда она показалась. Серия квестов звенит один раз. */
    static void playOnce(Notice.Sound sound) {
        if (sound == Notice.Sound.NONE) return;
        long now = System.currentTimeMillis();
        if (sound == Notice.Sound.QUEST) {
            if (now - lastQuestSound < QUEST_SOUND_GAP) return;
            lastQuestSound = now;
        }
        play(sound);
    }

    /**
     * Мягкие короткие звуки из звуков игры, громкость обычная. Нота «колокольчик» нотного блока (в звуках игры громкость 1)
     * даёт звон, перелив аметиста (в звуках игры громкость 0,2, поэтому тише сам по себе) — мягкий хвост.
     * Квест: один звон. Веха: две ноты вверх. Глава: три ноты вверх. Эпоха: три ноты и аккорд с колоколом. Шаг нот 100 мс.
     */
    static void play(Notice.Sound sound) {
        if (!soundOn() || sound == Notice.Sound.NONE) return;
        float v = volume();
        SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value(), bell = SoundEvents.NOTE_BLOCK_BELL.value();
        SoundEvent shimmer = SoundEvents.AMETHYST_BLOCK_CHIME;
        switch (sound) {
            case QUEST -> {
                note(chime, 1.26f, v, 0);
                note(shimmer, 1.2f, v, 0);
            }
            case MILESTONE -> {
                note(chime, 1.0f, v, 0);
                note(chime, 1.5f, v, 2);
                note(shimmer, 1.2f, v, 2);
            }
            case CHAPTER -> {
                note(chime, 1.0f, v, 0);
                note(chime, 1.26f, v, 2);
                note(chime, 1.5f, v, 4);
                note(shimmer, 1.2f, v, 4);
            }
            case EPOCH -> {
                note(chime, 1.0f, v, 0);
                note(chime, 1.26f, v, 2);
                note(chime, 1.5f, v, 4);
                note(chime, 2.0f, v, 6);
                note(bell, 1.0f, v, 6);
                note(shimmer, 1.0f, v, 6);
            }
            case INVITE -> {
                note(chime, 1.5f, v, 0);
                note(chime, 1.26f, v, 2);
            }
            default -> {
            }
        }
    }

    private static void note(SoundEvent event, float pitch, float volume, int ticks) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        SimpleSoundInstance instance = SimpleSoundInstance.forUI(event, pitch, volume);
        if (ticks <= 0) sounds.play(instance);
        else sounds.playDelayed(instance, ticks);
        String line = event.getLocation() + " volume " + volume + " pitch " + pitch + " delay " + ticks * 50 + "ms";
        PLAYED.add(line);
        if (PLAYED.size() > 64) PLAYED.removeFirst();
        RYSCore.LOG.debug("Notice sound: {}", line);
    }

    // --- для автотестов ---

    /** Сыгранные звуки уведомлений (событие, громкость, тон, задержка), последние 64. */
    public static List<String> playedForTest() {
        return List.copyOf(PLAYED);
    }

    /** Видимые и ждущие уведомления: «источник/id вид». */
    public static List<String> activeForTest() {
        prune();
        List<String> out = new ArrayList<>();
        for (NoticeToast t : ACTIVE) out.add(t.notice.source + "/" + t.notice.id + " " + t.notice.kind + (t.onScreen() ? " on" : " queued"));
        return out;
    }

    /** Прямоугольник уведомления на экране в единицах GUI игры (x, y, w, h), или null. */
    public static int[] boundsForTest(String source, String id) {
        NoticeToast t = find(source, id);
        return t == null || !t.onScreen() ? null : t.bounds();
    }

    public static void clearForTest() {
        for (NoticeToast t : List.copyOf(ACTIVE)) t.dismiss();
        Minecraft.getInstance().getToasts().clear();
        ACTIVE.clear();
    }
}
