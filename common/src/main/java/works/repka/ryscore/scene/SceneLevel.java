package works.repka.ryscore.scene;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/** Мир сцены для построения геометрии: соседей знает, свет полный, биом равнины. */
final class SceneLevel implements BlockAndTintGetter {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final Map<BlockPos, BlockState> blocks;
    private final int height;
    @Nullable
    private final Integer layer;
    @Nullable
    private final Biome biome;

    SceneLevel(BlockScene scene, @Nullable Integer layer) {
        this.blocks = scene.blocks();
        this.height = Math.max(1, scene.size().getY());
        this.layer = layer;
        ClientLevel level = Minecraft.getInstance().level;
        this.biome = level == null ? null : level.registryAccess().registryOrThrow(Registries.BIOME).get(Biomes.PLAINS);
    }

    boolean shown(BlockPos pos) {
        return layer == null || pos.getY() == layer;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return shown(pos) ? blocks.getOrDefault(pos, AIR) : AIR;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) return 1f;
        return switch (direction) {
            case DOWN -> 0.5f;
            case UP -> 1f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return Minecraft.getInstance().level.getLightEngine();
    }

    @Override
    public int getBrightness(LightLayer type, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return biome == null ? -1 : resolver.getColor(biome, pos.getX(), pos.getZ());
    }
}
