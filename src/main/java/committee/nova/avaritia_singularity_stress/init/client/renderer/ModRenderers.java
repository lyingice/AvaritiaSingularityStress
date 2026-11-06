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
    }
}
