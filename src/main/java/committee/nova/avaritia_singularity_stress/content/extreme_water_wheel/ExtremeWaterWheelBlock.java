package committee.nova.avaritia_singularity_stress.content.extreme_water_wheel;

import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * B28 终焉水车：沿用 Create 水车的朝向/形状/材质交互，仅替换方块实体类型。
 * <p>
 * 父类 {@code tick} 会调用 {@code determineAndApplyFlowScore}，本模组实体已将其覆写为
 * "检测流体→刷新容量"，因此无需改动方块逻辑。IBE 按 isInstance 判定，子类实体天然通过。
 */
public class ExtremeWaterWheelBlock extends WaterWheelBlock {

    public ExtremeWaterWheelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends WaterWheelBlockEntity> getBlockEntityType() {
        return ModBlockEntities.EXTREME_WATER_WHEEL.get();
    }
}
