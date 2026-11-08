package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.function.Supplier;

/**
 * 包壳传动件在「没有 Flywheel 可视化的世界」里的兜底渲染。
 * <p>
 * <b>为什么需要它</b>：包壳方块的方块模型只有外壳（和中间一条色带），轴承/齿盘那部分几何是
 * Flywheel visual（{@code SingleAxisRotatingVisual} / {@code EncasedCogVisual}）现画的。
 * 而 Flywheel 的 {@code VisualizationManager} 只挂在 {@code ClientLevel} 上，
 * Ponder 用的是假世界 {@code PonderLevel}，visual 整个不参与，
 * 于是可怜地只剩一个空壳——这就是「封装后齿轮消失」的根因。
 * <p>
 * <b>本类的做法</b>：在非 {@code ClientLevel} 的环境里，把对应**普通方块**的模型（轴 / 小齿轮 / 大齿轮）
 * 叠在包壳方块上。这些模型的齿本来就伸出方块边界（小齿轮 x −1..17，大齿轮 −7..23），
 * 所以能从外壳两侧露出来，视觉上和游戏里的包壳一致。
 * <p>
 * <b>不会画两遍</b>：{@code ClientLevel} 里直接 return，交回给 {@code ModVisualizers} 的 visual。
 * <p>
 * 注意：Ponder 里画出来的是**静态**齿轮（不转）——场景里本来就没有真正的动能模拟。
 */
public class PonderKineticFallbackRenderer<T extends KineticBlockEntity> implements BlockEntityRenderer<T> {

    /** 拿哪个方块的模型来画（轴 / 小齿轮 / 大齿轮）。 */
    // 改后
    private final Supplier<PartialModel> model;

    public PonderKineticFallbackRenderer(BlockEntityRendererProvider.Context context,
                                         Supplier<PartialModel> model) {
        this.model = model;
    }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        // 真实客户端世界里交给 Flywheel visual，这里什么都不画，避免双份
        if (be.getLevel() instanceof ClientLevel) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!state.hasProperty(RotatedPillarKineticBlock.AXIS)) {
            return;
        }
        Direction.Axis axis = state.getValue(RotatedPillarKineticBlock.AXIS);

        // partial model 天生轴朝 Y（模型里齿条是绕 Y 排开的）。所以两步必须分开：
        //   1) 先在**模型自己的**坐标系里绕 Y 自转（轴传 Y，不是方块的实际轴）；
        //   2) 再用 PoseStack 绕方块中心把它摆到方块的实际轴上。
        // 如果先用方块轴自转、再摆正，转的就不是模型自己的轴了，看起来会歪。
        float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis);
        SuperByteBuffer buffer = CachedBuffers.partial(model.get(), state);
        buffer = KineticBlockEntityRenderer.kineticRotationTransform(
                buffer, be, Direction.Axis.Y, -angle, packedLight);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(90));
        } else if (axis == Direction.Axis.Z) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
        }
        poseStack.translate(-0.5, -0.5, -0.5);

        buffer.renderInto(poseStack, bufferSource.getBuffer(RenderType.cutoutMipped()));
        poseStack.popPose();
    }

    /** 齿轮的齿伸到方块外，包围盒放宽一格，免得被视锥/距离裁掉。 */
    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(8);
    }
}
