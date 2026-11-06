package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge.WirelessStressBridgeBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B10 无线应力传输器：完整照搬 Create 创造马达的渲染。
 * <p>
 * 来源：{@code CreativeMotorRenderer}（allapis 6.0.11，
 * {@code com/simibubi/create/content/kinetics/motor/CreativeMotorRenderer.java}）——
 * 它只做一件事：{@code getRotatedModel} 返回 {@code SHAFT_HALF} 的 partial，
 * 由 {@link KineticBlockEntityRenderer} 统一按 {@code KineticBlockEntity.getAngle()} 旋转。
 * 方块几何（外壳/面板/轴孔）则由 blockstate 模型
 * {@code block/wireless_stress_bridge/block} 与 {@code block_vertical} 负责，
 * 这两个模型是 {@code create:block/creative_motor/block(_vertical)} 的复制件，
 * 纹理换到本包 {@code block/wireless_stress_bridge/*}，方便后期改。
 */
public class WirelessStressBridgeRenderer extends KineticBlockEntityRenderer<WirelessStressBridgeBlockEntity> {

    public WirelessStressBridgeRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(WirelessStressBridgeBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state);
    }
}
