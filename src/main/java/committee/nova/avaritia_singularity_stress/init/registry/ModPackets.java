package committee.nova.avaritia_singularity_stress.init.registry;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel.WarpTunnelConfigPacket;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigPacket;
import committee.nova.avaritia_singularity_stress.content.wireless.frequency_card.FrequencyCardPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ModPackets {

    private ModPackets() {
    }

    public static void init() {
        AvaritiaSingularityStressMod.addNetworkMessage(
                FrequencyCardPacket.TYPE, FrequencyCardPacket.STREAM_CODEC, FrequencyCardPacket.HANDLER);
        // B9 / B10 / B11 共用的方块配置包
        AvaritiaSingularityStressMod.addNetworkMessage(
                WirelessConfigPacket.TYPE, WirelessConfigPacket.STREAM_CODEC, WirelessConfigPacket.HANDLER);
        // B15 空间折跃隧道的配置包（改面频率 / 出口过滤）
        AvaritiaSingularityStressMod.addNetworkMessage(
                WarpTunnelConfigPacket.TYPE, WarpTunnelConfigPacket.STREAM_CODEC, WarpTunnelConfigPacket.HANDLER);

    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}