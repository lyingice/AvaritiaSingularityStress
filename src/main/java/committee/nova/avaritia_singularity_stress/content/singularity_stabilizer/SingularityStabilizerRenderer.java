package committee.nova.avaritia_singularity_stress.content.singularity_stabilizer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B14 奇点稳定器的 BER(flywheel 不可用时的兜底渲染)。
 * <p>
 * 方块状态模型是空壳(只有粒子纹理),旋转几何全部来自 rotor partial 模型。
 * 这里按方块旋转轴把 Y 轴几何转到对应方向,之后由标准旋转变换按动能角度自转。
 * 转轴约定与 flywheel 路径的 {@code RotatingInstance.rotateToFace(UP, axis)} 一致。
 */
public class SingularityStabilizerRenderer extends KineticBlockEntityRenderer<SingularityStabilizerBlockEntity> {

    public SingularityStabilizerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(SingularityStabilizerBlockEntity be, BlockState state) {
        SuperByteBuffer buffer = CachedBuffers.partial(ModPartialModels.SINGULARITY_STABILIZER, state);
        Direction.Axis axis = state.getValue(RotatedPillarKineticBlock.AXIS);
        if (axis == Direction.Axis.X) {
            // +Y → +X
            buffer.rotateCenteredDegrees(-90.0F, Direction.Axis.Z);
        } else if (axis == Direction.Axis.Z) {
            // +Y → +Z
            buffer.rotateCenteredDegrees(90.0F, Direction.Axis.X);
        }
        return buffer;
    }
}