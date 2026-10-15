package committee.nova.avaritia_singularity_stress.content.kinetic_common;

import net.minecraft.util.Mth;

/**
 * B2 收集器与 B4 压缩机共用的转速—周期换算。
 * <p>
 * <b>语义（重要）</b>：转速对产出只有<b>一个</b>作用——<b>缩短产出周期</b>。
 * 转速越高，周期越短，因而单位时间产出越多；不存在"另外一条加速轴"。
 * 本类的倍率就是"周期缩短倍率"：{@code 周期 = 基准周期 ÷ 本倍率}。
 * <p>
 * 换算曲线为速率线性：{@code 倍率 = 1 + 7×(clamp(RPM,16,256) − 16) / 240}，
 * 16 RPM → 1×（基准周期），256 RPM → 8×（周期缩短到 1/8）。
 * <ul>
 *   <li>B2 收集器：基准周期单位为「秒」（320/280/240/200），乘 20 转 tick 后再除以倍率；</li>
 *   <li>B4 压缩机：基准周期单位已是「tick」（320，SSU 模式 160），直接除以倍率。</li>
 * </ul>
 */
public final class KineticPeriods {

    /** 最低可运行转速：低于此值不产出。 */
    public static final float MIN_RPM = 16.0F;

    /** 最高有效转速：达到此值时周期最短（基准的 1/8）。 */
    public static final float MAX_RPM = 256.0F;

    /** 转速区间的最大周期缩短倍数(256RPM 时相对基准缩短 8 倍)。 */
    private static final float PERIOD_BONUS_RANGE = 7.0F;

    private KineticPeriods() {
    }

    /**
     * 周期缩短倍率（≥1）：把基准周期除以本值即得当前周期。
     * <p>
     * 转速超出 [16, 256] 会被夹紧——低于 16 视为 1×(最短意义的基准)，
     * 高于 256 不再进一步缩短。
     */
    public static float periodDivisor(float rpm) {
        float clamped = Mth.clamp(Math.abs(rpm), MIN_RPM, MAX_RPM);
        return 1.0F + PERIOD_BONUS_RANGE * (clamped - MIN_RPM) / (MAX_RPM - MIN_RPM);
    }
}
