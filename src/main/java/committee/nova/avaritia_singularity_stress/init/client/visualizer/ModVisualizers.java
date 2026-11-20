package committee.nova.avaritia_singularity_stress.init.client.visualizer;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltVisual;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogVisual;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller.SingularityRotationSpeedControllerBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity_stabilizer.SingularityStabilizerBlockEntity;
import committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge.WirelessStressBridgeBlockEntity;
import committee.nova.avaritia_singularity_stress.init.client.renderer.ExtremeCrafterVisualSet;
import committee.nova.avaritia_singularity_stress.init.client.visual.ExtremeCrafterVisual;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.model.Models;
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
        // B26 炽骨立方合成器:同一 Visual 实现,注入专属齿轮模型
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.BLAZE_CUBE_CRAFTER.get(),
                new Visualizer<>(ExtremeCrafterVisualSet.BLAZE_CUBE)
        );
        // B27 终焉钻头:朝向旋转视觉(专属 partial),skipVanillaRender=false 与 Create 钻头一致
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.EXTREME_DRILL.get(),
                new SimpleBlockEntityVisualizer<committee.nova.avaritia_singularity_stress.content.extreme_drill.ExtremeDrillBlockEntity>(
                        com.simibubi.create.content.kinetics.base.OrientedRotatingVisual.of(ModPartialModels.EXTREME_DRILL_HEAD),
                        be -> false
                )
        );
        // B28 终焉水车:模组本地视觉(轮体固定贴图),skipVanillaRender=false
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.EXTREME_WATER_WHEEL.get(),
                new SimpleBlockEntityVisualizer<committee.nova.avaritia_singularity_stress.content.extreme_water_wheel.ExtremeWaterWheelBlockEntity>(
                        (ctx, be, pt) -> new committee.nova.avaritia_singularity_stress.init.client.visual.ExtremeWaterWheelVisual(ctx, be, pt),
                        be -> false
                )
        );
        // B15:门帘改成写进方块模型（见 models/block/warp_tunnel/*.json 的 curtain_* 元素），
        // 不再依赖 Flywheel 的 BeltTunnelVisual——那条路依赖 flaps 同步，实测没画出来。
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


        // B18 水晶矩阵机壳包壳齿轮：直接用 Create 现成的 EncasedCogVisual.small/large，
        // 它除了齿轮（SHAFTLESS_COGWHEEL / SHAFTLESS_LARGE_COGWHEEL）还会按
        // top_shaft/bottom_shaft 状态补 SHAFT_HALF 轴段——自己用 SingleAxisRotatingVisual.of 只能画齿轮，
        // 会缺轴、看起来像"方框套网格"（实机已见）。
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.CRYSTAL_ENCASED_COGWHEEL.get(),
                new SimpleBlockEntityVisualizer<SimpleKineticBlockEntity>(
                        (ctx, be, pt) -> new EncasedCogVisual(ctx, be, false, pt,
                                Models.partial(ModPartialModels.CRYSTAL_COGWHEEL_GEAR)),
                        be -> false));

        VisualizerRegistry.setVisualizer(
                ModBlockEntities.CRYSTAL_ENCASED_LARGE_COGWHEEL.get(),
                new SimpleBlockEntityVisualizer<SimpleKineticBlockEntity>(
                        (ctx, be, pt) -> new EncasedCogVisual(ctx, be, true, pt,
                                Models.partial(ModPartialModels.CRYSTAL_LARGE_COGWHEEL_GEAR)),
                        be -> false));

        // B19 炽骨立方机壳包壳齿轮（小 / 大）：与 B18 同构——壳由方块模型画，齿轮/轴段用 Create 的 EncasedCogVisual。
        // skipVanillaRender 与 Create 的 ENCASED_COGWHEEL 注册保持一致（AllBlockEntityTypes.java:283/290 都是 false）。
        // 小齿轮这里必须传 large=false：之前误写成 true，会让轴段多套一次 45° 偏转，
        // 齿轮与轴口对不齐（EncasedCogVisual.java:69-71 只在 large 时加 shaftAngleOffset）。
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.BLAZE_ENCASED_COGWHEEL.get(),
                new SimpleBlockEntityVisualizer<SimpleKineticBlockEntity>(
                        (ctx, be, pt) -> new EncasedCogVisual(ctx, be, false, pt,
                                Models.partial(ModPartialModels.BLAZE_COGWHEEL_GEAR)),
                        be -> false)
        );
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.BLAZE_ENCASED_LARGE_COGWHEEL.get(),
                new SimpleBlockEntityVisualizer<SimpleKineticBlockEntity>(
                        (ctx, be, pt) -> new EncasedCogVisual(ctx, be, true, pt,
                                Models.partial(ModPartialModels.BLAZE_LARGE_COGWHEEL_GEAR)),
                        be -> false)
        );

        // B19 炽骨立方机壳包壳轴：与 B18 轴同款视觉（壳由方块模型画，轴心用 Create 原版轴）。
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.BLAZE_CUBE_ENCASED_SHAFT.get(),
                new SimpleBlockEntityVisualizer<BracketedKineticBlockEntity>(
                        SingleAxisRotatingVisual.of(AllPartialModels.SHAFT),
                        be -> false
                )
        );
        // B18 水晶矩阵机壳包壳轴:壳由方块模型(chunk)画,轴心用 Create 原版轴模型——
        // 包壳轴本来就是"原轴 + 外壳",与 Create 的安山/黄铜包壳轴一致。
        // skipVanillaRender=true 只是不跑 Create 的 BER(ShaftRenderer),与 Create 对
        // ENCASED_SHAFT 的处理一致(AllBlockEntityTypes.java:275-281 的 .visual(..., false))。
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.CRYSTAL_ENCASED_SHAFT.get(),
                new SimpleBlockEntityVisualizer<BracketedKineticBlockEntity>(
                        SingleAxisRotatingVisual.of(AllPartialModels.SHAFT),
                        be -> false
                )
        );

        // B10 无线应力传输器：必须走 Flywheel 实例化，不能只挂 BER。
        // 原因：Flywheel 可用时 KineticBlockEntityRenderer.renderSafe 第一句就
        // `if (VisualizationManager.supportsVisualization(be.getLevel())) return;`
        // （KineticBlockEntityRenderer.java:61-62），只注册 WirelessStressBridgeRenderer
        // 等于轴永远不会被画（实测：方块底部只剩报文模型，没有任何轴段）。
        // 做法与创造马达逐字相同：AllBlockEntityTypes.java:263 的
        // `.visual(() -> OrientedRotatingVisual.of(AllPartialModels.SHAFT_HALF), false)`。
        // 之所以现在可以原样照抄、不用再自己拼 from/to：
        //   OrientedRotatingVisual.of() 写死 `from = SOUTH`、`to = FACING`
        //   （OrientedRotatingVisual.java:43-49），而 SHAFT_HALF 模型
        //   （create:block/shaft_half，几何 from [6,6,8] to [10,10,16]）正好占 +Z 那半边，
        //   所以 `to = FACING` 就是把半轴摆在 FACING 那一面。
        //   本方块的 hasShaftTowards 已改成 `face == FACING`
        //   （WirelessStressBridgeBlock.java，与 CreativeMotorBlock.java:38-41 一致），
        //   模型的端口面也在 FACING（模型是创造马达的逐元素复制），三者对齐。
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.WIRELESS_STRESS_BRIDGE_SENDER.get(),
                new SimpleBlockEntityVisualizer<WirelessStressBridgeBlockEntity>(
                        OrientedRotatingVisual.of(AllPartialModels.SHAFT_HALF),
                        be -> false
                )
        );
        VisualizerRegistry.setVisualizer(
                ModBlockEntities.WIRELESS_STRESS_BRIDGE_RECEIVER.get(),
                new SimpleBlockEntityVisualizer<WirelessStressBridgeBlockEntity>(
                        OrientedRotatingVisual.of(AllPartialModels.SHAFT_HALF),
                        be -> false
                )
        );
    }
}


