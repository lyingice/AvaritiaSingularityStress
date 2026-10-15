package committee.nova.avaritia_singularity_stress.init.client.visual;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.init.client.renderer.ExtremeCrafterVisualSet;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

import java.util.function.Consumer;

/**
 * 终焉合成器家族的 Flywheel 可视化:一个随动力旋转的齿轮。
 * <p>
 * 齿轮模型由 {@link ExtremeCrafterVisualSet} 提供,因此同一个 Visual 类同时服务
 * 终焉动力合成器与终焉锻造器(两者齿轮纹理相同)。
 */
public class ExtremeCrafterVisual<T extends ExtremeMechanicalCrafterBlockEntity>
        extends KineticBlockEntityVisual<T> {

    private final RotatingInstance rotatingModel;

    public ExtremeCrafterVisual(
            VisualizationContext ctx,
            T be,
            float partialTick,
            ExtremeCrafterVisualSet visuals
    ) {
        super(ctx, be, partialTick);
        rotatingModel = (RotatingInstance) instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(visuals.cogwheel()))
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
