package committee.nova.avaritia_singularity_stress.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock;
import committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller.SingularityRotationSpeedControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Create 的动力传播把"上方大齿轮"识别为也能连接<strong>奇点转速控制器</strong>。
 * <p>
 * Create 的 {@code RotationPropagator.isLargeCogToSpeedController} 里硬编码了
 * {@code AllBlocks.ROTATION_SPEED_CONTROLLER.has(to)},自定义方块不在其中,
 * 于是大齿轮永远不会与 B6 建立传动。
 * <p>
 * 这里在方法 HEAD 处拦截:只要 {@code to} 是本模组的控制器方块,就按 Create 原有的
 * "大齿轮在上、轴线水平且互相垂直"的规则给出判定;否则原样放行。
 */
@Mixin(RotationPropagator.class)
public abstract class RotationPropagatorMixin {

    @Inject(method = "isLargeCogToSpeedController", at = @At("HEAD"), cancellable = true)
    private static void avaritia_singularity_stress$recognizeSingularityController(
            BlockState from, BlockState to, BlockPos diff, CallbackInfoReturnable<Boolean> cir) {
        if (!(to.getBlock() instanceof SingularityRotationSpeedControllerBlock))
            return;

        boolean isController = ICogWheel.isLargeCog(from)
                && diff.equals(BlockPos.ZERO.below())
                && !from.getValue(CogWheelBlock.AXIS).isVertical()
                && to.getValue(SpeedControllerBlock.HORIZONTAL_AXIS) != from.getValue(CogWheelBlock.AXIS);

        cir.setReturnValue(isController);
    }
}