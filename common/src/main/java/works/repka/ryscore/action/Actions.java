package works.repka.ryscore.action;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.RYSCore;
import works.repka.ryscore.dock.DockButton;
import works.repka.ryscore.dock.DockButtons;
import works.repka.ryscore.perm.Roles;
import works.repka.ryscore.ui.Icons;
import works.repka.ryscore.ui.Notice;

/**
 * Действия модов семьи (1.14, план Essentials 3.1): реестр, кольца колеса, свои клавиши игрока. Только клиент, главный поток.
 *
 * <ul>
 *   <li>{@link #register} — действие мода; {@link #provider} — меняющийся список (каждое место сервера, каждый дом),
 *   на любое из них можно повесить клавишу;</li>
 *   <li>кнопки панели семьи ({@link DockButtons}) сами становятся действиями группы «Окна» с id
 *   {@code ryscore:dock/<id кнопки>}, если мод не закрыл их своим действием ({@link #coversDock});</li>
 *   <li>колесо: клавиша {@link #WHEEL} (Ё), кольца {@link Ring}, сектора выбирает игрок в окне «Клавиши» ({@link #openKeys});</li>
 *   <li>свои клавиши работают только в мире без открытого окна и только при точном совпадении Ctrl, Shift, Alt.</li>
 * </ul>
 */
