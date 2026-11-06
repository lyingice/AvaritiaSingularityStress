package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import committee.nova.avaritia_singularity_stress.content.wireless.item_funnel.WirelessItemFunnelBlockEntity;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * B9 无线物品传输漏斗·方块实体渲染器。
 *
 * <h2>为什么必须挂这个（空实现也不行）</h2>
 * 漏斗沿用了 Create 黄铜漏斗的 {@code FilteringBehaviour}（见
 * {@code WirelessItemFunnelBlockEntity.addBehaviours}），而 Create 画过滤槽的唯一路径是
 * {@code SmartBlockEntityRenderer.renderSafe} → {@code FilteringRenderer.renderOnBlockEntity}
 * （Create 侧证据：{@code foundation/blockEntity/renderer/SmartBlockEntityRenderer.java:29-33}、
 * 黄铜漏斗自己挂的 {@code FunnelRenderer.java:24-42}）。
 * <p>
 * 我们的 BE 是新注册的类型、之前<b>没有挂任何 BER</b>，于是那条渲染链根本没人调用 ——
 * 表现就是"漏斗的过滤槽不见了"。
 *
 * <h2>与黄铜漏斗的差别</h2>
 * {@code FunnelRenderer} 除了 {@code super.renderSafe} 还要画 flap（{@code FlapStuffs.renderFlaps}）；
 * 本模组的漏斗没有 flap 字段（物品进出只在无线缓存里完成），所以这里只保留 super 那一段。
 * 将来若加 flap，照 {@code FunnelRenderer.java:32-41} 补，并把 buffer 换成
 * {@code RenderType.cutoutMipped()}（本漏斗贴图带透明像素，{@code solid} 会盖住）。
 */
public class WirelessItemFunnelRenderer extends SmartBlockEntityRenderer<WirelessItemFunnelBlockEntity> {

    public WirelessItemFunnelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
}
