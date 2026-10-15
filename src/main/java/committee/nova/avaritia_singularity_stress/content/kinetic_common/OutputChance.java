package committee.nova.avaritia_singularity_stress.content.kinetic_common;

import net.minecraft.world.item.ItemStack;

/**
 * 机器的一次可能产出:产物与触发概率(0~1)。
 * <p>
 * 用于 Jade 展示"可能产出 + 概率"列表;概率为 1.0 的确定性产出通常不显示百分比。
 */
public record OutputChance(ItemStack stack, float chance) {
}
