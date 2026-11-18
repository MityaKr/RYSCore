package works.repka.ryscore.ui;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Где стоят постоянные элементы семьи на игровом экране и какой угол занят картой. Окна в инвентаре на эту зону не смотрят. По умолчанию занят правый верх.
 * Настройка одна на все моды, размер чужой мини-карты не учитывается. Вызывается внутри {@link UiScale#render}.
 */
public final class Anchors {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public enum Element {
        STICKERS(Corner.TOP_LEFT), PINNED_QUESTS(Corner.TOP_LEFT), INVITES(Corner.BOTTOM_RIGHT), TEAM_CARD(Corner.BOTTOM_RIGHT);

        final Corner fallback;

        Element(Corner fallback) {
            this.fallback = fallback;
        }
    }

    private static final int STACK_GAP = 6;
    private static final Map<Corner, Integer> USED = new EnumMap<>(Corner.class);
    private static long frame = -1;

    private Anchors() {}

    public static Corner corner(Element element) {
        return UiConfig.get().corners.getOrDefault(element, element.fallback);
    }

    public static void setCorner(Element element, Corner corner) {
        UiConfig.get().corners.put(element, corner);
        UiConfig.save();
    }

    public static Corner mapCorner() {
        return UiConfig.get().mapCorner;
    }

    /**
     * Угол зоны карты (1.13): карта сообщает угол, который выбрал игрок, и соседи (закреплённые квесты, карточка
     * команды, приглашения) подвигаются по нему. Пишется в {@code config/ryscore/ui.json}.
     */
    public static void setMapCorner(Corner corner) {
        if (UiConfig.get().mapCorner == corner) return;
        UiConfig.get().mapCorner = java.util.Objects.requireNonNull(corner);
        UiConfig.save();
    }

    /** Высота занятой картой зоны в единицах интерфейса. */
    public static int mapHeight() {
        return (int) Math.ceil(UiScale.height() * UiConfig.get().mapPercent / 100.0);
    }

    /** Нижний край зоны карты, если карта в этом верхнем углу; иначе 0. */
    public static int topInset(Corner corner) {
        return mapCorner() == corner ? mapHeight() : 0;
    }

    /**
     * Левый верхний угол прямоугольника w×h для элемента. Элементы в одном углу за кадр выстраиваются стопкой
     * и не заходят на зону карты.
     */
    public static int[] place(Element element, int w, int h, int marginX, int marginY) {
        long now = Minecraft.getInstance().getFrameTimeNs();
        if (now != frame) {
            frame = now;
            USED.clear();
        }
        Corner corner = corner(element);
        int offset = USED.getOrDefault(corner, 0);
        boolean top = corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT;
        boolean left = corner == Corner.TOP_LEFT || corner == Corner.BOTTOM_LEFT;
        int inset = mapCorner() == corner ? mapHeight() : 0;
        int x = left ? marginX : UiScale.width() - w - marginX;
        int y = top ? marginY + inset + offset : UiScale.height() - h - marginY - inset - offset;
        USED.put(corner, offset + h + STACK_GAP);
        return new int[] {x, y};
    }
}
