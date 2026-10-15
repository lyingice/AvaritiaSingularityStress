package committee.nova.avaritia_singularity_stress.content.wireless;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 无线系统可配置参数（COMMON config，双端共用）。
 * <p>
 * 与 {@code SingularityConfig} 同一套写法：
 * <b>所有 getter 都必须带 try/catch 未加载回退</b>，否则世界装配阶段的早期调用会抛
 * {@code IllegalStateException}。
 * <p>
 * 三个距离上限严格按最终规格：B9=64 / B10=64 / B11=16。
 */
public final class WirelessConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** B9 无线物品传输漏斗的有效距离（格）。 */
    private static final ModConfigSpec.DoubleValue ITEM_FUNNEL_RANGE = BUILDER
            .comment("B9 wireless item funnel: maximum pairing distance in blocks.",
                    "Default: 64")
            .defineInRange("itemFunnelRange", 64.0D, 1.0D, 512.0D);

    /** B10 无线应力传输器的有效距离（格）。 */
    private static final ModConfigSpec.DoubleValue STRESS_BRIDGE_RANGE = BUILDER
            .comment("B10 wireless stress bridge: maximum pairing distance in blocks.",
                    "Default: 64")
            .defineInRange("stressBridgeRange", 64.0D, 1.0D, 512.0D);

    // [B11 雪藏] B11 无线流体传输器有效距离：整体雪藏（配置项一并取消注册）。
    // /** B11 无线流体传输器的有效距离（格）。 */
    // private static final ModConfigSpec.DoubleValue FLUID_TRANSMITTER_RANGE = BUILDER
    //         .comment("B11 wireless fluid transmitter: maximum pairing distance in blocks.",
    //                 "Default: 16")
    //         .defineInRange("fluidTransmitterRange", 16.0D, 1.0D, 512.0D);

    /** 频率卡 GUI 允许输入的上限；与 {@link IFrequencyBindable#MAX_FREQUENCY} 保持一致。 */
    private static final ModConfigSpec.IntValue MAX_FREQUENCY = BUILDER
            .comment("Highest channel frequency accepted by the wireless frequency card.",
                    "Default: 9999")
            .defineInRange("maxFrequency", IFrequencyBindable.MAX_FREQUENCY, 0, 9999);

    /** config 规格，在 mod 构造函数中注册。 */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private WirelessConfig() {
    }

    // ================= getter（config 未加载时回退默认值） =================

    /** B9 漏斗配对距离（格），默认 64。 */
    public static double itemFunnelRange() {
        return d(ITEM_FUNNEL_RANGE, 64.0D);
    }

    /** B10 应力桥接器配对距离（格），默认 64。 */
    public static double stressBridgeRange() {
        return d(STRESS_BRIDGE_RANGE, 64.0D);
    }

    // [B11 雪藏] B11 流体传输器配对距离 getter：整体雪藏。
    // /** B11 流体传输器配对距离（格），默认 16。 */
    // public static double fluidTransmitterRange() {
    //     return d(FLUID_TRANSMITTER_RANGE, 16.0D);
    // }

    /** 频率上限，默认 9999。 */
    public static int maxFrequency() {
        try {
            return MAX_FREQUENCY.get();
        } catch (IllegalStateException e) {
            return IFrequencyBindable.MAX_FREQUENCY;
        }
    }

    private static double d(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            // config 尚未加载（极早期调用）：回退默认值
            return fallback;
        }
    }
}
