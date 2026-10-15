package committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor;

import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import net.minecraft.world.level.block.Block;

/**
 * B4 动能中子态素压缩机的四个等级。
 * <p>
 * 全等级能耗相同(32 SU/RPM),差异体现在 <b>效能</b> 上:
 * <ul>
 *   <li>{@code materialReduction} —— RPM 达到 64/128/256 时原材料消耗的减免比例(绝对值)。</li>
 *   <li>{@code doubleChance} —— RPM 达到 64/128/256 时获得双倍产出的概率(需 SSU 才会触发)。</li>
 *   <li>{@code quadChance} —— 在双倍基础上再翻倍(即四倍)的概率(需 SSU)。</li>
 * </ul>
 * 三个数组的下标依次对应 RPM 档位 64 / 128 / 256。
 */
public enum CompressorTier {

    BASE("kinetic_neutron_compressor",
            new float[]{0.05F, 0.10F, 0.20F},
            new float[]{0.0F, 0.0F, 0.0F},
            new float[]{0.0F, 0.0F, 0.0F}),

    DENSE("dense_kinetic_neutron_compressor",
            new float[]{0.10F, 0.15F, 0.30F},
            new float[]{0.03F, 0.06F, 0.09F},
            new float[]{0.0F, 0.0F, 0.0F}),

    ELITE("elite_kinetic_neutron_compressor",
            new float[]{0.20F, 0.25F, 0.45F},
            new float[]{0.09F, 0.18F, 0.27F},
            new float[]{0.0F, 0.0F, 0.0F}),

    ULTIMATE("ultimate_kinetic_neutron_compressor",
            new float[]{0.40F, 0.55F, 0.65F},
            new float[]{1.0F, 1.0F, 1.0F},
            new float[]{0.12F, 0.18F, 0.27F});

    /** 全等级统一能耗。 */
    public static final float SU_PER_RPM = 32.0F;

    /** 基准产出周期(tick):SU 模式 320,SSU 模式减半为 160。 */
    public static final int BASE_TICKS = 320;
    public static final int SSU_PERIOD_DIVISOR = 2;

    /** 三个效能档位对应的 RPM 阈值。 */
    public static final float[] RPM_THRESHOLDS = {64.0F, 128.0F, 256.0F};

    private final String registryName;
    private final float[] materialReduction;
    private final float[] doubleChance;
    private final float[] quadChance;

    CompressorTier(String registryName, float[] materialReduction, float[] doubleChance, float[] quadChance) {
        this.registryName = registryName;
        this.materialReduction = materialReduction;
        this.doubleChance = doubleChance;
        this.quadChance = quadChance;
    }

    public String registryName() {
        return registryName;
    }

    /** 当前转速命中的效能档位下标(0/1/2),未达 64 返回 -1。 */
    public int rpmTierIndex(float rpm) {
        float abs = Math.abs(rpm);
        int index = -1;
        for (int i = 0; i < RPM_THRESHOLDS.length; i++) {
            if (abs >= RPM_THRESHOLDS[i]) {
                index = i;
            }
        }
        return index;
    }

    public float materialReduction(float rpm) {
        int i = rpmTierIndex(rpm);
        return i < 0 ? 0.0F : materialReduction[i];
    }

    public float doubleChance(float rpm) {
        int i = rpmTierIndex(rpm);
        return i < 0 ? 0.0F : doubleChance[i];
    }

    public float quadChance(float rpm) {
        int i = rpmTierIndex(rpm);
        return i < 0 ? 0.0F : quadChance[i];
    }

    /** 由方块反查等级。 */
    public static CompressorTier fromBlock(Block block) {
        if (block == ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get()) {
            return DENSE;
        }
        if (block == ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get()) {
            return ELITE;
        }
        if (block == ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get()) {
            return ULTIMATE;
        }
        return BASE;
    }
}
