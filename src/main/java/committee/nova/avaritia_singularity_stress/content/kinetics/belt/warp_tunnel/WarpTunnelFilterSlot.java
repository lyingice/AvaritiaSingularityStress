package committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * B15 双槽的槽位定位：<b>挂在隧道的开口侧面上</b>（与黄铜隧道同一个位置风格），
 * 一面两槽并排，{@code warpSide=true} 是折跃槽、{@code false} 是物流槽。
 * <p>
 * 开口面 = 沿带轴正向那一面（带轴 Z → 南面；带轴 X → 东面）。
 * 高度取 13/16，与 Create 的 {@code BrassTunnelFilterSlot} 一致。
 * <p>
 * 之前放在顶面是因为"四面可选"的旧设计；按截图反馈改回侧面。
 */
public class WarpTunnelFilterSlot extends ValueBoxTransform {

    /** 槽位在面内的横向偏移（左右各一格）。 */
    private static final double LATERAL = 0.19D;
    /** 槽位高度（13/16 = 0.8125）。 */
    private static final double HEIGHT = 0.8125D;

    private final boolean warpSide;

    public WarpTunnelFilterSlot(boolean warpSide) {
        this.warpSide = warpSide;
    }

    private static Direction faceOf(BlockState state) {
        return state.getValue(BeltTunnelBlock.HORIZONTAL_AXIS) == Direction.Axis.Z
                ? Direction.SOUTH
                : Direction.EAST;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction face = faceOf(state);
        double lateral = warpSide ? -LATERAL : LATERAL;
        return switch (face) {
            case SOUTH -> new Vec3(0.5 + lateral, HEIGHT, 1.0);
            case EAST -> new Vec3(1.0, HEIGHT, 0.5 + lateral);
            default -> new Vec3(0.5 + lateral, HEIGHT, 1.0);
        };
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        float yRot = AngleHelper.horizontalAngle(faceOf(state)) + 180;
        TransformStack.of(ms)
                .rotateYDegrees(yRot)
                .rotateXDegrees(0);
    }
}
