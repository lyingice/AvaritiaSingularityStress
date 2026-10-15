package committee.nova.avaritia_singularity_stress.content.wireless;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
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

/**
 * 客户端 → 服务端：提交某个无线方块（B9 / B10 / B11）的配置。
 * <p>
 * 字段全部是原始类型，用 {@link FriendlyByteBuf} 即可，不需要 RegistryFriendlyByteBuf。
 * <p>
 * <b>安全校验</b>（手抄 Create {@code BlockEntityConfigurationPacket.handle} 的逻辑，
 * 因为那个基类挂在 Create 自己的 registrar 上，本模组用不了）：
 * 旁观/冒险模式拒绝 → 距离校验 → 坐标处确实是目标 BE → 才写入。
 *
 * @param pos          目标方块坐标
 * @param frequency    频率（NO_FREQUENCY 表示未绑定）
 * @param visibility   可见性序号
 * @param amount1      第一个数量字段
 * @param amount2      第二个数量字段
 */
public record WirelessConfigPacket(BlockPos pos, int frequency, int visibility, int amount1, int amount2)
        implements CustomPacketPayload {

    /** 交互距离上限（格），与 Create 的 20 保持一致。 */
    private static final double MAX_RANGE = 20.0D;

    public static final CustomPacketPayload.Type<WirelessConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    AvaritiaSingularityStressMod.MODID, "set_wireless_config"));

    public static final StreamCodec<FriendlyByteBuf, WirelessConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, WirelessConfigPacket::pos,
                    ByteBufCodecs.VAR_INT, WirelessConfigPacket::frequency,
                    ByteBufCodecs.VAR_INT, WirelessConfigPacket::visibility,
                    ByteBufCodecs.VAR_INT, WirelessConfigPacket::amount1,
                    ByteBufCodecs.VAR_INT, WirelessConfigPacket::amount2,
                    WirelessConfigPacket::new);

    public static final IPayloadHandler<WirelessConfigPacket> HANDLER = WirelessConfigPacket::handle;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void handle(WirelessConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.isSpectator()) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            BlockPos pos = packet.pos();
            if (!level.isLoaded(pos)) {
                return;
            }
            if (!player.canInteractWithBlock(pos, MAX_RANGE)) {
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            IWirelessConfigHolder holder = IWirelessConfigHolder.of(blockEntity);
            if (holder == null) {
                return;
            }

            holder.setFrequency(packet.frequency());
            holder.setVisibility(IFrequencyBindable.ChannelVisibility.byIndex(packet.visibility()));
            holder.setPrimaryAmount(packet.amount1());
            holder.setSecondaryAmount(packet.amount2());

            // 把新值回灌给所有正在看这个 GUI 的玩家
            if (blockEntity instanceof com.simibubi.create.foundation.blockEntity.SyncedBlockEntity synced) {
                synced.sendData();
            }
            blockEntity.setChanged();
        });
    }
}