public final class Actions {
    public static final String CATEGORY = "key.categories.ryscore";
    /** Клавиша колеса: Ё ({@code `}), в RepYS свободна (вопрос 3 плана, проверено по options.txt 19.11). */
    public static final KeyMapping WHEEL = new KeyMapping("key.ryscore.wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY);
    /**
     * Сектор-круг ядра «Перенести…» (1.15): все способы перенести себя, кроме дома и «назад», одним сектором. Появляется,
     * когда есть хоть одно из его действий; мод добавляет своё через {@code suggest(TRAVEL, id)}.
     */
    public static final ResourceLocation TRAVEL = ResourceLocation.fromNamespaceAndPath(RYSCore.MOD_ID, "travel");
    private static final List<ResourceLocation> TRAVEL_CHILDREN = List.of(rl("rysmap:spawn"), rl("rysmap:places"), rl("rysmap:team_home"),
        rl("rysmap:players"), rl("rysmap:bed"), rl("rysmap:rtp"));

    private static final Map<ResourceLocation, Action> REGISTERED = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Supplier<? extends Collection<Action>>> PROVIDERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Ring> RINGS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, List<ResourceLocation>> SUGGESTED = new LinkedHashMap<>();
    private static final Set<String> COVERED_DOCK = new HashSet<>();
    private static Function<String, String> modNames = ns -> ns;
    private static ToIntFunction<KeyMapping> keyMods = k -> 0;
    private static BiPredicate<KeyMapping, KeyMapping> contextClash = (a, b) -> true;
    private static @Nullable ActionConfig config;
    private static @Nullable Path configFile;

    static {
        ring(Ring.builder(Ring.MINE).label(() -> Component.translatable("ryscore.ring.mine")).order(0)
            .defaults("rysmap:home", "rysmap:back", "ryscore:travel", "rysteams:ping", "rysmap:new_point", "rysclaims:claim_here",
                "ryshelm:ender_chest", "ryshelm:trash")
            .build());
        ring(Ring.builder(Ring.MODERATION).label(() -> Component.translatable("ryscore.ring.moderation")).order(10)
            .available(() -> Roles.atLeastClient(Roles.MODERATOR))
            .defaults("ryshelm:players", "ryshelm:reports", "ryshelm:staff_chat", "ryshelm:spectate", "ryshelm:spectate_return",
                "ryshelm:journal", "rysclaims:bypass", "ryshelm:open")
            .build());
        ring(Ring.builder(Ring.OPERATOR).label(() -> Component.translatable("ryscore.ring.operator")).order(20)
            .available(() -> Roles.atLeastClient(Roles.OPERATOR))
            .defaults("ryshelm:fly", "ryshelm:god", "ryshelm:heal", "ryshelm:feed", "ryshelm:speed", "ryshelm:invsee",
                "ryshelm:vanish", "ryshelm:open")
            .build());
    }

    private Actions() {}

    private static ResourceLocation rl(String s) {
        return ResourceLocation.parse(s);
    }

    // --- реестр ---

    /** Регистрирует действие; то же id заменяет прежнее. */
    public static void register(Action action) {
        REGISTERED.put(action.id(), action);
        changed();
    }

    public static void unregister(ResourceLocation id) {
        REGISTERED.remove(id);
        PROVIDERS.remove(id);
        changed();
    }

    /** Поставщик меняющегося списка действий: опрашивается при открытии колеса, окна и при нажатии своей клавиши. */
    public static void provider(ResourceLocation id, Supplier<? extends Collection<Action>> provider) {
        PROVIDERS.put(id, provider);
        changed();
    }

    /** Своё кольцо мода (или замена кольца ядра с тем же id). */
    public static void ring(Ring ring) {
        RINGS.put(ring.id(), ring);
    }

    /** Подсказка умолчания: действие встаёт в первый свободный сектор кольца после умолчаний кольца. */
    public static void suggest(ResourceLocation ring, ResourceLocation action) {
        List<ResourceLocation> list = SUGGESTED.computeIfAbsent(ring, k -> new ArrayList<>());
        if (!list.contains(action)) list.add(action);
    }

    /** Мод закрыл кнопку панели семьи своим действием: кнопка не дублируется в «Окнах». */
    public static void coversDock(String dockButtonId) {
        COVERED_DOCK.add(dockButtonId);
    }

    private static List<Action> cache = List.of();
    private static long cacheAt;
    /** Список действий живёт четверть секунды: колесо и окно спрашивают его много раз за кадр. */
    private static final long CACHE_NS = 250_000_000L;

    /** Все действия: зарегистрированные, от поставщиков и кнопки панели, по группам и порядку. */
    public static List<Action> all() {
        long now = System.nanoTime();
        if (cacheAt != 0 && now - cacheAt < CACHE_NS) return cache;
        cache = List.copyOf(collect());
        cacheAt = now;
        return cache;
    }

    /** Сбросить запомненный список: мод поменял действия и хочет, чтобы колесо увидело это сразу. */
    public static void changed() {
        cacheAt = 0;
    }

    private static List<Action> collect() {
        Map<ResourceLocation, Action> out = new LinkedHashMap<>(REGISTERED);
        for (var p : PROVIDERS.values()) {
            try {
                Collection<Action> list = p.get();
                if (list != null) for (Action a : list) out.putIfAbsent(a.id(), a);
            } catch (RuntimeException e) {
                RYSCore.LOG.warn("Поставщик действий упал: {}", e.toString());
            }
        }
        for (Action a : dockActions()) out.putIfAbsent(a.id(), a);
        Action travel = travel(out);
        if (travel != null) out.putIfAbsent(TRAVEL, travel);
        List<Action> list = new ArrayList<>(out.values());
        list.sort(Comparator.comparingInt((Action a) -> a.group().order()).thenComparing(a -> a.group().id())
            .thenComparingInt(Action::order));
        return list;
    }

    /** Действие по id: в реестре, у поставщиков, в списках действий («Места сервера…» → место «oil») и среди кнопок панели. */
    public static @Nullable Action get(ResourceLocation id) {
        Action a = REGISTERED.get(id);
        if (a != null) return a;
        List<Action> all = all();
        for (Action x : all) if (x.id().equals(id)) return x;
        for (Action x : all) {
            if (x.opensList()) for (Action child : x.list()) if (child.id().equals(id)) return child;
            if (x.opensNested()) for (Action child : x.nested()) if (child.id().equals(id)) return child;
        }
        return null;
    }

    /** «Перенести…» из тех действий, что есть; null, если ни одного. */
    private static @Nullable Action travel(Map<ResourceLocation, Action> have) {
        List<ResourceLocation> ids = new ArrayList<>(TRAVEL_CHILDREN);
        for (ResourceLocation id : SUGGESTED.getOrDefault(TRAVEL, List.of())) if (!ids.contains(id)) ids.add(id);
        List<Action> children = new ArrayList<>();
        for (ResourceLocation id : ids) {
            Action a = have.get(id);
            if (a != null && children.size() < Ring.SECTORS) children.add(a);
        }
        if (children.isEmpty()) return null;
        List<Action> fixed = List.copyOf(children);
        return Action.builder(TRAVEL).label(() -> Component.translatable("ryscore.action.travel")).icon(Icons.Shape.TELEPORT)
            .group(Action.Group.PLACES).order(-1).detail(() -> Component.translatable("ryscore.action.travel.detail", fixed.size()).getString())
            .nested(() -> fixed).build();
    }

    private static List<Action> dockActions() {
        List<Action> out = new ArrayList<>();
        for (DockButton b : DockButtons.all()) {
            if (COVERED_DOCK.contains(b.id())) continue;
            ResourceLocation id = ResourceLocation.tryBuild(RYSCore.MOD_ID, "dock/" + b.id().toLowerCase(java.util.Locale.ROOT));
            if (id == null) continue;
            Action.Builder builder = Action.builder(id).label(b.label()).group(Action.Group.WINDOWS).order(b.order()).run(b.action())
                .state(() -> b.visible().getAsBoolean() ? Action.State.ready() : Action.State.off(""));
            if (b.description() != null) builder.detail(() -> b.description().get().getString());
            if (b.texture() != null) builder.texture(b.texture());
            else builder.icon(b.shape() != null ? b.shape() : Icons.Shape.DOTS);
            out.add(builder.build());
        }
        return out;
    }

    // --- кольца ---

    /** Все кольца по порядку. */
    public static List<Ring> rings() {
        List<Ring> list = new ArrayList<>(RINGS.values());
        list.sort(Comparator.comparingInt(Ring::order));
        return list;
    }

    /** Кольца, которые игрок видит сейчас: доступные и не пустые. «Моё» видно всегда. */
    public static List<Ring> availableRings() {
        List<Ring> out = new ArrayList<>();
        for (Ring r : rings()) {
            if (!r.available()) continue;
            if (!r.id().equals(Ring.MINE) && slots(r.id()).stream().allMatch(java.util.Objects::isNull)) continue;
            out.add(r);
        }
        return out;
    }

    public static @Nullable Ring ringById(ResourceLocation id) {
        return RINGS.get(id);
    }

    /** Восемь секторов кольца: действие или null (пусто, мода нет). */
    public static List<@Nullable Action> slots(ResourceLocation ring) {
        Ring r = RINGS.get(ring);
        Map<ResourceLocation, Action> byId = new LinkedHashMap<>();
        List<Action> all = all();
        for (Action a : all) byId.put(a.id(), a);
        List<ResourceLocation> fallback = new ArrayList<>();
        if (ring.equals(Ring.MINE)) for (Action a : all) if (a.group().equals(Action.Group.WINDOWS)) fallback.add(a.id());
        List<String> saved = config().ring(ring.toString());
        // дети списков (место «oil» из «Мест сервера…») ищутся, только если игрок положил такое в кольцо
        if (saved != null) {
            for (String s : saved) {
                ResourceLocation id = s.isEmpty() ? null : ResourceLocation.tryParse(s);
                if (id == null || byId.containsKey(id)) continue;
                Action child = get(id);
                if (child != null) byId.put(id, child);
            }
        }
        List<ResourceLocation> ids = Ring.layout(saved, r == null ? List.of() : r.defaults(),
            SUGGESTED.getOrDefault(ring, List.of()), fallback, byId::containsKey);
        List<Action> out = new ArrayList<>(Ring.SECTORS);
        for (ResourceLocation id : ids) out.add(id == null ? null : byId.get(id));
        return out;
    }

    /** Кладёт действие в сектор (null очищает). Остальные сектора кольца фиксируются как есть. */
    public static void setSlot(ResourceLocation ring, int index, @Nullable ResourceLocation action) {
        if (index < 0 || index >= Ring.SECTORS) return;
        List<String> now = new ArrayList<>();
        for (Action a : slots(ring)) now.add(a == null ? "" : a.id().toString());
        String value = action == null ? "" : action.toString();
        // одно действие в кольце один раз: прежний сектор освобождается
        if (!value.isEmpty()) for (int i = 0; i < now.size(); i++) if (now.get(i).equals(value)) now.set(i, "");
        now.set(index, value);
        config().rings.put(ring.toString(), now);
        save();
    }

    /** Сбрасывает кольцо к умолчаниям. */
    public static void resetRing(ResourceLocation ring) {
        config().rings.remove(ring.toString());
        save();
    }

    /** Номер сектора действия в кольце или −1. */
    public static int slotOf(ResourceLocation ring, ResourceLocation action) {
        List<Action> s = slots(ring);
        for (int i = 0; i < s.size(); i++) if (s.get(i) != null && s.get(i).id().equals(action)) return i;
        return -1;
    }

    /** Колесо в режиме «Держать и отпустить» (короткое нажатие оставляет его открытым) или «Только клик». */
    public static boolean holdMode() {
        return !ActionConfig.CLICK.equals(config().wheelMode);
    }

    public static void setHoldMode(boolean hold) {
        config().wheelMode = hold ? ActionConfig.HOLD : ActionConfig.CLICK;
        save();
    }

    static ResourceLocation lastRing() {
        ResourceLocation id = ResourceLocation.tryParse(config().lastRing);
        return id == null ? Ring.MINE : id;
    }

    static void setLastRing(ResourceLocation id) {
        config().lastRing = id.toString();
    }

    // --- свои клавиши ---

    public static @Nullable Binding binding(ResourceLocation action) {
        return Binding.parse(config().bindings.get(action.toString()));
    }

    public static void bind(ResourceLocation action, @Nullable Binding binding) {
        if (binding == null) config().bindings.remove(action.toString());
        else config().bindings.put(action.toString(), binding.serialize());
        save();
    }

    /** Все свои клавиши игрока: id действия → сочетание. */
    public static Map<ResourceLocation, Binding> bindings() {
        Map<ResourceLocation, Binding> out = new LinkedHashMap<>();
        for (var e : config().bindings.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            Binding b = Binding.parse(e.getValue());
            if (id != null && b != null) out.put(id, b);
        }
        return out;
    }

    /**
     * Кто ещё держит это сочетание: клавиши игры и модов ({@code options.keyMappings}) и другие действия.
     * Клавиша игры с модификатором NeoForge спорит только с тем же модификатором; без него — только с нажатием без модификаторов.
     * Строки готовы для игрока: «Mekanism (подсказки HUD)».
     */
    public static List<String> conflicts(Binding binding, @Nullable ResourceLocation self) {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping k : mc.options.keyMappings) {
            if (k == WHEEL && binding.key().equals(Binding.keyOf(k)) && !binding.modified()) {
                out.add(Component.translatable(k.getName()).getString());
                continue;
            }
            if (k.isUnbound() || !Binding.keyOf(k).equals(binding.key())) continue;
            if (keyMods.applyAsInt(k) != binding.mods()) continue;
            // своё сочетание работает только в мире: клавиша игры только для окон с ним не спорит
            if (!contextClash.test(WHEEL, k)) continue;
            out.add(describe(k));
        }
        for (var e : bindings().entrySet()) {
            if (e.getKey().equals(self) || !e.getValue().equals(binding)) continue;
            Action a = get(e.getKey());
            out.add(a == null ? e.getKey().toString() : a.label().getString());
        }
        return out;
    }

