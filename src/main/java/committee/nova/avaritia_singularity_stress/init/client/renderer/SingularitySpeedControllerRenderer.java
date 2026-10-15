package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller.SingularityRotationSpeedControllerBlockEntity;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 奇点转速控制器(B6)的方块实体渲染,与 Create 转速控制器一致:
 * Flywheel 不可用时才绘制旋转轴,并在上方确有大齿轮时绘制支架。
 */
public class SingularitySpeedControllerRenderer
        extends SmartBlockEntityRenderer<SingularityRotationSpeedControllerBlockEntity> {

    public SingularitySpeedControllerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SingularityRotationSpeedControllerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        VertexConsumer builder = buffer.getBuffer(Sheets.solidBlockSheet());
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            KineticBlockEntityRenderer.renderRotatingBuffer(be, getRotatedModel(be), ms, builder, light);
        }

        if (!hasLargeCogAbove(be))
            return;

        BlockPos pos = be.getBlockPos();
        Level world = be.getLevel();
        BlockState blockState = be.getBlockState();
        boolean alongX = blockState.getValue(SpeedControllerBlock.HORIZONTAL_AXIS) == Axis.X;

        SuperByteBuffer bracket = CachedBuffers.partial(AllPartialModels.SPEED_CONTROLLER_BRACKET, blockState);
        bracket.translate(0, 1, 0);
        bracket.rotateCentered((float) (alongX ? Math.PI : Math.PI / 2), Direction.UP);
        bracket.light(LevelRenderer.getLightColor(world, pos.above()));
        bracket.renderInto(ms, builder);
    }

    private SuperByteBuffer getRotatedModel(SingularityRotationSpeedControllerBlockEntity be) {
        return CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK,
                KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(be)));
    }

    private static boolean hasLargeCogAbove(SingularityRotationSpeedControllerBlockEntity be) {
        Level level = be.getLevel();
        if (level == null)
            return false;
        BlockState above = level.getBlockState(be.getBlockPos().above());
        return ICogWheel.isLargeCog(above) && above.getValue(CogWheelBlock.AXIS).isHorizontal();
    }
}