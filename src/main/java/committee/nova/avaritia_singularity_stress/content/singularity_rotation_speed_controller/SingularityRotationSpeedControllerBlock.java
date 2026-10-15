package committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller;

import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * B6 奇点转速控制器。
 * <p>
 * 行为完全继承 Create 转速控制器(滚轮设目标转速、上方大齿轮输出、输出端限速),
 * 仅把方块实体类型换成自己的实现。自定义方块要获得"上方大齿轮连接"识别,
 * 还需要 {@code RotationPropagatorMixin} 扩展 Create 内部硬编码的判定。
 */
public class SingularityRotationSpeedControllerBlock extends SpeedControllerBlock {

    public SingularityRotationSpeedControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends SpeedControllerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SINGULARITY_ROTATION_SPEED_CONTROLLER.get();
    }
}