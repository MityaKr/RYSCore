package works.repka.ryscore.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import works.repka.ryscore.RYSCore;
import works.repka.ryscore.scene.SceneHooks;
import works.repka.ryscore.theme.ThemeManager;

/** Клиентская часть кора: темы перечитываются вместе с ресурсами, модам семьи делать ничего не нужно. */
@Mod(value = RYSCore.MOD_ID, dist = Dist.CLIENT)
public final class RYSCoreNeoForgeClient {
    public RYSCoreNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((ResourceManagerReloadListener) ThemeManager::reload));
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
            event.registerReloadListener((ResourceManagerReloadListener) resources -> SceneHooks.reloaded()));
        SceneHooks.setMesher(RYSCoreNeoForgeClient::mesh);
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
