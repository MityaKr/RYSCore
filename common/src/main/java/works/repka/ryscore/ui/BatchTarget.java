package works.repka.ryscore.ui;

/** Добавляется перехватом кора к {@link net.minecraft.client.gui.GuiGraphics}: есть ли у игры несброшенная отрисовка. */
public interface BatchTarget {
    boolean ryscore$vanillaPending();
}
