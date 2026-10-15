package committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor;

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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * B4 动能中子态素压缩机——方块。
 * <p>
 * 接入旋转力后把矿物压缩成奇点,配方复用无尽贪婪的压缩机配方。右键打开 GUI。
 */
public class KineticNeutronCompressorBlock extends HorizontalKineticBlock
        implements IBE<KineticNeutronCompressorBlockEntity> {

    public KineticNeutronCompressorBlock(Properties properties) {
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
        if (level.getBlockEntity(pos) instanceof KineticNeutronCompressorBlockEntity compressor) {
            player.openMenu(compressor, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<KineticNeutronCompressorBlockEntity> getBlockEntityClass() {
        return KineticNeutronCompressorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KineticNeutronCompressorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.KINETIC_NEUTRON_COMPRESSOR.get();
    }
}
