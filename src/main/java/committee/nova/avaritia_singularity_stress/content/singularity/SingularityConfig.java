package committee.nova.avaritia_singularity_stress.content.singularity;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * SSU 体系可配置参数(COMMON config,双端共用)。
 * <p>
 * 所有奇点应力相关数值统一从这里读取,不再硬编码。
 * COMMON 类型保证客户端渲染护目镜 tooltip 时也能读到同一份值。
 * <p>
 * 注意:config 在 mod 构造期注册、游戏早期加载;所有 getter 均带未加载回退
 * (返回默认值),避免任何早期调用(如世界装配阶段)抛异常。
 */
public final class SingularityConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * 压缩比:多少 SU 压缩成 1 SSU。
     * <p>
     * 同时是 SSU 产出公式的除数和奇点浓度判定的除数——
     * 本机需求 = 本机应力消耗 ÷ 本值 = 本机需要的 SSU 数量。单位:SU/SSU。
     */
    private static final ModConfigSpec.DoubleValue COMPRESSION_RATIO = BUILDER
            .comment("Compression ratio: how many SU are compressed into 1 SSU.",
                    "Also used as the divisor for singularity concentration.",
                    "Unit: SU per SSU. Default: 32")
            .defineInRange("compressionRatio", 32.0D, 1.0D, Double.MAX_VALUE);

    /**
     * 产能门槛:空闲应力低于该值时不产出任何 SSU。单位:SU。
     */
    private static final ModConfigSpec.DoubleValue CAPACITY_GATE = BUILDER
            .comment("Capacity gate: no SSU is produced while idle stress is below this value.",
                    "Unit: SU. Default: 1024")
            .defineInRange("capacityGate", 1024.0D, 0.0D, Double.MAX_VALUE);

    /**
     * 品控损耗下限:空闲比例为 0 时每台 B6 的损耗。单位:SU/RPM/台。
     */
    private static final ModConfigSpec.DoubleValue QUALITY_LOSS_MIN = BUILDER
            .comment("Quality-control loss lower bound (idle ratio = 0).",
                    "Unit: SU per RPM per compressor. Default: 8")
            .defineInRange("qualityLossMin", 8.0D, 0.0D, Double.MAX_VALUE);

    /**
     * 品控损耗上限:空闲比例为 100% 时每台 B6 的损耗。单位:SU/RPM/台。
     * 应不低于 qualityLossMin。
     */
    private static final ModConfigSpec.DoubleValue QUALITY_LOSS_MAX = BUILDER
            .comment("Quality-control loss upper bound (idle ratio = 100%).",
                    "Should be >= qualityLossMin.",
                    "Unit: SU per RPM per compressor. Default: 32")
            .defineInRange("qualityLossMax", 32.0D, 0.0D, Double.MAX_VALUE);

    /**
     * B6 基础应力占用率(不含品控损耗)。单位:SU/RPM/台。
     */
    private static final ModConfigSpec.DoubleValue B6_STRESS_PER_RPM = BUILDER
            .comment("Base stress occupancy of each singularity compressor, excluding quality loss.",
                    "Unit: SU per RPM. Default: 32")
            .defineInRange("b6StressPerRpm", 32.0D, 0.0D, Double.MAX_VALUE);

    /**
     * SSU 等效倍率:1 SSU = 本倍率 × 压缩比 的 SU 等效做功能力。
     * <p>
     * 用于护目镜"奇点应力影响"的 ssu 折算显示;默认 1.0,
     * 即 1 SSU = 32 SU 等效(压缩比 32 时)。
     */
    private static final ModConfigSpec.DoubleValue SSU_TO_SU_RATIO = BUILDER
            .comment("SSU equivalence multiplier: 1 SSU equals (this value x compressionRatio) SU.",
                    "Used for goggle ssu display. Default: 1.0")
            .defineInRange("ssuToSuRatio", 1.0D, 0.01D, Double.MAX_VALUE);

    /** config 规格,在 mod 构造函数中注册。 */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private SingularityConfig() {
    }

    // ================= getter(config 未加载时回退默认值) =================

    /** 压缩比(SU/SSU),默认 32。 */
    public static float compressionRatio() {
        return f(COMPRESSION_RATIO, 32.0F);
    }

    /** 产能门槛(SU),默认 1024。 */
    public static float capacityGate() {
        return f(CAPACITY_GATE, 1024.0F);
    }

    /** 品控损耗下限(SU/RPM/台),默认 8。 */
    public static float qualityLossMin() {
        return f(QUALITY_LOSS_MIN, 8.0F);
    }

    /** 品控损耗上限(SU/RPM/台),默认 32。 */
    public static float qualityLossMax() {
        return f(QUALITY_LOSS_MAX, 32.0F);
    }

    /** B6 基础应力占用(SU/RPM/台),默认 32。 */
    public static float b6StressPerRpm() {
        return f(B6_STRESS_PER_RPM, 32.0F);
    }

    /** SSU 等效倍率,默认 1.0(1 SSU = 压缩比 × 本值 SU 等效)。 */
    public static float ssuToSuRatio() {
        return f(SSU_TO_SU_RATIO, 1.0F);
    }

    private static float f(ModConfigSpec.DoubleValue value, float fallback) {
        try {
            return value.get().floatValue();
        } catch (IllegalStateException e) {
            // config 尚未加载(极早期调用):回退默认值
            return fallback;
        }
    }
}