    /**
     * Конфликт нашей клавиши (1.15): клавиша мода семьи RYS или своё сочетание игрока, на которой сидят чужие действия
     * с пересекающимся контекстом NeoForge. Одно нажатие идёт всем, поэтому окно «Клавиши» предлагает снять нашу.
     */
    public static final class Clash {
        private final @Nullable KeyMapping mapping;
        private final @Nullable ResourceLocation action;
        private final String label, key;
        private final List<String> others;

        Clash(@Nullable KeyMapping mapping, @Nullable ResourceLocation action, String label, String key, List<String> others) {
            this.mapping = mapping;
            this.action = action;
            this.label = label;
            this.key = key;
            this.others = List.copyOf(others);
        }

        /** Наше действие: «Карта», «Колесо быстрых действий». */
        public String label() {
            return label;
        }

        /** Клавиша: «V», «Alt+1». */
        public String key() {
            return key;
        }

        /** Чужие на той же клавише: «Mekanism (режим шлема)». */
        public List<String> others() {
            return others;
        }

        /** Снять нашу: клавиша мода становится «не назначена», своё сочетание убирается. */
        public void unbind() {
            if (mapping != null) {
                Minecraft mc = Minecraft.getInstance();
                mc.options.setKey(mapping, InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
                mc.options.save();
            } else if (action != null) {
                bind(action, null);
            }
        }
    }

