package committee.nova.avaritia_singularity_stress.content.wireless;

import net.minecraft.util.Mth;

/**
 * 无线配置界面(B9 / B10 / B11 共用)需要知道的设备种类。
 * <p>
 * 三台机器的界面共用同一张面板贴图(I3 频率卡那张),但字段并不相同:
 * 只有 B10 无线应力传输器有"数量"字段,而它的两个字段含义还随角色变化
 * (输出端 = 送出上限;接收端 = 接收上限 + 优先级),所以界面必须先知道
 * "这是哪台设备、是哪一端"才能决定画什么。
 * <p>
 * 这些信息在客户端从<b>客户端方块实体</b>读取(见
 * {@link IWirelessConfigHolder#guiKind()}),不走网络包 —— 开界面时 BE 已经在客户端了。
 */
public enum WirelessGuiKind {

    /** B9 无线物品传输漏斗:只有频率 + 频道可见性。 */
    ITEM_FUNNEL,

    /** B10 无线应力传输器:频率 + 可见性 + 数量字段。 */
    STRESS_BRIDGE;

    // ===== B11 无线流体传输器：整体雪藏（枚举常量一并注释，恢复时记得补回 ";" 的位置）=====
    // /** B11 无线流体传输器:只有频率 + 频道可见性。 */
    // FLUID_TRANSMITTER;

    /** @return 本设备是否显示"数量"字段(当前只有 B10 需要)。 */
    public boolean hasAmounts() {
        return this == STRESS_BRIDGE;
    }

    /** 越界序号回退到 {@link #ITEM_FUNNEL},避免旧存档/异常数据导致崩溃。 */
    public static WirelessGuiKind byIndex(int index) {
        WirelessGuiKind[] values = values();
        return values[Mth.clamp(index, 0, values.length - 1)];
    }
}
