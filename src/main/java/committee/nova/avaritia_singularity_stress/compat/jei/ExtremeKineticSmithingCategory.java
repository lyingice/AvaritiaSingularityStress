package committee.nova.avaritia_singularity_stress.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticSmithingRecipe;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * 终焉动能锻造的 JEI 分类。
 * <p>
 * 版式与锻造分类一致:模板在左、基座居中、三个添加物位于基座的上/右/下,
 * 材料位置严格对应锻造的顺序与位置。
 * 数据直接读本模组配方字段,添加物候选项不足 3 个时回退展示整个
 * Ingredient,避免越界(与锻造分类同一防御)。
 */
public class ExtremeKineticSmithingCategory extends CreateRecipeCategory<ExtremeKineticSmithingRecipe> {

    // ===== 固定槽位(严格对应锻造的顺序与位置) =====
    private static final int TEMPLATE_X = 35;
    private static final int TEMPLATE_Y = 35;
    private static final int BASE_X = 53;
    private static final int BASE_Y = 35;
    private static final int ADD1_X = 53;
    private static final int ADD1_Y = 17;
    private static final int ADD2_X = 71;
    private static final int ADD2_Y = 35;
    private static final int ADD3_X = 53;
    private static final int ADD3_Y = 53;
    private static final int RESULT_X = 134;
    private static final int RESULT_Y = 81;

    /** 输入槽位表,索引即 模板/基座/添加物1/添加物2/添加物3。 */
    private static final List<int[]> INPUT_SLOTS = List.of(
            new int[]{TEMPLATE_X, TEMPLATE_Y},
            new int[]{BASE_X, BASE_Y},
            new int[]{ADD1_X, ADD1_Y},
            new int[]{ADD2_X, ADD2_Y},
            new int[]{ADD3_X, ADD3_Y}
    );

    private final AnimatedExtremeCrafter crafter = new AnimatedExtremeCrafter(
            () -> ModBlocks.EXTREME_SMITHING_CRAFTER.get().defaultBlockState());

    public ExtremeKineticSmithingCategory(Info<ExtremeKineticSmithingRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, ExtremeKineticSmithingRecipe recipe,
                             IFocusGroup focuses) {
        List<Ingredient> display = displayIngredients(recipe);

        for (int i = 0; i < INPUT_SLOTS.size() && i < display.size(); i++) {
            int[] slot = INPUT_SLOTS.get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, slot[0], slot[1])
                    .addIngredients(display.get(i));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, RESULT_X, RESULT_Y)
                .addItemStack(getResultItem(recipe));
    }

    /**
     * 按锻造顺序取 5 个展示用原料:模板、基座、添加物1..3。
     * 候选项够 3 个逐个展示,不足时回退整个 Ingredient。
     */
    private static List<Ingredient> displayIngredients(ExtremeKineticSmithingRecipe recipe) {
        List<Ingredient> display = new ArrayList<>(INPUT_SLOTS.size());
        display.add(recipe.template);
        display.add(recipe.base);

        ItemStack[] additionItems = recipe.additions.getItems();
        for (int i = 0; i < 3; i++) {
            display.add(i < additionItems.length ? Ingredient.of(additionItems[i]) : recipe.additions);
        }
        return display;
    }

    @Override
    protected void draw(ExtremeKineticSmithingRecipe recipe, IRecipeSlotsView recipeSlotsView,
                        GuiGraphics guiGraphics, double mouseX, double mouseY) {
        for (int[] slot : INPUT_SLOTS) {
            AllGuiTextures.JEI_SLOT.render(guiGraphics, slot[0] - 1, slot[1] - 1);
        }

        AllGuiTextures.JEI_SLOT.render(guiGraphics, RESULT_X - 1, RESULT_Y - 1);
        AllGuiTextures.JEI_DOWN_ARROW.render(guiGraphics, 128, 59);
        crafter.draw(guiGraphics, 129, 25);
    }
}
