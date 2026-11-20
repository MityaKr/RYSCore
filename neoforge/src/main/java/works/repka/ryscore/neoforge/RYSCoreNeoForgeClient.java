package works.repka.ryscore.neoforge;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import works.repka.ryscore.RYSCore;
import works.repka.ryscore.action.Actions;
import works.repka.ryscore.action.Answers;
import works.repka.ryscore.action.Binding;
import works.repka.ryscore.action.KeyDefaults;
import works.repka.ryscore.dock.DockButton;
import works.repka.ryscore.ui.Icons;
import net.minecraft.network.chat.Component;
import works.repka.ryscore.dock.Dock;
import works.repka.ryscore.ui.Notices;
import works.repka.ryscore.ui.UiScale;
import works.repka.ryscore.scene.SceneHooks;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Batch;

/** Клиентская часть кора: темы перечитываются вместе с ресурсами, модам семьи делать ничего не нужно. */
@Mod(value = RYSCore.MOD_ID, dist = Dist.CLIENT)
public final class RYSCoreNeoForgeClient {
    private static final String KEYS_TILE = "ryscore_keys";

    public RYSCoreNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((ResourceManagerReloadListener) ThemeManager::reload));
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((ResourceManagerReloadListener) resources -> SceneHooks.reloaded()));
        SceneHooks.setMesher(RYSCoreNeoForgeClient::mesh);
        modBus.addListener(RYSCoreNeoForgeClient::registerShaders);
        dockHooks();
        noticeHooks();
        actionHooks(modBus);
    }

    /**
     * Действия семьи (1.14): клавиши колеса, «Принять» и «Отказать» (только в мире), свои сочетания игрока на любое действие,
     * имена модов для окна клавиш и модификаторы NeoForge у клавиш игры для проверки конфликтов.
     */
    private static void actionHooks(IEventBus modBus) {
        for (KeyMapping k : new KeyMapping[] {Actions.WHEEL, Answers.ACCEPT, Answers.DECLINE}) k.setKeyConflictContext(KeyConflictContext.IN_GAME);
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(Actions.WHEEL);
            e.register(Answers.ACCEPT);
            e.register(Answers.DECLINE);
        });
        Actions.modNames(ns -> ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns));
        Actions.keyModifiers(k -> switch (k.getKeyModifier()) {
            case CONTROL -> GLFW.GLFW_MOD_CONTROL;
            case SHIFT -> GLFW.GLFW_MOD_SHIFT;
            case ALT -> GLFW.GLFW_MOD_ALT;
            default -> 0;
        });
        Actions.keyContexts((a, b) -> a.getKeyConflictContext().conflicts(b.getKeyConflictContext())
            || b.getKeyConflictContext().conflicts(a.getKeyConflictContext()));
        // плитка «Клавиши» на панели семьи: окно «Клавиши» (1.15, значок клавиш с 1.15.5); в колесе её не дублируем
        DockButton.builder(KEYS_TILE).label(() -> Component.translatable("ryscore.dock.keys"))
            .description(() -> Component.translatable("ryscore.dock.keys.desc", Binding.keyLabel(Binding.keyOf(Actions.WHEEL))))
            .tile(0xFF4E9A6A).icon(Icons.Shape.KEYCAPS).order(90)
            .action(() -> Actions.openKeys(Minecraft.getInstance().screen)).register();
        Actions.coversDock(KEYS_TILE);
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Post e) -> {
            KeyDefaults.apply();
            Actions.clientTick();
            Answers.clientTick();
        });
        bus.addListener((InputEvent.Key e) -> {
            if (e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
            Actions.keyPressed(InputConstants.getKey(e.getKey(), e.getScanCode()), e.getModifiers());
        });
        bus.addListener((InputEvent.MouseButton.Post e) -> {
            if (e.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
            Actions.keyPressed(InputConstants.Type.MOUSE.getOrCreate(e.getButton()), e.getModifiers());
        });
    }

    /**
     * Уведомления семьи (тосты игры) в открытом окне: клик по карточке открывает её дело, крестик и правая кнопка убирают её;
     * нажатие и отпускание над карточкой в окно под ней не уходят.
     */
    private static void noticeHooks() {
        IEventBus bus = NeoForge.EVENT_BUS;
        // боссбары сверху по центру: уведомления «по центру» встают под последним (полоса высотой 5 под строкой имени)
        bus.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress e) -> {
            if (!e.isCanceled()) Notices.bossBar(e.getY() + 5);
        });
        bus.addListener(EventPriority.HIGHEST, (ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (Notices.click(e.getMouseX(), e.getMouseY(), e.getButton())) {
                noticePress = true;
                e.setCanceled(true);
            }
        });
        bus.addListener(EventPriority.HIGHEST, (ScreenEvent.MouseButtonReleased.Pre e) -> {
            if (noticePress) {
                noticePress = false;
                e.setCanceled(true);
            }
        });
    }

    private static boolean noticePress;

    /**
     * Панель кнопок семьи: где хозяин заменяет панель FTB, её виджет убирается; где хозяина нет и FTB молчит,
     * кор сам рисует панель слева от окна инвентаря и ловит по ней нажатия.
     */
    private static void dockHooks() {
        IEventBus bus = NeoForge.EVENT_BUS;
        // самый низкий приоритет: виджет FTB добавляется в этом же событии и должен уже быть на экране
        bus.addListener(EventPriority.LOWEST, (ScreenEvent.Init.Post e) -> {
            Dock.screenChanged();
            if (!Dock.replaces(e.getScreen())) return;
            for (var listener : List.copyOf(e.getListenersList())) {
                if (Dock.isFtbWidget(listener)) e.removeListener(listener);
            }
            Dock.widgetRemoved();
        });
        bus.addListener((ScreenEvent.Render.Post e) -> {
            if (!(e.getScreen() instanceof AbstractContainerScreen<?> screen) || !Dock.standalone(screen)) return;
            int left = (int) Math.floor(UiScale.fromGui(screen.getGuiLeft())), top = (int) Math.floor(UiScale.fromGui(screen.getGuiTop()));
            int mx = (int) Math.floor(UiScale.mouseX()), my = (int) Math.floor(UiScale.mouseY());
            UiScale.render(e.getGuiGraphics(), g -> {
                Dock.layoutBeside(screen, left, top);
                Dock.render(g, ThemeManager.get(), mx, my);
                Dock.renderTooltip(g, ThemeManager.get(), mx, my);
            });
        });
        bus.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (!Dock.standalone(e.getScreen())) return;
            if (Dock.click(UiScale.fromGui(e.getMouseX()), UiScale.fromGui(e.getMouseY()), e.getButton())) e.setCanceled(true);
        });
    }

    /** Шейдер пачки кора: цвет в вершинах, отбрасываются только полностью прозрачные пиксели. */
    private static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(RYSCore.MOD_ID, "gui_tex_color"), DefaultVertexFormat.POSITION_TEX_COLOR), Batch::setShader);
        } catch (IOException e) {
            // без шейдера кор рисует каждую фигуру сразу, как раньше
            Batch.setShader(null);
            RYSCore.LOG.error("Шейдер пачки кора не загружен", e);
        }
    }

    /** Как в компиляции секций NeoForge: слои отрисовки задаёт сама модель. */
    private static void mesh(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose, RandomSource random, Function<RenderType, VertexConsumer> buffers) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(state);
        ModelData data = model.getModelData(level, pos, state, ModelData.EMPTY);
        random.setSeed(state.getSeed(pos));
        for (RenderType type : model.getRenderTypes(state, random, data)) {
            dispatcher.renderBatched(state, pos, level, pose, buffers.apply(type), true, random, data, type);
        }
    }
}
