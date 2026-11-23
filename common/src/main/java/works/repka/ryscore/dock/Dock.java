package works.repka.ryscore.dock;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.RYSCore;
import works.repka.ryscore.theme.Theme;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Batch;
import works.repka.ryscore.ui.Draw;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Type.Weight;

/**
 * Панель кнопок семьи в инвентаре: сетка цветных плиток из {@link DockButtons} и, если панель заменяет панель FTB,
 * кнопки FTB Library в том же стиле (их действия, значки и подсказки). Зависимости от FTB нет: всё через отражение,
 * а если FTB Library нет или её устройство изменилось, остаётся её собственная панель.
 *
 * <p>Три случая:
 * <ul>
 *   <li>FTB Library нет: панель семьи с кнопками реестра. Её ставит хозяин ({@link Host}, например RYSIndex в своей
 *       левой колонке), а без хозяина кор рисует её сам слева от окна инвентаря;</li>
 *   <li>FTB Library есть, хозяина нет (стоит только RYSQuests): своей панели нет, кнопки модов стоят в панели FTB
 *       (их файлы {@code sidebar_buttons});</li>
 *   <li>FTB Library и хозяин, который заменяет её панель ({@link Host#replacesFtb}): панель семьи вместо панели FTB,
 *       кнопки FTB внутри неё, кнопки семьи первыми.</li>
 * </ul>
 * Все координаты в единицах интерфейса ({@link works.repka.ryscore.ui.UiScale}).
 */
public final class Dock {
    private static final int BUTTON = 18;
    private static final int GAP = 2;
    private static final int PAD = 5;
    private static final int ICON = 12;
    private static final int SHAPE = 10;
    private static final int ICON_TEXTURE = 42;
    private static final long REFRESH_MS = 500;
    /** Панель рисуется мельче остального интерфейса: при 125 % кнопки выглядят как при 100 %. */
    private static final float SCALE = 0.8f;

    /** Порядок кнопок FTB: сначала прогресс и карта, потом частые действия, настройки последними. */
    private static final List<String> ORDER = List.of(
        "ftbquests_quests", "ftbteams_my_team", "ftbteams_team_lives", "ftbchunks_chunks", "ftbchunks_claim_chunks",
        "ftbessentials_trash_can", "ftblibrary_toggle_day", "ftblibrary_toggle_night", "ftblibrary_toggle_rain",
        "ftblibrary_toggle_gamemode", "ftbultimine_config", "ftbbackups3_config", "ftblibrary_config");

    /** Цвет плитки у каждой кнопки FTB свой: кнопки различаются с первого взгляда. Для них в коре свои значки. */
    private static final Map<String, Integer> TILES = Map.ofEntries(
        Map.entry("ftbquests_quests", 0xFFE9A83A), Map.entry("ftbteams_my_team", 0xFF4C8DF5), Map.entry("ftbteams_team_lives", 0xFFE5484D),
        Map.entry("ftbchunks_chunks", 0xFF3FB56B), Map.entry("ftbchunks_claim_chunks", 0xFF159E94), Map.entry("ftbessentials_trash_can", 0xFF8A93A6),
        Map.entry("ftblibrary_toggle_day", 0xFFF5C542), Map.entry("ftblibrary_toggle_night", 0xFF5B5BD6), Map.entry("ftblibrary_toggle_rain", 0xFF38A3E0),
        Map.entry("ftblibrary_toggle_gamemode", 0xFFD9622B), Map.entry("ftbultimine_config", 0xFFA9753F), Map.entry("ftbbackups3_config", 0xFF8E5BD0),
        Map.entry("ftblibrary_config", 0xFF6B7488));

    /**
     * Мод, который сам ставит панель на своих экранах (RYSIndex: верх левой колонки, панели обходят друг друга).
     * Без хозяина кор рисует панель сам слева от окна инвентаря.
     */
    public interface Host {
        /** Хозяин сам ставит и рисует панель на этом экране: {@link #layout}, {@link #render}, {@link #click}. */
        boolean hosts(Screen screen);

