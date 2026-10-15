package committee.nova.avaritia_singularity_stress.content.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 三种无线共用的配置读写契约。
 * <p>
 * B9 / B10 / B11 的方块实体都实现它，从而共用同一套
 * {@code WirelessConfigMenu} + {@code WirelessConfigScreen}，
 * 不必为每台机器各写一份 GUI。
 * <p>
 * 同步策略：GUI 打开时把字段装进 {@code SimpleContainerData} 下发；
 * 运行中玩家改值 → 发 {@code WirelessConfigPacket} → 服务端写回 BE 并刷新 data。
 * 所有值都是小整数（频率 ≤9999，数量 ≤ 整数上限），
 * 注意 {@code ContainerData} 在网络上被压成 SHORT，所以<b>不要</b>往里塞大数。
 */
public interface IWirelessConfigHolder {

    // ===================== 频率（共享底层） =====================

    /** @return 当前频率，未绑定时为 {@link IFrequencyBindable#NO_FREQUENCY}。 */
    int getFrequency();

    /** 写入频率；实现方负责 setChanged + sendData。 */
    void setFrequency(int frequency);

    /** @return 频道可见性。 */
    IFrequencyBindable.ChannelVisibility getVisibility();

    /** 写入频道可见性；实现方负责 setChanged + sendData。 */
    void setVisibility(IFrequencyBindable.ChannelVisibility visibility);

    // ===================== 数量字段（B10 用） =====================

    /**
     * @return 第一个“数量”字段。B10 输出端 = 每个接收端最多分多少 SU；
     *         B10 接收端 = 自己最多接收多少 SU。
     */
    default int getPrimaryAmount() {
        return 0;
    }

    default void setPrimaryAmount(int value) {
    }

    /** @return 第二字段。B10 接收端 = 优先级（越大越优先）。 */
    default int getSecondaryAmount() {
        return 0;
    }

    default void setSecondaryAmount(int value) {
    }

    // ===================== 菜单定位 =====================

    /** @return 本 BE 的坐标，供菜单在客户端查找自身。 */
    BlockPos getBlockPos();

    // ===================== 界面布局提示(客户端也要读,故不放进 ContainerData) =====================

    /**
     * @return 本设备的界面种类，决定界面显示哪些字段。
     *         B9 / B11 用默认值(只有频率)，B10 覆写为 {@link WirelessGuiKind#STRESS_BRIDGE}。
     */
    default WirelessGuiKind guiKind() {
        return WirelessGuiKind.ITEM_FUNNEL;
    }

    /**
     * @return 是否输出端(仅 B10 / B11 有角色之分；B9 恒为 false)。
     *         界面用它决定"数量"字段的标签(送出上限 / 接收上限)。
     */
    default boolean guiOutputSide() {
        return false;
    }

    /**
     * 默认实现：让菜单能从任意方块实体取到配置读写入口。
     *
     * @param blockEntity 候选方块实体
     * @return 实现了本接口则返回其自身，否则 null
     */
    static IWirelessConfigHolder of(BlockEntity blockEntity) {
        return blockEntity instanceof IWirelessConfigHolder holder ? holder : null;
    }
}
