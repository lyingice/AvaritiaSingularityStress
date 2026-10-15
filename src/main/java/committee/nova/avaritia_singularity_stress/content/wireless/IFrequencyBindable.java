package committee.nova.avaritia_singularity_stress.content.wireless;

import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * 本模组自定义的无线频率绑定契约。
 * <p>
 * <b>Create 里不存在这个接口</b>（已全库检索零命中），它是本工程的共享底层：
 * B9 无线物品漏斗 / B10 无线应力传输器 / B11 无线流体传输器 三者的方块实体都实现它，
 * I3 无线频率卡通过它读写频率。
 * <p>
 * 语义约定（最终版）：
 * <ul>
 *   <li>频率范围 <b>0~9999</b>（含端点），{@link #NO_FREQUENCY} 表示未绑定。</li>
 *   <li>频率与频道可见性、所有者 UUID 一起存在<b>方块 NBT</b>——不存物品、不存独立注册表。</li>
 *   <li>所有者 UUID 由放置方块的玩家写入，决定了私有时谁能与之配对。</li>
 * </ul>
 */
public interface IFrequencyBindable {

    /** 未绑定的频率值。 */
    int NO_FREQUENCY = -1;

    /** 频率上限（含）。 */
    int MAX_FREQUENCY = 9999;

    /** @return 当前频率，未绑定时为 {@link #NO_FREQUENCY}。 */
    int getFrequency();

    /** 写入频率；实现方负责 clamp 到 0~9999 并触发 setChanged/sendData。 */
    void setFrequency(int frequency);

    /** @return 频道可见性。 */
    ChannelVisibility getVisibility();

    /** 写入频道可见性；实现方负责触发 setChanged/sendData。 */
    void setVisibility(ChannelVisibility visibility);

    /** @return 绑定的所有者 UUID，未绑定时为 null。 */
    UUID getOwner();

    /** 写入所有者 UUID（放置时由方块写入）。 */
    void setOwner(UUID owner);

    /** 未绑定（无频率）时为 true。 */
    default boolean hasFrequency() {
        return getFrequency() != NO_FREQUENCY;
    }

    /**
     * 默认实现：把频率/可见性/所有者写进方块 NBT。
     * <p>
     * <b>必须同时写 clientPacket 分支</b>（即无论 clientPacket 真假都写），
     * 否则护目镜/GUI 在客户端读不到值。
     */
    default void writeFrequency(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("Frequency", getFrequency());
        tag.putInt("ChannelVisibility", getVisibility().ordinal());
        UUID owner = getOwner();
        if (owner != null) {
            tag.putUUID("ChannelOwner", owner);
        }
    }

    /** 默认实现：从方块 NBT 读回频率/可见性/所有者。 */
    default void readFrequency(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        setFrequency(tag.contains("Frequency") ? tag.getInt("Frequency") : NO_FREQUENCY);
        setVisibility(ChannelVisibility.byIndex(tag.getInt("ChannelVisibility")));
        setOwner(tag.hasUUID("ChannelOwner") ? tag.getUUID("ChannelOwner") : null);
    }

    /** 把任意输入 clamp 到合法频率区间。 */
    static int clampFrequency(int frequency) {
        if (frequency == NO_FREQUENCY) {
            return NO_FREQUENCY;
        }
        return Mth.clamp(frequency, 0, MAX_FREQUENCY);
    }

    /**
     * 频道可见性。
     * <ul>
     *   <li>{@link #PRIVATE}：只与<b>同一所有者 UUID</b> 的对端配对。</li>
     *   <li>{@link #PUBLIC}：任意玩家的同频率对端都可配对。</li>
     * </ul>
     */
    enum ChannelVisibility {
        PRIVATE,
        PUBLIC;

        public static ChannelVisibility byIndex(int index) {
            ChannelVisibility[] values = values();
            return values[Mth.clamp(index, 0, values.length - 1)];
        }
    }
}
