package works.repka.ryscore.action;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.ui.Notice;
import works.repka.ryscore.ui.Notices;

/**
 * «Принять» и «Отказать» для запросов всех модов семьи (1.14, план Essentials 3.4): клавиши Y и N живут в ядре.
 * Они отвечают на самое новое видимое уведомление с кнопками, у которого есть кнопка {@link Notice#accept} или
 * {@link Notice#decline}: приглашение в команду, запрос телепорта, вопрос другого мода. Если такого уведомления нет,
 * клавиша идёт по запасным обработчикам модов ({@link #onAccept}, {@link #onDecline}) по порядку регистрации.
 *
 * <p>Уведомления со старыми кнопками {@link Notice#button} клавиши ядра не трогают: моды на ядре 1.13 и раньше держат свои.
 */
public final class Answers {
    public static final KeyMapping ACCEPT = new KeyMapping("key.ryscore.accept", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, Actions.CATEGORY);
    public static final KeyMapping DECLINE = new KeyMapping("key.ryscore.decline", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, Actions.CATEGORY);

    private static final List<BooleanSupplier> ACCEPT_FALLBACK = new ArrayList<>(), DECLINE_FALLBACK = new ArrayList<>();

    private Answers() {}

    /**
     * Запасной обработчик «Принять», когда ответить не на что (RYSTeams: позвать в команду игрока под прицелом).
     * Возвращает true, если клавиша съедена.
     */
    public static void onAccept(BooleanSupplier handler) {
        ACCEPT_FALLBACK.add(handler);
    }

    public static void onDecline(BooleanSupplier handler) {
        DECLINE_FALLBACK.add(handler);
    }

    /** Подпись клавиши «Принять» латиницей, как на клавиатуре: «Y». */
    public static String acceptKey() {
        return label(ACCEPT);
    }

    public static String declineKey() {
        return label(DECLINE);
    }

    private static String label(KeyMapping key) {
        return key.isUnbound() ? "" : Binding.keyLabel(Binding.keyOf(key));
    }

    /** «Принять» в мире: как нажатие клавиши. @return съедено ли нажатие */
    public static boolean accept() {
        if (Notices.answer(true)) return true;
        for (BooleanSupplier h : ACCEPT_FALLBACK) if (h.getAsBoolean()) return true;
        return false;
    }

    public static boolean decline() {
        if (Notices.answer(false)) return true;
        for (BooleanSupplier h : DECLINE_FALLBACK) if (h.getAsBoolean()) return true;
        return false;
    }

    /** Каждый тик клиента (зовёт загрузчик): нажатия клавиш ответа в мире. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        while (ACCEPT.consumeClick()) if (mc.player != null && mc.screen == null) accept();
        while (DECLINE.consumeClick()) if (mc.player != null && mc.screen == null) decline();
    }
}
