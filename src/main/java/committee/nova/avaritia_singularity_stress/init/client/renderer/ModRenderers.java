package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
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
        // B26 炽骨立方合成器:复用同一渲染器,注入专属资源集合
        event.registerBlockEntityRenderer(
                ModBlockEntities.BLAZE_CUBE_CRAFTER.get(),
                context -> new ExtremeCrafterRenderer<>(context, ExtremeCrafterVisualSet.BLAZE_CUBE)
        );
        // B27 终焉钻头:模组本地渲染器(专属钻头 partial)
        event.registerBlockEntityRenderer(
                ModBlockEntities.EXTREME_DRILL.get(),
                ExtremeDrillRenderer::new
        );
        // B28 终焉水车:模组本地渲染器(轮体固定贴图,无材质重映射)
        event.registerBlockEntityRenderer(
                ModBlockEntities.EXTREME_WATER_WHEEL.get(),
                ExtremeWaterWheelRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(),
                SingularitySpeedControllerRenderer::new
        );
        // B14 奇点稳定器：改用与包壳齿轮**同一条**兜底路径（PonderKineticFallbackRenderer）。
        // 旧实现自写 getRotatedModel：先把 rotor 从 Y 摆到方块轴、再由父类绕方块轴自转，
        // 顺序与已验证可行的 fallback 相反，axis≠Y 时会"躺着转"（Ponder 里实测）。
        // 现在：先在模型自己的 Y 轴坐标系里自转，再用 PoseStack 摆到方块轴——与齿轮一致。
        event.registerBlockEntityRenderer(
                ModBlockEntities.SINGULARITY_STABILIZER.get(),
                context -> new PonderKineticFallbackRenderer<>(context, () -> ModPartialModels.SINGULARITY_STABILIZER)
        );
        // B15 空间折跃隧道:双槽图标 + 频道数字 + 状态文字。
        event.registerBlockEntityRenderer(
                ModBlockEntities.WARP_TUNNEL.get(),
                WarpTunnelRenderer::new
        );
        // B9 无线物品传输漏斗:只是为了让 Create 的 FilteringRenderer 有机会画过滤槽
        // (黄铜漏斗同款路径;之前没挂 BER,所以槽不显示)。见 WirelessItemFunnelRenderer 的注释。
        event.registerBlockEntityRenderer(
                ModBlockEntities.WIRELESS_ITEM_FUNNEL.get(),
                WirelessItemFunnelRenderer::new
        );
        // B10 无线应力传输器：复制创造马达的渲染（SHAFT_HALF 旋转轴）。
        event.registerBlockEntityRenderer(
                ModBlockEntities.WIRELESS_STRESS_BRIDGE_SENDER.get(),
                WirelessStressBridgeRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.WIRELESS_STRESS_BRIDGE_RECEIVER.get(),
                WirelessStressBridgeRenderer::new
        );
        // B18/B19 包壳传动件：包壳方块只有外壳模型，轴心/齿盘本来是 Flywheel visual 现画的。
        // Ponder 的假世界没有 VisualizationManager，visual 不参与，于是「封装后齿轮消失」。
        // 这些兜底渲染器只在非 ClientLevel 环境生效（见 PonderKineticFallbackRenderer），
        // 游戏内仍然交给 visual，不会画两遍。
        event.registerBlockEntityRenderer(
                ModBlockEntities.BLAZE_CUBE_ENCASED_SHAFT.get(),
                context -> new PonderKineticFallbackRenderer<>(context, () -> AllPartialModels.SHAFT));
        event.registerBlockEntityRenderer(
                ModBlockEntities.BLAZE_ENCASED_COGWHEEL.get(),
                context -> new PonderKineticFallbackRenderer<>(context, () -> ModPartialModels.BLAZE_COGWHEEL_GEAR));
        event.registerBlockEntityRenderer(
                ModBlockEntities.BLAZE_ENCASED_LARGE_COGWHEEL.get(),
                context -> new PonderKineticFallbackRenderer<>(context, () -> ModPartialModels.BLAZE_LARGE_COGWHEEL_GEAR));

        event.registerBlockEntityRenderer(
                ModBlockEntities.CRYSTAL_ENCASED_SHAFT.get(),
                context -> new PonderKineticFallbackRenderer<>(context, () -> AllPartialModels.SHAFT));
        event.registerBlockEntityRenderer(
                ModBlockEntities.CRYSTAL_ENCASED_COGWHEEL.get(),
                context -> new PonderKineticFallbackRenderer<>(context, () -> ModPartialModels.CRYSTAL_COGWHEEL_GEAR));
        event.registerBlockEntityRenderer(
                ModBlockEntities.CRYSTAL_ENCASED_LARGE_COGWHEEL.get(),
                context -> new PonderKineticFallbackRenderer<>(context, () -> ModPartialModels.CRYSTAL_LARGE_COGWHEEL_GEAR));
    }
}
