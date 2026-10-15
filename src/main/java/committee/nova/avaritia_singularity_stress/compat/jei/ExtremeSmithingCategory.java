package committee.nova.avaritia_singularity_stress.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
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
 * 终焉锻造配方的 JEI 分类。
 * <p>
 * 与终焉动力合成器的分级分类的区别:材料<b>严格按终焉锻造的顺序与位置</b>摆放,
 * 而不是像分级配方那样按网格尺寸自动排布 ——
 * 因为锻造配方的 5 个输入各有固定语义(模板 / 基座 / 3 个添加物)。
 * <p>
 * 槽位排布与原版终焉锻造台的界面一致:
 * 模板在左,基座居中,三个添加物分别位于基座的上/右/下三侧。
 * <p>
 * 原料取值顺序同样遵循官方语义:{@code getIngredients()} 在 1.21.1 中
 * 依次返回 模板、基座、添加物1、添加物2、添加物3。
 */
public class ExtremeSmithingCategory extends CreateRecipeCategory<ExtremeSmithingRecipe> {

    // ===== 固定槽位(严格对应锻造配方的顺序与位置) =====
    /** 模板:基座左侧。 */
    private static final int TEMPLATE_X = 35;
    private static final int TEMPLATE_Y = 35;
    /** 基座:居中。 */
    private static final int BASE_X = 53;
    private static final int BASE_Y = 35;
    /** 添加物1:基座上方。 */
    private static final int ADD1_X = 53;
    private static final int ADD1_Y = 17;
    /** 添加物2:基座右侧。 */
    private static final int ADD2_X = 71;
    private static final int ADD2_Y = 35;
    /** 添加物3:基座下方。 */
    private static final int ADD3_X = 53;
    private static final int ADD3_Y = 53;
    /** 产物:右下(与分级分类同位)。 */
    private static final int RESULT_X = 134;
    private static final int RESULT_Y = 81;

    /** 输入槽位表,索引即 {@code getIngredients()} 的下标顺序。 */
    private static final List<int[]> INPUT_SLOTS = List.of(
            new int[]{TEMPLATE_X, TEMPLATE_Y},
            new int[]{BASE_X, BASE_Y},
            new int[]{ADD1_X, ADD1_Y},
            new int[]{ADD2_X, ADD2_Y},
            new int[]{ADD3_X, ADD3_Y}
    );

    private final AnimatedExtremeCrafter crafter = new AnimatedExtremeCrafter(
            () -> ModBlocks.EXTREME_SMITHING_CRAFTER.get().defaultBlockState());

    public ExtremeSmithingCategory(Info<ExtremeSmithingRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, ExtremeSmithingRecipe recipe,
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
     * 按锻造顺序取出 5 个展示用原料:模板、基座、添加物1、添加物2、添加物3。
     * <p>
     * 官方 {@code getIngredients()} 在添加物候选项不足 3 个时会越界抛异常,
     * 因此这里自行按同一顺序取值:候选项够 3 个就逐个展示,
     * 不够时退回展示整个添加物 Ingredient,避免 JEI 崩溃。
     */
    private static List<Ingredient> displayIngredients(ExtremeSmithingRecipe recipe) {
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
    protected void draw(ExtremeSmithingRecipe recipe, IRecipeSlotsView recipeSlotsView,
                        GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 输入槽底板:五种槽位始终存在,因此始终绘制
        for (int[] slot : INPUT_SLOTS) {
            AllGuiTextures.JEI_SLOT.render(guiGraphics, slot[0] - 1, slot[1] - 1);
        }

        // 输出槽底板 + 向下箭头 + 3D 方块动画(与分级分类同位)
        AllGuiTextures.JEI_SLOT.render(guiGraphics, RESULT_X - 1, RESULT_Y - 1);
        AllGuiTextures.JEI_DOWN_ARROW.render(guiGraphics, 128, 59);
        crafter.draw(guiGraphics, 129, 25);
    }
}
