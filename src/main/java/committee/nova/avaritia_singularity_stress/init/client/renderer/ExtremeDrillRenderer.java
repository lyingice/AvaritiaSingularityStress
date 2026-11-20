package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import committee.nova.avaritia_singularity_stress.content.extreme_drill.ExtremeDrillBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B27 终焉钻头渲染器：镜像 Create {@code DrillRenderer}（DrillRenderer.java:27-30），
 * 仅把旋转钻头模型换成本模组专属 partial（占位贴图复制自 Create，可独立替换）。
 * 父类 {@code KineticBlockEntityRenderer.renderSafe} 在 Flywheel 接管时跳过旋转模型
 * （KineticBlockEntityRenderer.java:50），故与 Flywheel visual 不会重复绘制。
 */
public class ExtremeDrillRenderer extends KineticBlockEntityRenderer<ExtremeDrillBlockEntity> {

    public ExtremeDrillRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(ExtremeDrillBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(ModPartialModels.EXTREME_DRILL_HEAD, state);
    }
}
