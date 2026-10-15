package committee.nova.avaritia_singularity_stress.init.client.visualizer;

import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller.SingularityRotationSpeedControllerBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity_stabilizer.SingularityStabilizerBlockEntity;
import committee.nova.avaritia_singularity_stress.init.client.renderer.ExtremeCrafterVisualSet;
import committee.nova.avaritia_singularity_stress.init.client.visual.ExtremeCrafterVisual;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

/**
 * 终焉合成器家族的 Flywheel 可视化器注册。
 * <p>
 * 因为齿轮纹理两种方块相同、渲染逻辑也相同,这里用同一个泛型实现
 * 为两个方块实体类型各注册一个实例,只是注入不同的
 * {@link ExtremeCrafterVisualSet}。
 * <p>
 * 注意:不能用单例 enum 实现——两个方块实体类型需要不同的泛型参数,
 * 单个 enum 常量无法同时满足。
 */
public final class ModVisualizers {

    private ModVisualizers() {
    }

    private record Visualizer<T extends ExtremeMechanicalCrafterBlockEntity>(
            ExtremeCrafterVisualSet visuals
    ) implements BlockEntityVisualizer<T> {

        @Override
        public BlockEntityVisual<? super T> createVisual(
                VisualizationContext ctx, T be, float partialTick) {
            return new ExtremeCrafterVisual<>(ctx, be, partialTick, visuals);
        }

        @Override
        public boolean skipVanillaRender(T be) {
            return false;
        }
    }

    public static void register() {
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.EXTREME_MECHANICAL_CRAFTER.get(),
                new Visualizer<>(ExtremeCrafterVisualSet.MECHANICAL)
        );
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.EXTREME_SMITHING_CRAFTER.get(),
                new Visualizer<>(ExtremeCrafterVisualSet.SMITHING)
        );
        // B6:输出轴沿用 Create 转速控制器的旋转轴可视化;支架由 BER 绘制,故 skipVanillaRender=false。
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(),
                new SimpleBlockEntityVisualizer<SingularityRotationSpeedControllerBlockEntity>(
                        (ctx, be, pt) -> SingleAxisRotatingVisual.shaft(ctx, be, pt),
                        be -> false
                )
        );
        // B14:整个方块模型都是旋转部件,旋转实例用自有 partial 模型;
        // 第二参 skipVanillaRender=true → Flywheel 隐藏静态 chunk 网格,防止与旋转实例重叠
        // (与 Create 对轴/齿轮的处理一致:整体旋转方块一律不保留静态网格)。
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.SINGULARITY_STABILIZER.get(),
                new SimpleBlockEntityVisualizer<SingularityStabilizerBlockEntity>(
                        SingleAxisRotatingVisual.of(ModPartialModels.SINGULARITY_STABILIZER),
                        be -> true
                )
        );
    }
}
