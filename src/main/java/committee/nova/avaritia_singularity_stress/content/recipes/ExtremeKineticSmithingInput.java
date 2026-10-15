package committee.nova.avaritia_singularity_stress.content.recipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * 终焉动能锻造的输入:模板 / 基座 / 3 个添加物,共 5 格。
 * <p>
 * 槽位语义与原版终焉锻造台一致:
 * getItem(0)=模板、getItem(1)=基座、getItem(2..4)=添加物1..3。
 */
public record ExtremeKineticSmithingInput(ItemStack template, ItemStack base,
                                          ItemStack addition1, ItemStack addition2, ItemStack addition3)
        implements RecipeInput {

    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> this.template;
            case 1 -> this.base;
            case 2 -> this.addition1;
            case 3 -> this.addition2;
            case 4 -> this.addition3;
            default -> throw new IllegalArgumentException("Recipe index " + index + " is out of range [0, 4]");
        };
    }

    @Override
    public int size() {
        return 5;
    }

    public boolean isEmpty() {
        return this.template.isEmpty() && this.base.isEmpty()
                && this.addition1.isEmpty() && this.addition2.isEmpty() && this.addition3.isEmpty();
    }
}
