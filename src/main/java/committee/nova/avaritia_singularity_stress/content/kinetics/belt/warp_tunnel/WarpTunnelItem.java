package committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel;

import com.simibubi.create.content.logistics.tunnel.BeltTunnelItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * B15 空间折跃隧道·物品。
 * <p>
 * 基类 {@link BeltTunnelItem} 的 {@code canPlace} 把"能不能放"写死成
 * {@code AllBlocks.ANDESITE_TUNNEL.isValidPositionForPlacement(...)}；
 * 那段判定对任何隧道都一样（只看下方是不是水平带子），但直接沿用会让本方块
 * 借用到 Create 的方块实例上，语义容易误读，所以这里用我们自己的实例重算一次。
 * <p>
 * 放置外观保持 Create 原版行为：父类 {@code updateCustomBlockEntityTag}
 * （allapis 6.0.11 {@code BeltTunnelItem.java:36-45}）会在隧道放下时把下方带子的
 * {@code BeltBlockEntity.casing} 从 {@code NONE} 设成 {@code BRASS}，也就是给底下那段
 * 带子套上黄铜机壳。用户定稿：保留这个黄铜封壳，不再做水晶传送带。
 */
public class WarpTunnelItem extends BeltTunnelItem {

    public WarpTunnelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        Player player = context.getPlayer();
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Block block = getBlock();
        boolean validPosition = !mustSurvive()
                || (block instanceof WarpTunnelBlock tunnel && tunnel.isValidPositionForPlacement(state, level, pos));
        return validPosition && level.isUnobstructed(state, pos, collision);
    }
}
