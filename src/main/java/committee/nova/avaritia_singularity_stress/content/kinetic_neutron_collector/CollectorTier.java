package committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector;

import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * B2 动能中子态素收集器的四个等级。
 * <p>
 * 数值取自设计稿:
 * <ul>
 *   <li>{@code suPerRpm} —— 该等级的应力消耗(SU/RPM)。基础 32,越高阶越贵。</li>
 *   <li>{@code baseSeconds} —— 16 RPM 时的基准产出周期(秒)。RPM 越高周期越短(见速度倍率公式)。</li>
 *   <li>{@code overexciteBonus} —— SSU 且 RPM &gt; 128 进入超激发态时,额外增加的产量。</li>
 *   <li>{@code outputTag} —— 产出物品标签(与无尽贪婪的中子物品一致)。</li>
 * </ul>
 */
public enum CollectorTier {

    /** 基础:产出中子尘埃。 */
    BASE("kinetic_neutron_collector", 32.0F, 320, 0, tag("dust/neutronium")),

    /** 致密:产出中子颗粒,超激发态 +1。 */
    DENSE("dense_kinetic_neutron_collector", 64.0F, 280, 1, tag("nuggets/neutronium")),

    /** 精英:产出中子锭,超激发态 +2。 */
    ELITE("elite_kinetic_neutron_collector", 128.0F, 240, 2, tag("ingots/neutronium")),

    /** 极限:产出中子块,超激发态 +3(256RPM 为终极激发态)。 */
    ULTIMATE("ultimate_kinetic_neutron_collector", 256.0F, 200, 3, tag("storage_blocks/neutronium"));

    /** SSU 模式下产出周期减半(2 倍效率)。 */
    public static final int SSU_PERIOD_DIVISOR = 2;

    /** 进入超激发态所需的 SSU 转速下限(严格大于,不含)。 */
    public static final float OVEREXCITE_RPM = 128.0F;

    private final String registryName;
    public final float suPerRpm;
    public final int baseSeconds;
    public final int overexciteBonus;
    public final TagKey<Item> outputTag;

    CollectorTier(String registryName, float suPerRpm, int baseSeconds, int overexciteBonus, TagKey<Item> outputTag) {
        this.registryName = registryName;
        this.suPerRpm = suPerRpm;
        this.baseSeconds = baseSeconds;
        this.overexciteBonus = overexciteBonus;
        this.outputTag = outputTag;
    }

    public String registryName() {
        return registryName;
    }

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
    }

    /** 解析当前等级的产出物品;标签为空时返回空栈。 */
    public ItemStack outputStack() {
        ItemStack[] items = Ingredient.of(outputTag).getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0].copyWithCount(1);
    }

    /** 由方块反查等级(避免枚举构造期引用 ModBlocks 造成类初始化环路)。 */
    public static CollectorTier fromBlock(Block block) {
        if (block == ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get()) {
            return DENSE;
        }
        if (block == ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get()) {
            return ELITE;
        }
        if (block == ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get()) {
            return ULTIMATE;
        }
        return BASE;
    }
}
