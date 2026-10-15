package committee.nova.avaritia_singularity_stress.content.recipes;

import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler.GroupedItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * 终焉动能合成的紧凑网格工具:把连接组聚合网格裁剪出非空包围盒,
 * 并按 {@code y = maxY - row} 翻转,使行 0 对应世界最高一行
 * (与 Minecraft 配方自上而下的行序一致)。
 * <p>
 * 1.21.1 中 {@code CraftingInput} 是 final,无法子类化,
 * 因此这里经公开工厂 {@code CraftingInput.of(w, h, list)} 产出紧凑实例;
 * {@code ShapedRecipePattern.matches} 要求输入尺寸与 pattern 完全一致,
 * 因此外围空行空列必须先被裁掉。
 */
public final class ExtremeKineticCraftingInput {

    private ExtremeKineticCraftingInput() {
    }

    /** 裁剪 + 翻转,产出可直接交给配方匹配的紧凑网格。 */
    public static CraftingInput of(GroupedItems items) {
        // 第一遍:找出实际非空物品的包围盒(忽略外围空行/空列)
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = 0; y < items.height; y++) {
            for (int x = 0; x < items.width; x++) {
                int xp = x + items.minX;
                int yp = y + items.minY;
                ItemStack stack = items.grid.get(Pair.of(xp, yp));
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                minX = Math.min(minX, xp);
                maxX = Math.max(maxX, xp);
                minY = Math.min(minY, yp);
                maxY = Math.max(maxY, yp);
            }
        }

        int w = maxX - minX + 1;
        int h = maxY - minY + 1;

        // 第二遍:按 y 翻转,生成自上而下的行序
        List<ItemStack> list = new ArrayList<>(w * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ItemStack stack = items.grid.get(Pair.of(x + minX, maxY - y));
                list.add(stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }

        return CraftingInput.of(w, h, list);
    }
}
