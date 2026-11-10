package works.repka.ryscore.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Связь сцены с загрузчиком. Служебный класс кора, модам семьи он не нужен. */
public final class SceneHooks {
    /** Раскладывает модель блока по слоям отрисовки: слои модели знает только загрузчик. */
    public interface Mesher {
        void mesh(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose, RandomSource random, Function<RenderType, VertexConsumer> buffers);
    }

    private static volatile Mesher mesher = SceneHooks::vanilla;
    private static volatile int generation;

    private SceneHooks() {}

    public static void setMesher(Mesher value) {
        mesher = value;
    }

    /** Ресурсы перечитаны: координаты в атласе устарели, геометрию надо строить заново. */
    public static void reloaded() {
        generation++;
    }

    static Mesher mesher() {
        return mesher;
    }

    static int generation() {
        return generation;
    }

    private static void vanilla(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose, RandomSource random, Function<RenderType, VertexConsumer> buffers) {
        Minecraft.getInstance().getBlockRenderer().renderBatched(state, pos, level, pose, buffers.apply(ItemBlockRenderTypes.getChunkRenderType(state)), true, random);
    }
}
