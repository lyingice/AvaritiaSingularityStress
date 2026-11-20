package committee.nova.avaritia_singularity_stress.content.extreme_drill;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * B27 终焉钻头可配置参数（SERVER config，随存档；独立文件名
 * {@code avaritia_singularity_stress-drill-server.toml}，避免与主 SERVER config 冲突）。
 * <p>
 * 时运额外乘区：有效时运 = 基础等级（0~16，随转速每 16RPM 一级）× 本值。
 * getter 带未加载回退（默认 5），避免极早期调用抛异常（镜像 SingularityConfig 的写法）。
 */
public final class ExtremeDrillConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 时运额外乘区，默认 5。公开字段供自检覆写（镜像 AllConfigs 自检用法）。 */
    public static final ModConfigSpec.IntValue FORTUNE_MULTIPLIER = BUILDER
            .comment("Extra multiplier zone for the Extreme Drill fortune level:",
                    "effective fortune = base level (0-16, one per 16 RPM) x this value.",
                    "Default: 5")
            .defineInRange("fortuneMultiplier", 5, 1, 256);

    /** config 规格，在 mod 构造函数中注册。 */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ExtremeDrillConfig() {
    }

    /** 时运乘区，默认 5（config 未加载时回退）。 */
    public static int fortuneMultiplier() {
        try {
            return FORTUNE_MULTIPLIER.get();
        } catch (IllegalStateException e) {
            return 5;
        }
    }
}