    /** Клавиша мода семьи: имя начинается с {@code key.rys} (ryscore, rysmap, rysteams, rysquests, ryscope, rysclaims, ryshelm). */
    static boolean family(KeyMapping k) {
        return k.getName().startsWith("key.rys");
    }

    /** Все конфликты наших клавиш с чужими (1.15): клавиши модов семьи и свои сочетания игрока. */
    public static List<Clash> clashes() {
        List<Clash> out = new ArrayList<>();
        KeyMapping[] all = Minecraft.getInstance().options.keyMappings;
        Map<InputConstants.Key, List<KeyMapping>> byKey = new LinkedHashMap<>();
        for (KeyMapping k : all) if (!k.isUnbound()) byKey.computeIfAbsent(Binding.keyOf(k), x -> new ArrayList<>()).add(k);
        for (KeyMapping ours : all) {
            if (!family(ours) || ours.isUnbound()) continue;
            InputConstants.Key key = Binding.keyOf(ours);
            int mods = keyMods.applyAsInt(ours);
            List<String> others = new ArrayList<>();
            for (KeyMapping k : byKey.getOrDefault(key, List.of())) {
                if (k == ours || family(k) || keyMods.applyAsInt(k) != mods || !contextClash.test(ours, k)) continue;
                others.add(describe(k));
            }
            if (others.isEmpty()) continue;
            String label = Binding.of(key, mods).label();
            out.add(new Clash(ours, null, Component.translatable(ours.getName()).getString(), label, others));
        }
        for (var e : bindings().entrySet()) {
            List<String> others = conflicts(e.getValue(), e.getKey());
            if (others.isEmpty()) continue;
            Action a = get(e.getKey());
            out.add(new Clash(null, e.getKey(), a == null ? e.getKey().toString() : a.label().getString(), e.getValue().label(), others));
        }
        return out;
    }

