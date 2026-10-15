package committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * B2 动能中子态素收集器——方块。
 * <p>
 * 接入旋转力后持续产出中子材料(RPM 越高越快、耗应力越大)。右键打开 GUI 查看产出槽。
 * 产出速度与 SSU 加成的计算在 {@link KineticNeutronCollectorBlockEntity} 内。
 */
public class KineticNeutronCollectorBlock extends HorizontalKineticBlock
        implements IBE<KineticNeutronCollectorBlockEntity> {

    public KineticNeutronCollectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof KineticNeutronCollectorBlockEntity collector) {
            player.openMenu(collector, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<KineticNeutronCollectorBlockEntity> getBlockEntityClass() {
        return KineticNeutronCollectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KineticNeutronCollectorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.KINETIC_NEUTRON_COLLECTOR.get();
    }
}
