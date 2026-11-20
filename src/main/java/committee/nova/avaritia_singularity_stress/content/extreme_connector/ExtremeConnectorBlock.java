package committee.nova.avaritia_singularity_stress.content.extreme_connector;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeCrafterHelper;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * B30 终焉连接器：CT 方块，放在终焉动能合成器<b>背面</b>（{@code crafterPos.relative(facing.getOpposite())}），
 * 把该合成器纳入终焉动能合成终端的统一管理。
 * <p>
 * <b>纳管按位置语义</b>：连接器只要恰在某合成器背面（合成器朝向的反方向一格）就纳管它，
 * 与连接器自身 FACING 无关——对着相邻连接器侧边点击放置也算背贴（2026-11-20 按用户反馈放宽，
 * 旧版要求 FACING 与合成器一致，重放时点击面不同就整链拒绝，反直觉）。
 * FACING 仅作放置提示：放置点若在某合成器背面则优先指向它，否则按点击面反向。
 * <p>
 * 连接器之间相邻即组网；终端只要贴着网络中任一连接器，即可管理整片合成网络
 * （网络 = 所有经连接器接入的合成器集体，见 {@code CraftingTerminalNetwork}）。
 * 侧面/顶面贴附（不在任何合成器背面）只作为中继节点，不要求前方有合成器。
 * 背面位于朝向轴上，不参与扳手连接组（shouldConnect 排除同轴邻居），故不破坏现有 ConnectedInput。
 */
public class ExtremeConnectorBlock extends DirectionalBlock implements IBE<ExtremeConnectorBlockEntity> {

    public ExtremeConnectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<ExtremeConnectorBlock> codec() {
        return simpleCodec(ExtremeConnectorBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
        // 放置点若恰在某合成器背面，优先指向它（与点击面无关），
        // 保证"对着相邻连接器侧边放"也得到正确提示朝向；纳管本身按位置判定。
        BlockPos pos = context.getClickedPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = context.getLevel().getBlockState(pos.relative(d));
            if (ExtremeCrafterHelper.isCrafter(neighbor)
                    && neighbor.getValue(HORIZONTAL_FACING) == d) {
                return state.setValue(FACING, d);
            }
        }
        return state;
    }

    /**
     * 原版 {@link DirectionalBlock} 只声明 FACING，没有覆写 rotate/mirror；
     * {@code BlockBehaviour.java:248-254} 的默认实现是原样返回（no-op）。
     * 不补这两个方法，结构方块旋转/镜像后连接器朝向会和周围机器脱节，
     * 背贴网络验证就会把整条链判失败。这里沿用
     * {@code HorizontalDirectionalBlock.java:20-27} 的标准写法。
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * 该连接器背贴的合成器位置（位置语义：连接器恰在合成器朝向反方向一格）；无则 null。
     * 不读连接器自身 FACING，重放/旋转后依然按几何关系判定。
     */
    public static BlockPos attachedCrafterPos(Level level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos crafterPos = pos.relative(d);
            BlockState state = level.getBlockState(crafterPos);
            if (ExtremeCrafterHelper.isCrafter(state)
                    && state.getValue(HORIZONTAL_FACING) == d) {
                return crafterPos;
            }
        }
        return null;
    }

    @Override
    public Class<ExtremeConnectorBlockEntity> getBlockEntityClass() {
        return ExtremeConnectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ExtremeConnectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.EXTREME_CONNECTOR.get();
    }
}