        /** Панель семьи заменяет здесь панель FTB: её виджет убирается, кнопки FTB встают в панель семьи. */
        default boolean replacesFtb(Screen screen) {
            return hosts(screen);
        }
    }

    /** Кнопка на панели: своя из реестра или кнопка FTB. */
    private record Entry(String key, @Nullable DockButton own, @Nullable Object ftb, @Nullable ResourceLocation texture,
                         @Nullable Object ftbIcon, int tile) {}

    @Nullable
    private static Host host;
    private static List<Entry> entries = List.of();
    private static long refreshedAt;
    private static int refreshedVersion = -1;
    private static boolean refreshedWithFtb;
    private static boolean visible;
    private static boolean broken;
    /** Размеры в единицах панели, то есть до умножения на {@link #SCALE}. */
    private static int x, y, w, h, cols, gridX, gridY;

    private Dock() {}

    /** Новый экран: прежняя раскладка недействительна, пока её не сделают заново. Вызывает кор. */
    public static void screenChanged() {
        visible = false;
        ftbScreen = null;
    }

    /** Видна ли панель FTB, запомнено на экран (1.15): отражение FTB не зовётся каждый кадр. */
    private static @Nullable java.lang.ref.WeakReference<Screen> ftbScreen;
    private static boolean ftbScreenVisible;

    /** Мод-хозяин панели. Один на сборку: RYSIndex. */
    public static void setHost(@Nullable Host value) {
        host = value;
    }

    /** Отражение в FTB Library. Отдельный класс: без FTB он не загружается. */
    private static final class Ftb {
        static final boolean OK;
        static Object manager;
        static Class<?> widget;
        static Field lastArea;
        static Method visible, enabled, sidebarButton, id, data, icon, tooltip, click, overlays, drawIcon, renderOverlay;

        static {
            boolean ok = false;
            Class<?> managerClass = null;
            try {
                managerClass = Class.forName("dev.ftb.mods.ftblibrary.sidebar.SidebarButtonManager");
            } catch (Throwable ignored) {
                // FTB Library не установлена
            }
            if (managerClass != null) {
                try {
                    manager = managerClass.getField("INSTANCE").get(null);
                    enabled = managerClass.getMethod("getEnabledButtonList", boolean.class);
                    visible = Class.forName("dev.ftb.mods.ftblibrary.FTBLibraryClient").getMethod("areButtonsVisible", Screen.class);
                    widget = Class.forName("dev.ftb.mods.ftblibrary.sidebar.SidebarGroupGuiButton");
                    lastArea = widget.getField("lastDrawnArea");
                    sidebarButton = Class.forName("dev.ftb.mods.ftblibrary.sidebar.SidebarGuiButton").getMethod("getSidebarButton");
                    Class<?> registered = Class.forName("dev.ftb.mods.ftblibrary.sidebar.RegisteredSidebarButton");
                    id = registered.getMethod("getId");
                    data = registered.getMethod("getData");
                    tooltip = registered.getMethod("getTooltip", boolean.class);
                    click = registered.getMethod("clickButton", boolean.class);
                    overlays = registered.getMethod("getExtraRenderers");
                    icon = Class.forName("dev.ftb.mods.ftblibrary.sidebar.SidebarButtonData").getMethod("icon");
                    drawIcon = Class.forName("dev.ftb.mods.ftblibrary.icon.Icon")
                        .getMethod("draw", GuiGraphics.class, int.class, int.class, int.class, int.class);
                    renderOverlay = Class.forName("dev.ftb.mods.ftblibrary.api.sidebar.ButtonOverlayRender")
                        .getMethod("render", GuiGraphics.class, net.minecraft.client.gui.Font.class, int.class);
                    ok = true;
                } catch (Throwable t) {
                    RYSCore.LOG.warn("FTB Library buttons unavailable, its own panel stays: {}", t.toString());
                }
            }
            OK = ok;
        }
    }

    /** Установлена ли FTB Library такой версии, с которой панель умеет работать. */
    public static boolean available() {
        return !broken && Ftb.OK;
    }

