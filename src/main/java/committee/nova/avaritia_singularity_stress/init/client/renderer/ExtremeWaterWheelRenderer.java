package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_water_wheel.ExtremeWaterWheelBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperBufferFactory;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B28 终焉水车渲染器：轮体完全模组本地化、固定贴图。
 * <p>
 * 与 Create {@code WaterWheelRenderer} 的区别：不再调用 {@code generateModel} 把 oak 模板
 * 重映射成材质贴图，而是直接用模组轮体 partial（{@link ModPartialModels#EXTREME_WATER_WHEEL}，
 * OBJ 几何复制自 Create，贴图固定指向 extreme_water_wheel/）。覆盖贴图即生效，无"右键换材质"。
 * 朝向变换沿用 Create 的 {@code rotateToFaceVertical}（WaterWheelRenderer.java:64-78）。
 */
public class ExtremeWaterWheelRenderer extends KineticBlockEntityRenderer<ExtremeWaterWheelBlockEntity> {

    public ExtremeWaterWheelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** 轮体含透明像素,显式用 cutout(父类默认从方块模型推断,这里直接指定更稳)。 */
    @Override
    protected RenderType getRenderType(ExtremeWaterWheelBlockEntity be, BlockState state) {
        return RenderType.cutout();
    }

    @Override
    protected SuperByteBuffer getRotatedModel(ExtremeWaterWheelBlockEntity be, BlockState state) {
        BakedModel model = ModPartialModels.EXTREME_WATER_WHEEL.get();
        Direction dir = state.getValue(WaterWheelBlock.FACING);
        PoseStack transform = CachedBuffers.rotateToFaceVertical(dir).get();
        return SuperBufferFactory.getInstance().createForBlock(model, Blocks.AIR.defaultBlockState(), transform);
    }
}
