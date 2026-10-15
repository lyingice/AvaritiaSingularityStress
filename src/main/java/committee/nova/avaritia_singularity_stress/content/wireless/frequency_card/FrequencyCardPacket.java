package committee.nova.avaritia_singularity_stress.content.wireless.frequency_card;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/**
 * 客户端 → 服务端：频率卡 GUI 的所有操作。
 * <p>
 * Action 枚举决定 {@code value} 的语义。
 */
public record FrequencyCardPacket(int handOrdinal, Action action, int value)
        implements CustomPacketPayload {

    public enum Action {
        /** value = 新频率 */
        SET_FREQUENCY,
        /** value = 新可见性序号 */
        SET_VISIBILITY,
        /** value = 预设索引（0~5）；把当前频率存进该槽 */
        SAVE_PRESET,
        /** value = 预设索引（0~5）；把该槽读进当前频率 */
        LOAD_PRESET
    }

    public static final CustomPacketPayload.Type<FrequencyCardPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    AvaritiaSingularityStressMod.MODID, "frequency_card_action"));

    public static final StreamCodec<FriendlyByteBuf, FrequencyCardPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FrequencyCardPacket::handOrdinal,
                    ByteBufCodecs.VAR_INT, p -> p.action().ordinal(),
                    ByteBufCodecs.VAR_INT, FrequencyCardPacket::value,
                    (h, a, v) -> new FrequencyCardPacket(h, Action.values()[a], v));

    public static final IPayloadHandler<FrequencyCardPacket> HANDLER = FrequencyCardPacket::handle;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void handle(FrequencyCardPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            InteractionHand hand = packet.handOrdinal() == 1
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack card = player.getItemInHand(hand);
            if (!(card.getItem() instanceof WirelessFrequencyCardItem)) {
                return;
            }

            switch (packet.action()) {
                case SET_FREQUENCY ->
                        WirelessFrequencyCardItem.setFrequency(card, packet.value());
                case SET_VISIBILITY ->
                        WirelessFrequencyCardItem.setVisibility(card,
                                IFrequencyBindable.ChannelVisibility.byIndex(packet.value()));
                case SAVE_PRESET -> {
                    if (packet.value() < 0 || packet.value() >= FrequencyCardMenu.PRESET_COUNT) {
                        return;
                    }
                    WirelessFrequencyCardItem.savePreset(card, packet.value());
                }
                case LOAD_PRESET -> {
                    if (packet.value() < 0 || packet.value() >= FrequencyCardMenu.PRESET_COUNT) {
                        return;
                    }
                    WirelessFrequencyCardItem.loadPreset(card, packet.value());
                }
            }

            // 让服务端 Menu 的 ContainerData 重新装载卡片值
            if (player.containerMenu instanceof FrequencyCardMenu menu) {
                for (int i = 0; i < FrequencyCardMenu.DATA_SIZE; i++) {
                    menu.setData(i, readData(card, i));
                }
            }
            player.containerMenu.broadcastChanges();
        });
    }

    private static int readData(ItemStack card, int slot) {
        if (slot == 0) {
            return WirelessFrequencyCardItem.getFrequency(card);
        }
        if (slot == 1) {
            return WirelessFrequencyCardItem.getVisibility(card).ordinal();
        }
        int preset = (slot - 2) / 2;
        boolean isFrequency = (slot - 2) % 2 == 0;
        if (preset < 0 || preset >= FrequencyCardMenu.PRESET_COUNT) {
            return 0;
        }
        return isFrequency
                ? WirelessFrequencyCardItem.getPresetFrequency(card, preset)
                : WirelessFrequencyCardItem.getPresetVisibility(card, preset).ordinal();
    }
}