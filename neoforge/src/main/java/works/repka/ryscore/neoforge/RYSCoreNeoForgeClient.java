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
import works.repka.ryscore.RYSCore;
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
