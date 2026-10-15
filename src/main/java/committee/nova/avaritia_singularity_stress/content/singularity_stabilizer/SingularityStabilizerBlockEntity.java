package committee.nova.avaritia_singularity_stress.content.singularity_stabilizer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B14 奇点稳定器的方块实体。
 * <p>
 * 轴式动能方块。品控抑制是<b>按 B6 上链路</b>判定的,不是全网布尔:
 * 只有从某台 B6 沿 source 链向上回溯能到达本稳定器时,该台 B6 的品控损耗
 * ({@code SingularityConfig.qualityLossMin()/qualityLossMax()} 区间浮动值)
 * 才被抑制为 0;多台 B14 各管各的下游,互不叠加。
 * 稳定器自身不消耗应力;判定逻辑在 {@code SingularityStress.isStabilizerUpstream}。
 * 稳定器自身不消耗应力(占用 0 由注册表 impact=0 保证,无需覆写计算);
 * 是否生效由 {@code SingularityStress} 的网络快照统一检测(遍历 members 中的本类型)。
 */
public class SingularityStabilizerBlockEntity extends KineticBlockEntity {

    public SingularityStabilizerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 方块实体工厂,供注册表使用。 */
    public static SingularityStabilizerBlockEntity create(BlockPos pos, BlockState state) {
        return new SingularityStabilizerBlockEntity(
                ModBlockEntities.SINGULARITY_STABILIZER.get(), pos, state);
    }
}
