package committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * B21：接收端「档位转速」滚轮的贴面位置。
 * <p>
 * <b>为什么照抄 {@code CreativeMotorBlockEntity.MotorValueBox} 而不是自己算坐标</b>：
 * 创造马达的滚轮就是玩家已经熟悉的那一颗（悬停显示「产生的 RPM 转速 / 单击并按住以编辑」），
 * 位置、旋转、字体缩放全都经过调校。本方块的模型又是创造马达模型的逐元素复制件
 * （见 {@link WirelessStressBridgeBlock} 类注释），直接复用同一套 voxel 坐标即可对齐，
 * 不必重新试错。源码：{@code CreativeMotorBlockEntity.java:80-110}。
 * <p>
 * <b>为什么不放在接网面（{@code FACING}）上</b>：那一面插着半轴/齿轮，
 * 滚轮图标会被轴挡住、点击也容易点到轴上。
 *
 * @see WirelessStressBridgeGearBehaviour
 */
final class WirelessStressBridgeValueBox extends ValueBoxTransform.Sided {

    /** 与创造马达同一个落点：南面 z=12.5 处的方块中心。 */
    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8, 8, 12.5);
    }

    /**
     * 把落点往 {@code FACING} 的反方向推 1/16 —— 与创造马达一致：
     * 创造马达的面板在背面，滚轮不能浮在面板外面。
     * 源码：{@code CreativeMotorBlockEntity.java:89-93}。
     */
    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(WirelessStressBridgeBlock.FACING);
        return super.getLocalOffset(level, pos, state)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-1 / 16f));
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        super.rotate(level, pos, state, ms);
        Direction facing = state.getValue(WirelessStressBridgeBlock.FACING);
        if (facing.getAxis() == Axis.Y) {
            return;
        }
        if (getSide() != Direction.UP) {
            return;
        }
        TransformStack.of(ms).rotateZDegrees(-AngleHelper.horizontalAngle(facing) + 180);
    }

    /**
     * 只在「与接网面垂直」的四个侧面上激活。
     * <p>
     * 接网面本身（{@code FACING}）有轴，背面（{@code FACING.getOpposite()}）是机壳背板，
     * 两面都不放；剩下四个侧面各有一个滚轮图标。
     */
    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        Direction facing = state.getValue(WirelessStressBridgeBlock.FACING);
        return direction.getAxis() != facing.getAxis();
    }
}
