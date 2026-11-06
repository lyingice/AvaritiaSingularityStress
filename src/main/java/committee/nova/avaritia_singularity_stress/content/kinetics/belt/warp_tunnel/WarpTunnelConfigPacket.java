package committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;

/**
 * 空间折跃隧道 GUI 的上行包：改某一面的频率，或切换「出口过滤」。
 * <p>
 * 为什么不走 ContainerData：这项数据和 B10 无线设备一样直接住在方块实体 NBT 里，
 * BE 的 {@code sendData()} 会把改动推给所有正在看这个界面的客户端，
 * 所以界面读客户端 BE 就是真值，不需要再压一遍 short（大频率会截断）。
 */
public record WarpTunnelConfigPacket(BlockPos pos, int face, int kind, int value)
        implements CustomPacketPayload {

    /** 交互距离上限（格），与 {@code WarpTunnelConfigMenu} 同源。 */
    public static final double MAX_RANGE = 20.0D;

    /** 改这一面的频率。 */
    public static final int KIND_FREQUENCY = 0;
    /** 切换出口过滤（value != 0 表示勾上）。 */
    public static final int KIND_EXPORT_FILTER = 1;

    public static final CustomPacketPayload.Type<WarpTunnelConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    AvaritiaSingularityStressMod.MODID, "set_warp_tunnel_config"));

    public static final StreamCodec<FriendlyByteBuf, WarpTunnelConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, WarpTunnelConfigPacket::pos,
                    ByteBufCodecs.VAR_INT, WarpTunnelConfigPacket::face,
                    ByteBufCodecs.VAR_INT, WarpTunnelConfigPacket::kind,
                    ByteBufCodecs.VAR_INT, WarpTunnelConfigPacket::value,
                    WarpTunnelConfigPacket::new);

    public static final IPayloadHandler<WarpTunnelConfigPacket> HANDLER = WarpTunnelConfigPacket::handle;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void handle(WarpTunnelConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            BlockPos pos = packet.pos();
            if (!level.isLoaded(pos) || !player.canInteractWithBlock(pos, MAX_RANGE)) {
                return;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof WarpTunnelBlockEntity tunnel)) {
                return;
            }
            if (packet.kind() == KIND_EXPORT_FILTER) {
                tunnel.setExportFilter(packet.value() != 0);
                return;
            }
            // 只有「有软帘门」的面才允许改频率：没带子进出的面在游戏里本来就不该配对
            net.minecraft.core.Direction face = net.minecraft.core.Direction.from3DDataValue(packet.face());
            if (!tunnel.hasBeltOn(face)) {
                return;
            }
            tunnel.setFaceFrequency(face, packet.value());
        });
    }
}