    private static String describe(KeyMapping k) {
        return Component.translatable(k.getCategory()).getString() + " (" + Component.translatable(k.getName()).getString() + ")";
    }

    /** Клавиши, которые сейчас ни за кем не закреплены: из спокойного набора (знаки, Insert, Home, F-клавиши, мышь 4 и 5). */
    public static List<InputConstants.Key> freeKeys() {
        String[] candidates = {"key.keyboard.grave.accent", "key.keyboard.semicolon", "key.keyboard.apostrophe", "key.keyboard.insert",
            "key.keyboard.home", "key.keyboard.end", "key.keyboard.page.up", "key.keyboard.page.down", "key.keyboard.f4", "key.keyboard.f6",
            "key.keyboard.f7", "key.keyboard.f8", "key.keyboard.f9", "key.keyboard.f10", "key.keyboard.f12", "key.mouse.4", "key.mouse.5"};
        Set<InputConstants.Key> used = new HashSet<>();
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (!k.isUnbound()) used.add(Binding.keyOf(k));
        for (Binding b : bindings().values()) used.add(b.key());
        List<InputConstants.Key> out = new ArrayList<>();
        for (String c : candidates) {
            InputConstants.Key key = InputConstants.getKey(c);
            if (!used.contains(key)) out.add(key);
        }
        return out;
    }

