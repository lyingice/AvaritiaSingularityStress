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

    /** B15 空间折跃隧道的配对距离（格）。 */
    private static final ModConfigSpec.DoubleValue WARP_TUNNEL_RANGE = BUILDER
            .comment("B15 spatial fold tunnel: maximum pairing distance in blocks.",
                    "Default: 128")
            .defineInRange("warpTunnelRange", 128.0D, 1.0D, 4096.0D);

    /**
     * B15 是否允许跨维度配对（占位配置项）。
     * <p>
     * <b>当前实现仍是同维度</b>：{@code WirelessNetwork} 的频道表按维度分区
     * （{@code IdentityHashMap<LevelAccessor, …>}），要真正跨维度必须先加一张全局索引表。
     * 开启本项只会在日志里提示未生效，避免误以为已经跨维度。
     */
    private static final ModConfigSpec.BooleanValue WARP_TUNNEL_CROSS_DIMENSION = BUILDER
            .comment("B15 spatial fold tunnel: allow pairing across dimensions.",
                    "NOT IMPLEMENTED YET: the channel table is partitioned per dimension.",
                    "Kept as a placeholder; enabling it currently has no effect.",
                    "Default: false")
            .define("warpTunnelCrossDimension", false);

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

    /** B15 空间折跃隧道配对距离（格），默认 128。 */
    public static double warpTunnelRange() {
        return d(WARP_TUNNEL_RANGE, 128.0D);
    }

    /** B15 是否允许跨维度（当前未实现，见字段注释）。 */
    public static boolean warpTunnelCrossDimension() {
        try {
            return WARP_TUNNEL_CROSS_DIMENSION.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

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
