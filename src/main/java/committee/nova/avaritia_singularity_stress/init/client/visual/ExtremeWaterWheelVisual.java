package committee.nova.avaritia_singularity_stress.init.client.visual;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import committee.nova.avaritia_singularity_stress.content.extreme_water_wheel.ExtremeWaterWheelBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

import java.util.function.Consumer;

/**
 * B28 终焉水车 Flywheel 视觉：轮体完全模组本地化、固定贴图。
 * <p>
 * 结构镜像 {@code ExtremeCrafterVisual}，直接用模组轮体 partial
 * （{@link ModPartialModels#EXTREME_WATER_WHEEL}）建旋转实例，不做材质重映射。
 * 与 {@code ExtremeWaterWheelRenderer} 共用同一 partial，保证 Flywheel 开/关外观一致。
 */
public class ExtremeWaterWheelVisual extends KineticBlockEntityVisual<ExtremeWaterWheelBlockEntity> {

    private final RotatingInstance rotatingModel;

    public ExtremeWaterWheelVisual(VisualizationContext ctx, ExtremeWaterWheelBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);
        rotatingModel = (RotatingInstance) instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(ModPartialModels.EXTREME_WATER_WHEEL))
                .createInstance();
        rotatingModel.setup(be)
                .setPosition(getVisualPosition())
                .rotateToFace(rotationAxis())
                .setChanged();
    }

    @Override
    public void update(float pt) {
        rotatingModel.setup(blockEntity).setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(rotatingModel);
    }

    @Override
    protected void _delete() {
        rotatingModel.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(rotatingModel);
    }
}
