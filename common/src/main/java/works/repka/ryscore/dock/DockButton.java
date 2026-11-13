package works.repka.ryscore.dock;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import works.repka.ryscore.ui.Icons;

/**
 * Кнопка мода семьи на панели кнопок в инвентаре: значок на цветной плитке, подпись и клавиша в подсказке, действие.
 * Собирается через {@link #builder(String)} и регистрируется в {@link DockButtons}.
 *
 * @param id          ключ кнопки; принято {@code <modid>_<имя>}, как ключи панели FTB ({@code rysquests_book})
 * @param label       подпись: первая строка подсказки
 * @param description вторая строка подсказки, может меняться («Квестов пока нет…»); null — без неё
 * @param tile        цвет плитки ARGB; значок на ней белый или тёмный по яркости плитки
 * @param shape       значок из набора кора; если задана {@code texture}, рисуется она
 * @param texture     белая текстура значка 42×42 (окрашивается), например {@code modid:textures/gui/dock/book.png}
 * @param key         клавиша, которую показать в подсказке; null — без неё
 * @param action      что делает нажатие
 * @param order       место на панели: меньше — раньше; кнопки семьи стоят перед кнопками FTB
 * @param replacesFtb ключи кнопок FTB, которые эта кнопка заменяет: на панели семьи они не показываются
 * @param visible     показывать ли кнопку сейчас
 */
public record DockButton(String id, Supplier<Component> label, @Nullable Supplier<Component> description, int tile,
                         @Nullable Icons.Shape shape, @Nullable ResourceLocation texture, @Nullable KeyMapping key,
                         Runnable action, int order, Set<String> replacesFtb, BooleanSupplier visible) {

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private Supplier<Component> label;
        @Nullable
        private Supplier<Component> description;
        private int tile = 0xFF6B7488;
        @Nullable
        private Icons.Shape shape;
        @Nullable
        private ResourceLocation texture;
        @Nullable
        private KeyMapping key;
        private Runnable action = () -> {};
        private int order = 100;
        private final Set<String> replaces = new LinkedHashSet<>();
        private BooleanSupplier visible = () -> true;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id);
            this.label = () -> Component.literal(id);
        }

        public Builder label(Component label) {
            this.label = () -> label;
            return this;
        }

        public Builder label(Supplier<Component> label) {
            this.label = label;
            return this;
        }

        public Builder description(@Nullable Supplier<Component> description) {
            this.description = description;
            return this;
        }

        /** Цвет плитки ARGB, например {@code 0xFFE9A83A} у квестов, {@code 0xFF4C8DF5} у команды. */
        public Builder tile(int argb) {
            this.tile = argb;
            return this;
        }

        public Builder icon(Icons.Shape shape) {
            this.shape = shape;
            return this;
        }

        /** Белая текстура значка 42×42, окрашивается под плитку. Важнее {@link #icon}. */
        public Builder texture(ResourceLocation texture) {
            this.texture = texture;
            return this;
        }

        public Builder key(@Nullable KeyMapping key) {
            this.key = key;
            return this;
        }

        public Builder action(Runnable action) {
            this.action = Objects.requireNonNull(action);
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /** Кнопка FTB с этим ключом ({@code ftbquests_quests}) на панели семьи не показывается. */
        public Builder replacesFtb(String... keys) {
            this.replaces.addAll(Set.of(keys));
            return this;
        }

        public Builder visible(BooleanSupplier visible) {
            this.visible = Objects.requireNonNull(visible);
            return this;
        }

        public DockButton build() {
            return new DockButton(id, label, description, tile, shape, texture, key, action, order, Set.copyOf(replaces), visible);
        }

        /** Собрать и зарегистрировать в {@link DockButtons}. */
        public DockButton register() {
            DockButton button = build();
            DockButtons.register(button);
            return button;
        }
    }
}