    /**
     * Нажатие клавиши или кнопки мыши в мире без открытого окна (зовёт загрузчик).
     * @return сработало ли хотя бы одно действие
     */
    public static boolean keyPressed(InputConstants.Key key, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return false;
        boolean any = false;
        for (var e : config().bindings.entrySet()) {
            Binding b = Binding.parse(e.getValue());
            if (b == null || !b.matches(key, mods)) continue;
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            Action a = id == null ? null : get(id);
            if (a == null) continue;
            run(a);
            any = true;
        }
        return any;
    }

    // --- выполнение ---

    /**
     * Выполняет действие так, как его выполняет колесо и своя клавиша: список открывает колесо со списком, на паузе
     * и недоступное отвечают одной строкой уведомления с причиной.
     */
    public static void run(Action action) {
        if (action.opensList()) {
            WheelScreen.openList(action);
            return;
        }
        if (action.opensNested()) {
            WheelScreen.openNested(action);
            return;
        }
        Action.State state = action.state();
        switch (state.kind()) {
            case PAUSE -> Notice.row(Notice.CORE, "action").icon(action.shape() != null ? action.shape() : Icons.Shape.CLOCK)
                .title(Component.translatable("ryscore.action.pause", action.label(), state.clock()).getString()).show();
            case OFF -> Notice.row(Notice.CORE, "action").icon(action.shape() != null ? action.shape() : Icons.Shape.LOCK)
                .title(state.reason().isEmpty() ? Component.translatable("ryscore.action.off", action.label()).getString() : state.reason())
                .bad().show();
            default -> action.perform();
        }
    }

    /** Открывает колесо (как клавиша колеса в режиме клика). */
    public static void openWheel() {
        WheelScreen.open(false);
    }

    /** Открывает окно «Клавиши»; закрытие возвращает в {@code parent}. Своей клавиши у окна нет. */
    public static void openKeys(@Nullable Screen parent) {
        Minecraft.getInstance().setScreen(new KeysScreen(parent));
    }

    /** Имя мода по пространству id: заголовок группы в окне клавиш («RYSMap»). */
    public static String modName(String namespace) {
        return modNames.apply(namespace);
    }

    // --- для загрузчика ---

    /** Имена модов по modid. Ставит загрузчик. */
    public static void modNames(Function<String, String> names) {
        modNames = names;
    }

    /** Модификатор клавиши игры по NeoForge ({@code KeyModifier}) во флагах GLFW. Ставит загрузчик. */
    public static void keyModifiers(ToIntFunction<KeyMapping> mods) {
        keyMods = mods;
    }

    /** Пересекаются ли контексты двух клавиш игры (NeoForge {@code KeyConflictContext}). Ставит загрузчик. С 1.15. */
    public static void keyContexts(BiPredicate<KeyMapping, KeyMapping> clash) {
        contextClash = clash;
    }

    /** Каждый тик клиента: клавиша колеса. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        while (WHEEL.consumeClick()) {
            if (mc.screen == null && mc.player != null) WheelScreen.open(holdMode());
        }
    }

    private static ActionConfig config() {
        if (config == null) {
            configFile = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("ryscore").resolve("keys.json");
            config = ActionConfig.load(configFile);
        }
        return config;
    }

    private static void save() {
        if (config != null && configFile != null) config.save(configFile);
    }
}
