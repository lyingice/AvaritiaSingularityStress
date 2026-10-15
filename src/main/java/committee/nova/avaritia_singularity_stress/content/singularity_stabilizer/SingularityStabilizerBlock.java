package committee.nova.avaritia_singularity_stress.content.singularity_stabilizer;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * B14 奇点稳定器。
 * <p>
 * 轴式动能方块(结构与 Create 轴一致,可沿轴向传递旋转力),接入应力网络任意处即对
 * <b>全网</b>生效:网络中所有奇点压缩器(B6)的品控损耗
 * ({@code SingularityStress.QC_LOSS_PER_B6} SU/RPM/台)被抑制,
 * 压缩占用降为纯粹的 32 SU/RPM/台。
 * <p>
 * 稳定器自身不消耗应力(注册表 impact = 0);是否生效由
 * {@code SingularityStress} 的网络快照统一检测(遍历 members 中的本方块实体)。
 * 传动/扳手/支架行为完全继承 {@link AbstractSimpleShaftBlock}。
 */
public class SingularityStabilizerBlock extends AbstractSimpleShaftBlock {

    public SingularityStabilizerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SINGULARITY_STABILIZER.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.SIX_VOXEL_POLE.get(state.getValue(AXIS));
    }

    @Override
    public float getParticleTargetRadius() {
        return .35f;
    }

    @Override
    public float getParticleInitialRadius() {
        return .125f;
    }
}
