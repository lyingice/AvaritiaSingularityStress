package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import committee.nova.avaritia_singularity_stress.content.singularity_stabilizer.SingularityStabilizerRenderer;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ModRenderers {

    private ModRenderers() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.EXTREME_MECHANICAL_CRAFTER.get(),
                context -> new ExtremeCrafterRenderer<>(context, ExtremeCrafterVisualSet.MECHANICAL)
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.EXTREME_SMITHING_CRAFTER.get(),
                context -> new ExtremeCrafterRenderer<>(context, ExtremeCrafterVisualSet.SMITHING)
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(),
                SingularitySpeedControllerRenderer::new
        );
        // B14 奇点稳定器:方块状态模型为空壳,旋转几何由 rotor partial 提供(flywheel 不可用时兜底)。
        event.registerBlockEntityRenderer(
                ModBlockEntities.SINGULARITY_STABILIZER.get(),
                SingularityStabilizerRenderer::new
        );
    }
}
