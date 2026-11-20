package committee.nova.avaritia_singularity_stress.content.extreme_drill;

import com.simibubi.create.content.kinetics.drill.DrillBlock;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * B27 终焉钻头：沿用 Create 机械钻头的朝向/形状/水logged/伤害行为，仅替换方块实体类型。
 * <p>
 * IBE 通过 {@code getBlockEntityClass().isInstance} 判定实体（Create IBE.java:77-86），
 * 本实体是 {@link DrillBlockEntity} 子类，天然通过，故无需覆写 getBlockEntityClass。
 */
public class ExtremeDrillBlock extends DrillBlock {

    public ExtremeDrillBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends DrillBlockEntity> getBlockEntityType() {
        return ModBlockEntities.EXTREME_DRILL.get();
    }
}
