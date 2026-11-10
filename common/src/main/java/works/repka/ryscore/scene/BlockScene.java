package works.repka.ryscore.scene;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Неизменяемая блоковая структура для {@link SceneView}. Собирать можно на любом потоке. */
public final class BlockScene {
    private final Map<BlockPos, BlockState> blocks;
    private final Vec3i size;
    private final Map<Item, Integer> counts;

    private BlockScene(Map<BlockPos, BlockState> blocks, Vec3i size, Map<Item, Integer> counts) {
        this.blocks = blocks;
        this.size = size;
        this.counts = counts;
    }

    /** Координаты сдвигаются так, чтобы минимальный угол стал нулём; воздух отбрасывается. */
    public static BlockScene of(Map<BlockPos, BlockState> source) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : source.entrySet()) {
            if (e.getValue().isAir()) continue;
            BlockPos p = e.getKey();
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        Map<BlockPos, BlockState> shifted = new HashMap<>();
        Map<Item, Integer> tally = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : source.entrySet()) {
            BlockState state = e.getValue();
            if (state.isAir()) continue;
            BlockPos p = e.getKey();
            shifted.put(new BlockPos(p.getX() - minX, p.getY() - minY, p.getZ() - minZ), state);
            Item item = state.getBlock().asItem();
            if (item != Items.AIR) tally.merge(item, 1, Integer::sum);
        }
        Vec3i size = shifted.isEmpty() ? Vec3i.ZERO : new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        Map<Item, Integer> sorted = new LinkedHashMap<>();
        tally.entrySet().stream()
            .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
            .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return new BlockScene(Collections.unmodifiableMap(shifted), size, Collections.unmodifiableMap(sorted));
    }

    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    public Vec3i size() {
        return size;
    }

    /** Сколько блоков каждого предмета, по убыванию. Блоки без предмета не считаются. */
    public Map<Item, Integer> counts() {
        return counts;
    }
}
