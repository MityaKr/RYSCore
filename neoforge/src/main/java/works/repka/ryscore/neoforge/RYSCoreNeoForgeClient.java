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
import works.repka.ryscore.RYSCore;
import works.repka.ryscore.dock.Dock;
import works.repka.ryscore.ui.Notices;
import works.repka.ryscore.ui.UiScale;
import works.repka.ryscore.scene.SceneHooks;
import works.repka.ryscore.theme.ThemeManager;
import works.repka.ryscore.ui.Batch;

/** Клиентская часть кора: темы перечитываются вместе с ресурсами, модам семьи делать ничего не нужно. */
@Mod(value = RYSCore.MOD_ID, dist = Dist.CLIENT)
public final class RYSCoreNeoForgeClient {
    public RYSCoreNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((ResourceManagerReloadListener) ThemeManager::reload));
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((ResourceManagerReloadListener) resources -> SceneHooks.reloaded()));
        SceneHooks.setMesher(RYSCoreNeoForgeClient::mesh);
        modBus.addListener(RYSCoreNeoForgeClient::registerShaders);
        dockHooks();
        noticeHooks();
    }

    /**
     * Уведомления семьи (тосты игры) в открытом окне: клик по карточке открывает её дело, крестик и правая кнопка убирают её;
     * нажатие и отпускание над карточкой в окно под ней не уходят.
     */
    private static void noticeHooks() {
        IEventBus bus = NeoForge.EVENT_BUS;
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
