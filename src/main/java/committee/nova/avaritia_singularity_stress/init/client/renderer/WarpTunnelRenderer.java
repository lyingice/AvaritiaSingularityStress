package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.FlapStuffs;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel.WarpTunnelBlockEntity;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * B15 空间折跃隧道·方块实体渲染器。
 *
 * <h2>软门帘</h2>
 * 原样抄 Create 的 {@code BeltTunnelRenderer}：
 * {@code CachedBuffers.partial(AllPartialModels.BELT_TUNNEL_FLAP, state)} +
 * {@code FlapStuffs.renderFlaps(ms, vb, flapBuffer, FlapStuffs.TUNNEL_PIVOT, direction, f, 0, light)}。
 * 客户端 flaps 为空时按静止状态画两片兜底。
 *
 * <h2>四个过滤槽</h2>
 * 交给 Create 自己的过滤渲染链：{@code super.renderSafe} →
 * {@code FilteringRenderer.renderOnBlockEntity}，槽位定位器是 {@code BrassTunnelFilterSlot}
 * （见 {@code WarpTunnelBlockEntity.createSidedFilter}）。白色描边与悬停提示由
 * {@code FilteringRenderer.tick} 的 ValueBox 负责，所以这里<b>不再自绘</b>槽位图标，
 * 否则会和原生那份叠成两层。
 */
public class WarpTunnelRenderer extends SmartBlockEntityRenderer<WarpTunnelBlockEntity> {

    /** 一次性诊断开关：首个被渲染的隧道会把 BER 状态打进日志，方便确认渲染器到底有没有被调用。 */
    private static boolean diagnosticsLogged;

    private final BlockEntityRendererProvider.Context context;

    public WarpTunnelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected void renderSafe(WarpTunnelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
            int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        if (!be.hasLevel()) {
            return;
        }
        if (!diagnosticsLogged) {
            diagnosticsLogged = true;
            committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod.LOGGER.info(
                    "[warp_tunnel] BER active at {} flaps={} links={}",
                    be.getBlockPos(), be.flaps.keySet(), be.getFaceFrequencies().keySet());
        }
        renderCurtains(be, partialTicks, ms, buffer, light);
        // 过滤槽（四个白点 + 物品图标）现在交给 Create 的过滤渲染：
        // super.renderSafe → FilteringRenderer.renderOnBlockEntity，槽位定位器是 BrassTunnelFilterSlot。
        // 以前这里自绘一份，会和原生那份叠成两层图标。
        renderChannelText(be, ms, buffer, light);
    }

    // ===================== 软门帘（Create BeltTunnelRenderer 同款） =====================

    private void renderCurtains(WarpTunnelBlockEntity be, float partialTicks, PoseStack ms,
            MultiBufferSource buffer, int light) {
        SuperByteBuffer flapBuffer = CachedBuffers.partial(AllPartialModels.BELT_TUNNEL_FLAP, be.getBlockState());
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        boolean anyFlap = false;
        for (Direction direction : Iterate.directions) {
            if (!be.flaps.containsKey(direction)) {
                continue;
            }
            float flapness = be.flaps.get(direction).getValue(partialTicks);
            FlapStuffs.renderFlaps(ms, vb, flapBuffer, FlapStuffs.TUNNEL_PIVOT, direction, flapness, 0, light);
            anyFlap = true;
        }
        if (!anyFlap) {
            Direction.Axis axis = be.getBlockState().getValue(BeltTunnelBlock.HORIZONTAL_AXIS);
            Direction positive = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            FlapStuffs.renderFlaps(ms, vb, flapBuffer, FlapStuffs.TUNNEL_PIVOT, positive, 0, 0, light);
            FlapStuffs.renderFlaps(ms, vb, flapBuffer, FlapStuffs.TUNNEL_PIVOT, positive.getOpposite(), 0, 0, light);
        }
    }

    // ===================== 频道号 / 状态 =====================

    private void renderChannelText(WarpTunnelBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        int linkCount = be.getFaceFrequencies().size();
        String status = statusLabel(be.getStatus(), be);
        drawBillboardText("LINKS " + linkCount, 0xFFFFFF, 0.62f, ms, buffer, light);
        if (!status.isEmpty()) {
            drawBillboardText(status, statusColor(be.getStatus()), 0.30f, ms, buffer, light);
        }
    }

    private static String statusLabel(String status, WarpTunnelBlockEntity be) {
        return switch (status) {
            case "warping" -> "warp";
            // B15 分流版：不折跃、从出向面直接带出去的物品
            case "local" -> "local pass";
            // 待吐出缓冲：折跃过来的货正在往外送 / 送不出去
            case "receiving" -> "receiving";
            case "output_blocked" -> "exit: blocked";
            case "out_of_range" -> "out of range";
            case "logistics" -> "belt";
            case "blocked_filter" -> "no filter match";
            case "logistics_blocked" -> "logistics blocked";
            case "exit_no_belt" -> "exit: no belt";
            case "exit_blocked" -> "exit: belt full";
            case "unbound" -> "no channel";
            case "no_peer" -> "no peer";
            case "ambiguous" -> "channel conflict";
            case "bad_peer" -> "bad peer";
            case "peer_unloaded" -> "peer unloaded";
            case "powered" -> "redstone locked";
            case "logistics_no_belt" -> "no belt below";
            default -> "";
        };
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "warping" -> 0xC08BFF;
            case "local" -> 0x7CD7FF;
            case "logistics" -> 0x7CFC7C;
            case "blocked_filter", "logistics_blocked", "exit_no_belt", "exit_blocked",
                    "peer_unloaded", "logistics_no_belt", "ambiguous", "bad_peer", "out_of_range" -> 0xFF6B6B;
            default -> 0xCCCCCC;
        };
    }

    private void drawBillboardText(String text, int color, float y, PoseStack ms, MultiBufferSource buffer,
            int light) {
        Camera camera = context.getBlockEntityRenderDispatcher().camera;
        if (camera == null || text.isEmpty()) {
            return;
        }
        Font font = context.getFont();
        ms.pushPose();
        ms.translate(0.5, 1.30 + y, 0.5);
        ms.mulPose(camera.rotation());
        ms.scale(-0.02f, -0.02f, 0.02f);
        float half = font.width(text) / 2f;
        font.drawInBatch(Component.literal(text), -half, 0, color, true, ms.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0x40000000, light);
        ms.popPose();
    }

    private void drawTextAt(String text, int color, double x, double y, double z, float scale, PoseStack ms,
            MultiBufferSource buffer, int light) {
        Camera camera = context.getBlockEntityRenderDispatcher().camera;
        if (camera == null) {
            return;
        }
        Font font = context.getFont();
        ms.pushPose();
        ms.translate(x, y, z);
        ms.mulPose(camera.rotation());
        ms.scale(-scale, -scale, scale);
        float half = font.width(text) / 2f;
        font.drawInBatch(Component.literal(text), -half, 0, color, false, ms.last().pose(), buffer,
                Font.DisplayMode.SEE_THROUGH, 0, light);
        ms.popPose();
    }
}
