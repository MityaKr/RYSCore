package works.repka.ryscore.action;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import works.repka.ryscore.ui.Batch;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Type.Weight;

/** Общие кусочки колеса и окна клавиш: значок действия и плашка клавиши. */
final class Paint {
    private static final int TEXTURE = 42;

    private Paint() {}

    /** Значок действия в квадрате {@code size} с левым верхним углом (x, y): контур, белая текстура или предмет 16. */
    static void icon(GuiGraphics g, Action a, float x, float y, float size, int color) {
        if (!a.item().isEmpty()) {
            float s = size >= 16 ? 16 : 8;
            g.pose().pushPose();
            g.pose().translate(x + (size - s) / 2f, y + (size - s) / 2f, 0);
            g.pose().scale(s / 16f, s / 16f, 1f);
            g.renderItem(a.item(), 0, 0);
            g.pose().popPose();
            return;
        }
        if (a.texture() != null) {
            texture(g, a.texture(), Math.round(x), Math.round(y), Math.round(size), color);
            return;
        }
        Icons.draw(g, a.shape() != null ? a.shape() : Icons.Shape.DOTS, x, y, size, color);
    }

    private static void texture(GuiGraphics g, ResourceLocation texture, int x, int y, int size, int color) {
        Batch.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
        g.blit(texture, x, y, size, size, 0f, 0f, TEXTURE, TEXTURE, TEXTURE, TEXTURE);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    static float kbdWidth(String label, float px) {
        return Draw.labelWidth(label, px, Weight.SEMIBOLD) + 6;
    }

    /** Плашка клавиши «Ё», «Alt+1» с центром строки {@code cy}; возвращает ширину. */
    static float kbd(GuiGraphics g, String label, float x, float cy, float px, int fill, int border, int fg) {
        float w = kbdWidth(label, px), h = px <= 10.5f ? 8 : 9;
        Draw.roundBox(g, x, cy - h / 2f, w, h, 2.5f, fill, border);
        Draw.label(g, label, x + 3, cy, px, Weight.SEMIBOLD, fg);
        return w;
    }
}
