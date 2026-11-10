package works.repka.ryscore.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import works.repka.ryscore.RYSCore;

/** Геометрия одного вида сцены: сначала в памяти, после загрузки в буферах видеокарты. */
final class SceneMesh implements AutoCloseable {
    private static final int START_BYTES = 1 << 16;

    /** Готовые вершины, ещё не загруженные в видеокарту. Освобождать можно на любом потоке. */
    static final class Compiled {
        private final List<Piece> pieces = new ArrayList<>();
        private MeshData.SortState sort;
        private ByteBufferBuilder sortBuffer;

        void release() {
            for (Piece p : pieces) {
                p.mesh.close();
                p.memory.close();
            }
            pieces.clear();
            if (sortBuffer != null) sortBuffer.close();
            sortBuffer = null;
        }
    }

    private record Piece(RenderType type, ByteBufferBuilder memory, MeshData mesh) {}

    private record Uploaded(RenderType type, VertexBuffer buffer) {}

    /** Вершины жидкости игра пишет относительно секции 16×16×16, сцена больше: возвращаем сдвиг. */
    private record Shifted(VertexConsumer out, float x, float y, float z) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float vx, float vy, float vz) {
            out.addVertex(vx + x, vy + y, vz + z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            out.setNormal(nx, ny, nz);
            return this;
        }
    }

    static Compiled compile(BlockScene scene, @Nullable Integer layer, VertexSorting sorting) {
        SceneLevel level = new SceneLevel(scene, layer);
        Map<RenderType, BufferBuilder> builders = new IdentityHashMap<>();
        Map<RenderType, ByteBufferBuilder> memory = new IdentityHashMap<>();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        Compiled out = new Compiled();
        int skipped = 0;
        ModelBlockRenderer.enableCaching();
        try {
            Function<RenderType, VertexConsumer> buffers = type -> builders.computeIfAbsent(type, t -> {
                ByteBufferBuilder bytes = new ByteBufferBuilder(START_BYTES);
                memory.put(t, bytes);
                return new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            });
            for (Map.Entry<BlockPos, BlockState> e : scene.blocks().entrySet()) {
                BlockPos pos = e.getKey();
                if (!level.shown(pos)) continue;
                BlockState state = e.getValue();
                // чужая модель может упасть на необычном мире: теряем блок, а не всю сцену
                try {
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        VertexConsumer target = new Shifted(buffers.apply(ItemBlockRenderTypes.getRenderLayer(fluid)), pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
                        dispatcher.renderLiquid(pos, level, target, state, fluid);
                    }
                    if (state.getRenderShape() == RenderShape.MODEL) {
                        pose.pushPose();
                        pose.translate(pos.getX(), pos.getY(), pos.getZ());
                        try {
                            SceneHooks.mesher().mesh(state, pos, level, pose, random, buffers);
                        } finally {
                            pose.popPose();
                        }
                    }
                } catch (Throwable t) {
                    if (skipped++ == 0) RYSCore.LOG.warn("Блок {} не попал в сцену: {}", state, t.toString());
                }
            }
            for (Map.Entry<RenderType, BufferBuilder> e : builders.entrySet()) {
                MeshData mesh = e.getValue().build();
                ByteBufferBuilder bytes = memory.remove(e.getKey());
                if (mesh == null) {
                    bytes.close();
                    continue;
                }
                out.pieces.add(new Piece(e.getKey(), bytes, mesh));
                if (e.getKey() == RenderType.translucent()) {
                    out.sortBuffer = new ByteBufferBuilder(START_BYTES);
                    out.sort = mesh.sortQuads(out.sortBuffer, sorting);
                }
            }
        } catch (Throwable t) {
            out.release();
            memory.values().forEach(ByteBufferBuilder::close);
            throw t;
        } finally {
            ModelBlockRenderer.clearCache();
        }
        return out;
    }

    private final List<Uploaded> layers = new ArrayList<>();
    @Nullable
    private VertexBuffer translucent;
    @Nullable
    private MeshData.SortState sort;
    @Nullable
    private ByteBufferBuilder sortBuffer;

    /** Загружает вершины в видеокарту и отпускает их память. Только на потоке отрисовки. */
    SceneMesh(Compiled compiled) {
        RenderSystem.assertOnRenderThread();
        List<RenderType> order = RenderType.chunkBufferLayers();
        compiled.pieces.sort(Comparator.comparingInt(p -> {
            int index = order.indexOf(p.type);
            return index < 0 ? order.size() : index;
        }));
        for (Piece p : compiled.pieces) {
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(p.mesh);
            p.memory.close();
            layers.add(new Uploaded(p.type, buffer));
            if (p.type == RenderType.translucent()) translucent = buffer;
        }
        VertexBuffer.unbind();
        compiled.pieces.clear();
        sort = compiled.sort;
        sortBuffer = compiled.sortBuffer;
        compiled.sortBuffer = null;
    }

    void resort(VertexSorting sorting) {
        if (translucent == null || sort == null || sortBuffer == null) return;
        translucent.bind();
        translucent.uploadIndexBuffer(sort.buildSortedIndexBuffer(sortBuffer, sorting));
        VertexBuffer.unbind();
    }

    void draw(Matrix4f modelView, Matrix4f projection) {
        for (Uploaded layer : layers) {
            layer.type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                if (shader.CHUNK_OFFSET != null) shader.CHUNK_OFFSET.set(0f, 0f, 0f);
                layer.buffer.bind();
                layer.buffer.drawWithShader(modelView, projection, shader);
            }
            layer.type.clearRenderState();
        }
        VertexBuffer.unbind();
    }

    @Override
    public void close() {
        layers.forEach(l -> l.buffer.close());
        layers.clear();
        translucent = null;
        if (sortBuffer != null) sortBuffer.close();
        sortBuffer = null;
    }
}