    /** Показывает ли FTB Library свою панель на этом экране (пока её не убрали). */
    public static boolean ftbVisible(Screen screen) {
        if (!available()) return false;
        var remembered = ftbScreen;
        if (remembered != null && remembered.get() == screen) return ftbScreenVisible;
        boolean result;
        try {
            result = (boolean) Ftb.visible.invoke(null, screen);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
        ftbScreen = new java.lang.ref.WeakReference<>(screen);
        ftbScreenVisible = result;
        return result;
    }

    /** Заменяет ли панель семьи кнопки FTB на этом экране. */
    public static boolean replaces(Screen screen) {
        Host current = host;
        if (current == null || !(screen instanceof AbstractContainerScreen<?>)) return false;
        return current.hosts(screen) && current.replacesFtb(screen) && ftbVisible(screen);
    }

    /** Ставит ли панель хозяин на этом экране. */
    public static boolean hosted(Screen screen) {
        Host current = host;
        return current != null && current.hosts(screen);
    }

    /**
     * Рисует ли кор панель сам на этом экране: экран инвентаря, хозяина нет (или он здесь не рисует),
     * FTB свою панель не показывает, а в реестре есть кнопки.
     */
    public static boolean standalone(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>) || hosted(screen) || ftbVisible(screen)) return false;
        for (DockButton b : DockButtons.all()) if (b.visible().getAsBoolean()) return true;
        return false;
    }

    public static boolean isFtbWidget(Object widget) {
        return available() && Ftb.widget.isInstance(widget);
    }

    /**
     * Виджет FTB убран с экрана. FTB продолжает сообщать JEI последнюю занятую им область,
     * а панели обходят чужие области, поэтому её нужно обнулить.
     */
    public static void widgetRemoved() {
        if (!available()) return;
        try {
            Ftb.lastArea.set(null, new Rect2i(0, 0, 0, 0));
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Область, которую FTB сообщает JEI для своей панели (в единицах GUI игры): когда панель наша, обходить её незачем. */
    public static boolean isFtbArea(Screen screen, int[] area) {
        if (!replaces(screen)) return false;
        try {
            Rect2i own = (Rect2i) Ftb.lastArea.get(null);
            return own != null && own.getX() == area[0] && own.getY() == area[1] && own.getWidth() == area[2] && own.getHeight() == area[3];
        } catch (Throwable t) {
            return false;
        }
    }

    private static void fail(Throwable t) {
        broken = true;
        visible = false;
        RYSCore.LOG.warn("FTB buttons on the family panel disabled: {}", t.toString());
    }

    private static void refresh(boolean withFtb) {
        long now = System.currentTimeMillis();
        if (now - refreshedAt < REFRESH_MS && refreshedVersion == DockButtons.version() && refreshedWithFtb == withFtb) return;
        refreshedAt = now;
        refreshedVersion = DockButtons.version();
        refreshedWithFtb = withFtb;
        List<Entry> out = new ArrayList<>();
        for (DockButton b : DockButtons.all()) {
            if (b.visible().getAsBoolean()) out.add(new Entry(b.id(), b, null, b.texture(), null, b.tile()));
        }
        if (withFtb && available()) {
            try {
                List<Entry> ftb = new ArrayList<>();
                for (Object guiButton : (List<?>) Ftb.enabled.invoke(Ftb.manager, false)) {
                    Object button = Ftb.sidebarButton.invoke(guiButton);
                    ResourceLocation id = (ResourceLocation) Ftb.id.invoke(button);
                    String key = id.getNamespace() + "_" + id.getPath().replace('/', '_');
                    if (DockButtons.ftbHidden(key)) continue;
                    DockButtons.FtbStyle style = DockButtons.ftbStyle(key);
                    ResourceLocation texture = style != null ? style.texture()
                        : TILES.containsKey(key) ? ResourceLocation.fromNamespaceAndPath(RYSCore.MOD_ID, "textures/gui/dock/" + key + ".png") : null;
                    int tile = style != null ? style.tile() : TILES.getOrDefault(key, 0);
                    ftb.add(new Entry(key, null, button, texture, Ftb.icon.invoke(Ftb.data.invoke(button)), tile));
                }
                // сортировка устойчивая: кнопки других модов идут перед настройками в порядке FTB
                ftb.sort(Comparator.comparingInt(e -> rank(e.key())));
                out.addAll(ftb);
            } catch (Throwable t) {
                fail(t);
            }
        }
        entries = out;
    }

    private static int rank(String key) {
        DockButtons.FtbStyle style = DockButtons.ftbStyle(key);
        if (style != null) return style.order();
        int index = ORDER.indexOf(key);
        return 1000 + (index >= 0 ? index : ORDER.indexOf("ftblibrary_toggle_day"));
    }

    /**
     * Раскладка хозяина: панель в верхней части колонки (outerLeft, outerTop, ширина outerWidth), кнопок в ряду сколько влезет.
     * Если места нет или показывать нечего, панель скрывается.
     *
     * @return верхняя граница, оставшаяся для других панелей колонки
     */
    public static int layout(Screen screen, int outerLeft, int outerWidth, int outerTop, int outerBottom) {
        visible = false;
        if (!hosted(screen) || !(screen instanceof AbstractContainerScreen<?>)) return outerTop;
        boolean withFtb = replaces(screen);
        // FTB рисует свою панель, а наша её не заменяет: кнопки модов стоят там, вторая панель не нужна
        if (!withFtb && ftbVisible(screen)) return outerTop;
        refresh(withFtb);
        if (entries.isEmpty()) return outerTop;
        int left = (int) Math.floor(outerLeft / SCALE), top = (int) Math.ceil(outerTop / SCALE);
        int bottom = (int) Math.floor(outerBottom / SCALE);
        int width = (int) Math.floor(outerWidth / SCALE);
        if (width < BUTTON + PAD * 2) return outerTop;
        cols = Math.max(1, (width - PAD * 2 + GAP) / (BUTTON + GAP));
        int rows = (entries.size() + cols - 1) / cols;
        h = rows * BUTTON + (rows - 1) * GAP + PAD * 2;
        if (h > bottom - top) return outerTop;
        x = left;
        w = width;
        y = top;
        gridX = x + (w - (cols * BUTTON + (cols - 1) * GAP)) / 2;
        gridY = y + PAD;
        visible = true;
        return (int) Math.ceil((y + h) * SCALE) + 4;
    }

    /**
     * Раскладка без хозяина (её делает сам кор): два столбца слева от окна (guiLeft, guiTop в единицах интерфейса),
     * верх вровень с окном. Если слева нет места, панель скрывается.
     */
    public static void layoutBeside(Screen screen, int guiLeft, int guiTop) {
        visible = false;
        if (!standalone(screen)) return;
        refresh(false);
        if (entries.isEmpty()) return;
        cols = Math.min(2, entries.size());
        int rows = (entries.size() + cols - 1) / cols;
        w = cols * BUTTON + (cols - 1) * GAP + PAD * 2;
        h = rows * BUTTON + (rows - 1) * GAP + PAD * 2;
        int right = (int) Math.floor((guiLeft - 5) / SCALE);
        x = right - w;
        y = (int) Math.ceil((guiTop + 2) / SCALE);
        if (x < (int) Math.ceil(4 / SCALE)) return;
        gridX = x + PAD;
        gridY = y + PAD;
        visible = true;
    }

    private static int buttonX(int index) {
        return gridX + (index % cols) * (BUTTON + GAP);
    }

    private static int buttonY(int index) {
        return gridY + (index / cols) * (BUTTON + GAP);
    }

    public static boolean contains(double mx, double my) {
        mx /= SCALE;
        my /= SCALE;
        return visible && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int indexAt(double mx, double my) {
        if (!contains(mx, my)) return -1;
        mx /= SCALE;
        my /= SCALE;
        for (int i = 0; i < entries.size(); i++) {
            int bx = buttonX(i), by = buttonY(i);
            if (mx >= bx && mx < bx + BUTTON && my >= by && my < by + BUTTON) return i;
        }
        return -1;
    }

    public static void render(GuiGraphics g, Theme theme, int mx, int my) {
        if (!visible) return;
        int hover = indexAt(mx, my);
        g.pose().pushPose();
        g.pose().scale(SCALE, SCALE, 1f);
        try {
            renderButtons(g, theme, hover);
        } finally {
            g.pose().popPose();
        }
    }

    private static void renderButtons(GuiGraphics g, Theme theme, int hover) {
        theme.panel(g, x, y, w, h);
        Minecraft mc = Minecraft.getInstance();
        float radius = theme.beveled() ? 1f : 5f;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int bx = buttonX(i), by = buttonY(i);
            boolean hot = i == hover;
            try {
                boolean own = entry.texture() != null || (entry.own() != null && entry.own().shape() != null);
                if (own) {
                    int tile = entry.tile() != 0 ? entry.tile() : 0xFF6B7488;
                    Draw.round(g, bx, by, BUTTON, BUTTON, radius, hot && theme.beveled() ? Draw.mix(tile, 0xFFFFFFFF, 0.22f) : tile);
                    if (entry.texture() != null) {
                        drawTinted(g, entry.texture(), bx + (BUTTON - ICON) / 2, by + (BUTTON - ICON) / 2, glyphOn(tile));
                    } else {
                        Icons.draw(g, entry.own().shape(), bx + (BUTTON - SHAPE) / 2f, by + (BUTTON - SHAPE) / 2f, SHAPE, glyphOn(tile));
                    }
                } else {
                    // кнопка без своего значка: значок автора на нейтральной плитке
                    Draw.round(g, bx, by, BUTTON, BUTTON, radius, hot && theme.beveled() ? theme.c("slot.hover") : theme.c("slot.bg"));
                    if (entry.ftbIcon() != null) {
                        // значок FTB рисуется через Tesselator мимо GuiGraphics: плитка из пачки должна лечь раньше него.
                        // Сброс до enableBlend: он сам выключает смешивание
                        Batch.flush();
                        RenderSystem.enableBlend();
                        Ftb.drawIcon.invoke(entry.ftbIcon(), g, bx + 1, by + 1, 16, 16);
                    }
                }
                if (hot && !theme.beveled()) {
                    // кольцо выбранной плитки, как в макете: зазор цвета панели и светлая обводка
                    Draw.roundBorder(g, bx - 1.75f, by - 1.75f, BUTTON + 3.5f, BUTTON + 3.5f, radius + 1.75f, theme.c("text.primary"), 0.75f);
                }
                if (entry.ftb() != null) {
                    // значки поверх кнопки: например, счётчик непрочитанных квестов
                    Collection<?> overlays = (Collection<?>) Ftb.overlays.invoke(entry.ftb());
                    if (!overlays.isEmpty()) {
                        // наложения FTB тоже могут рисовать мимо GuiGraphics
                        Batch.flush();
                        g.pose().pushPose();
                        g.pose().translate(bx + 1, by + 1, 10);
                        for (Object overlay : overlays) Ftb.renderOverlay.invoke(overlay, g, mc.font, 16);
                        g.pose().popPose();
                    }
                }
            } catch (Throwable t) {
                fail(t);
                return;
            }
        }
    }

    /** На светлых плитках значок тёмный, на тёмных белый. */
    private static int glyphOn(int tile) {
        double luminance = (0.299 * ((tile >> 16) & 255) + 0.587 * ((tile >> 8) & 255) + 0.114 * (tile & 255)) / 255;
        return luminance > 0.62 ? 0xFF1C1F27 : 0xFFFFFFFF;
    }

    private static void drawTinted(GuiGraphics g, ResourceLocation texture, int ix, int iy, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1f);
        g.blit(texture, ix, iy, ICON, ICON, 0f, 0f, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE, ICON_TEXTURE);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Подсказка кнопки под курсором в стиле темы: подпись, клавиша, вторая строка. */
    public static void renderTooltip(GuiGraphics g, int mx, int my) {
        renderTooltip(g, ThemeManager.get(), mx, my);
    }

    @SuppressWarnings("unchecked")
    public static void renderTooltip(GuiGraphics g, Theme t, int mx, int my) {
        int hover = indexAt(mx, my);
        if (hover < 0) return;
        Entry entry = entries.get(hover);
        String title;
        String keyLabel = null;
        List<String> rest = new ArrayList<>();
        try {
            if (entry.own() != null) {
                DockButton b = entry.own();
                title = b.label().get().getString();
                KeyMapping key = b.key();
                if (key != null && !key.isUnbound()) keyLabel = key.getTranslatedKeyMessage().getString();
                if (b.description() != null) {
                    String d = b.description().get().getString();
                    if (!d.isEmpty()) rest.add(d);
                }
            } else {
                List<Component> lines = (List<Component>) Ftb.tooltip.invoke(entry.ftb(), Screen.hasShiftDown());
                if (lines.isEmpty()) return;
                title = lines.get(0).getString();
                for (int i = 1; i < lines.size(); i++) rest.add(lines.get(i).getString());
            }
        } catch (Throwable e) {
            fail(e);
            return;
        }
        float titlePx = 11.5f, smallPx = 11f, kbdPx = 11f;
        float titleW = Draw.labelWidth(title, titlePx, Weight.MEDIUM);
        float kbdW = keyLabel == null ? 0 : Draw.labelWidth(keyLabel, kbdPx, Weight.REGULAR) + 5;
        float textW = titleW + (kbdW > 0 ? 3 + kbdW : 0);
        for (String line : rest) textW = Math.max(textW, Draw.labelWidth(line, smallPx, Weight.REGULAR));
        int tw = (int) Math.ceil(textW) + 9, th = 5 + 8 + rest.size() * 8;
        // над панелью у её левого края, как в макете; если сверху тесно, под ней
        int px = (int) Math.floor(x * SCALE), py = (int) Math.floor(y * SCALE), pb = (int) Math.ceil((y + h) * SCALE);
        int tx = Math.max(4, Math.min(px, g.guiWidth() - 4 - tw));
        int ty = py - 2 - th;
        if (ty < 4) ty = pb + 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        if (t.beveled()) {
            t.tooltipBox(g, tx, ty, tw, th);
        } else {
            if (t.shadow) Draw.shadow(g, tx, ty, tw, th, 4f, 0.5f);
            Draw.roundBox(g, tx, ty, tw, th, 4f, t.c("sidebar.bg"), t.c("button.border"));
        }
        float cy = ty + 2.5f + 4f;
        Draw.label(g, title, tx + 4.5f, cy, titlePx, Weight.MEDIUM, t.beveled() ? t.c("tooltip.fg") : t.c("text.primary"));
        if (keyLabel != null) {
            float kx = tx + 4.5f + titleW + 3;
            Draw.roundBox(g, kx, cy - 4, kbdW, 8, t.beveled() ? 0 : 2.5f, t.c("button.bg"), t.c("button.border"));
            Draw.label(g, keyLabel, kx + 2.5f, cy, kbdPx, Weight.REGULAR, t.c("text.muted"));
        }
        for (String line : rest) {
            cy += 8;
            Draw.label(g, line, tx + 4.5f, cy, smallPx, Weight.REGULAR, t.c("text.muted"));
        }
        g.pose().popPose();
    }

    /** @return true, если клик пришёлся по кнопке и она сработала. */
    public static boolean click(double mx, double my, int button) {
        int index = indexAt(mx, my);
        if (index < 0 || button != 0) return false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        Entry entry = entries.get(index);
        try {
            if (entry.own() != null) entry.own().action().run();
            else Ftb.click.invoke(entry.ftb(), Screen.hasShiftDown());
        } catch (Throwable t) {
            RYSCore.LOG.warn("Panel button {} failed: {}", entry.key(), t.toString());
        }
        return true;
    }

    // --- для автотестов ---

    public static boolean visible() {
        return visible;
    }

    public static List<String> keys() {
        return entries.stream().map(Entry::key).toList();
    }

    /** Центр кнопки в единицах интерфейса или null, если её нет на панели. */
    public static int @Nullable [] center(String key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key().equals(key)) {
                return new int[] {Math.round((buttonX(i) + BUTTON / 2f) * SCALE), Math.round((buttonY(i) + BUTTON / 2f) * SCALE)};
            }
        }
        return null;
    }
}
