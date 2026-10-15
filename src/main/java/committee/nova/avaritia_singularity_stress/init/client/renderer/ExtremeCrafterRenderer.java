package committee.nova.avaritia_singularity_stress.init.client.renderer;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;
import static com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.standardKineticRotationTransform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity.Phase;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler.GroupedItems;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 终焉合成器家族的方块实体渲染器。
 * <p>
 * 逻辑完整复制原版 {@code MechanicalCrafterRenderer}:物品基准位姿、导出推进、
 * 合成三段动画、只渲染中心格、导出深度错位、齿轮 fallback、盖板条件、
 * 皮带 4 帧 UV 动画、以及 facing + pointing 双旋转。
 * <p>
 * 具体的部件模型与动画贴图由 {@link ExtremeCrafterVisualSet} 注入,
 * 因此同一个渲染器同时服务终焉动力合成器与终焉锻造器。
 */
public class ExtremeCrafterRenderer<T extends ExtremeMechanicalCrafterBlockEntity>
        extends SafeBlockEntityRenderer<T> {

    private final ExtremeCrafterVisualSet visuals;

    public ExtremeCrafterRenderer(BlockEntityRendererProvider.Context context,
                                  ExtremeCrafterVisualSet visuals) {
        this.visuals = visuals;
    }

    @Override
    protected void renderSafe(T be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        ms.pushPose();
        Direction facing = be.getBlockState().getValue(HORIZONTAL_FACING);
        Vec3 vec = Vec3.atLowerCornerOf(facing.getNormal())
                .scale(.58)
                .add(.5, .5, .5);

        if (be.getPhase() == Phase.EXPORTING) {
            Direction targetDirection = ExtremeMechanicalCrafterBlock.getTargetDirection(be.getBlockState());
            float progress =
                    Mth.clamp((1000 - be.getCountDown() + be.getCountDownSpeed() * partialTicks) / 1000f, 0, 1);
            vec = vec.add(Vec3.atLowerCornerOf(targetDirection.getNormal())
                    .scale(progress * .75f));
        }

        ms.translate(vec.x, vec.y, vec.z);
        ms.scale(1 / 2f, 1 / 2f, 1 / 2f);
        float yRot = AngleHelper.horizontalAngle(facing);
        ms.mulPose(Axis.YP.rotationDegrees(yRot));
        renderItems(be, partialTicks, ms, buffer, light, overlay);
        ms.popPose();

        renderFast(be, partialTicks, ms, buffer, light);
    }

    public void renderItems(T be, float partialTicks, PoseStack ms,
                            MultiBufferSource buffer, int light, int overlay) {
        if (be.getPhase() == Phase.IDLE) {
            ItemStack stack = be.getInventory().getItem(0);
            if (!stack.isEmpty()) {
                ms.pushPose();
                ms.translate(0, 0, -1 / 256f);
                ms.mulPose(Axis.YP.rotationDegrees(180));
                Minecraft.getInstance()
                        .getItemRenderer()
                        .renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
                ms.popPose();
            }
            return;
        }

        // 非空闲:渲染聚合网格
        GroupedItems items = be.getGroupedItems();
        float distance = .5f;

        ms.pushPose();

        if (be.getPhase() == Phase.CRAFTING) {
            items = be.getGroupedItemsBeforeCraft();
            items.calcStats();
            float progress =
                    Mth.clamp((2000 - be.getCountDown() + be.getCountDownSpeed() * partialTicks) / 1000f, 0, 1);
            float earlyProgress = Mth.clamp(progress * 2, 0, 1);
            float lateProgress = Mth.clamp(progress * 2 - 1, 0, 1);

            ms.scale(1 - lateProgress, 1 - lateProgress, 1 - lateProgress);
            Vec3 centering =
                    new Vec3(-items.minX + (-items.width + 1) / 2f, -items.minY + (-items.height + 1) / 2f, 0)
                            .scale(earlyProgress);
            ms.translate(centering.x * .5f, centering.y * .5f, 0);
            distance += (-4 * (progress - .5f) * (progress - .5f) + 1) * .25f;
        }

        boolean onlyRenderFirst = be.getPhase() == Phase.INSERTING
                || be.getPhase() == Phase.CRAFTING && be.getCountDown() < 1000;
        final float spacing = distance;
        items.grid.forEach((pair, stack) -> {
            if (onlyRenderFirst && (pair.getLeft() != 0 || pair.getRight() != 0)) {
                return;
            }

            ms.pushPose();
            Integer x = pair.getKey();
            Integer y = pair.getValue();
            ms.translate(x * spacing, y * spacing, 0);

            int offset = 0;
            if (be.getPhase() == Phase.EXPORTING
                    && be.getBlockState().hasProperty(ExtremeMechanicalCrafterBlock.POINTING)) {
                Pointing value = be.getBlockState().getValue(ExtremeMechanicalCrafterBlock.POINTING);
                offset = value == Pointing.UP ? -1 : value == Pointing.LEFT ? 2 : value == Pointing.RIGHT ? -2 : 1;
            }

            TransformStack.of(ms)
                    .rotateYDegrees(180)
                    .translate(0, 0, (x + y * 3 + offset * 9) / 1024f);
            Minecraft.getInstance()
                    .getItemRenderer()
                    .renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
            ms.popPose();
        });

        ms.popPose();

        // 合成结果:前半段绕 Z 转两圈并放大,后半段回落
        if (be.getPhase() == Phase.CRAFTING) {
            items = be.getGroupedItems();
            float progress =
                    Mth.clamp((1000 - be.getCountDown() + be.getCountDownSpeed() * partialTicks) / 1000f, 0, 1);
            float earlyProgress = Mth.clamp(progress * 2, 0, 1);
            float lateProgress = Mth.clamp(progress * 2 - 1, 0, 1);

            ms.mulPose(Axis.ZP.rotationDegrees(earlyProgress * 2 * 360));
            float upScaling = earlyProgress * 1.125f;
            float downScaling = 1 + (1 - lateProgress) * .125f;
            ms.scale(upScaling, upScaling, upScaling);
            ms.scale(downScaling, downScaling, downScaling);

            items.grid.forEach((pair, stack) -> {
                if (pair.getLeft() != 0 || pair.getRight() != 0) {
                    return;
                }
                ms.pushPose();
                ms.mulPose(Axis.YP.rotationDegrees(180));
                Minecraft.getInstance()
                        .getItemRenderer()
                        .renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
                ms.popPose();
            });
        }
    }

    public void renderFast(T be, float partialTicks, PoseStack ms,
                           MultiBufferSource buffer, int light) {
        BlockState blockState = be.getBlockState();
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        // 齿轮 fallback:仅在 Flywheel 未接管可视化时渲染,避免与 Visual 重复
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            SuperByteBuffer superBuffer = CachedBuffers.partial(visuals.cogwheel(), blockState);
            standardKineticRotationTransform(superBuffer, be, light);
            superBuffer.rotateCentered(
                    (float) (blockState.getValue(HORIZONTAL_FACING).getAxis() != Direction.Axis.X ? 0 : Math.PI / 2),
                    Direction.UP);
            superBuffer.rotateCentered((float) (Math.PI / 2), Direction.EAST);
            superBuffer.renderInto(ms, vb);
        }

        Direction targetDirection = ExtremeMechanicalCrafterBlock.getTargetDirection(blockState);
        BlockPos pos = be.getBlockPos();

        if ((be.isCovered() || be.getPhase() != Phase.IDLE)
                && be.getPhase() != Phase.CRAFTING && be.getPhase() != Phase.INSERTING) {
            SuperByteBuffer lidBuffer = renderAndTransform(visuals.lid(), blockState);
            lidBuffer.light(light)
                    .renderInto(ms, vb);
        }

        if (ExtremeMechanicalCrafterBlock.isValidTarget(be.getLevel(), pos.relative(targetDirection), blockState)) {
            SuperByteBuffer beltBuffer = renderAndTransform(visuals.belt(), blockState);
            SuperByteBuffer beltFrameBuffer = renderAndTransform(visuals.beltFrame(), blockState);

            if (be.getPhase() == Phase.EXPORTING) {
                int textureIndex = (int) ((be.getCountDownSpeed() / 128f * AnimationTickHolder.getTicks()));
                beltBuffer.shiftUVtoSheet(visuals.thingies(), (textureIndex % 4) / 4f, 0, 1);
            }

            beltBuffer.light(light)
                    .renderInto(ms, vb);
            beltFrameBuffer.light(light)
                    .renderInto(ms, vb);
        } else {
            SuperByteBuffer arrowBuffer = renderAndTransform(visuals.arrow(), blockState);
            arrowBuffer.light(light)
                    .renderInto(ms, vb);
        }
    }

    /**
     * 动态部件的统一旋转:先绕 Y 轴旋转(水平角 + 90°),再绕 EAST 轴旋转 pointing 的角度。
     * 这是 blockstate 不需要 pointing 模型变体的原因。
     */
    private SuperByteBuffer renderAndTransform(PartialModel renderBlock, BlockState crafterState) {
        SuperByteBuffer buffer = CachedBuffers.partial(renderBlock, crafterState);
        float xRot = crafterState.getValue(ExtremeMechanicalCrafterBlock.POINTING).getXRotation();
        float yRot = AngleHelper.horizontalAngle(crafterState.getValue(HORIZONTAL_FACING));
        buffer.rotateCentered((float) ((yRot + 90) / 180 * Math.PI), Direction.UP);
        buffer.rotateCentered((float) ((xRot) / 180 * Math.PI), Direction.EAST);
        return buffer;
    }
}
