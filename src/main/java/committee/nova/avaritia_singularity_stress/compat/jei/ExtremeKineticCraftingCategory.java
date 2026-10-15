package committee.nova.avaritia_singularity_stress.compat.jei;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 终焉动能合成的 JEI 分类。
 * <p>
 * 版式与分级分类一致:网格居中于 (53,53) 并按最大边缩放,
 * 产物固定右下 (134,81),箭头 (128,59),3D 动画 (129,25)。
 * 配方继承原版 ShapedRecipe,尺寸直接取 pattern 的宽高。
 */
public class ExtremeKineticCraftingCategory extends CreateRecipeCategory<ExtremeKineticCraftingRecipe> {

    private static final int MAX_SIZE = 100;
    private static final int SLOT_STEP = 19;

    private static final int RESULT_X = 134;
    private static final int RESULT_Y = 81;

    private final AnimatedExtremeCrafter crafter = new AnimatedExtremeCrafter();

    public ExtremeKineticCraftingCategory(Info<ExtremeKineticCraftingRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, ExtremeKineticCraftingRecipe recipe,
                             IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, RESULT_X, RESULT_Y)
                .addItemStack(getResultItem(recipe));

        int xPadding = getXPadding(recipe);
        int yPadding = getYPadding(recipe);
        float scale = getScale(recipe);

        var renderer = new ScaleAwareRenderer(scale);

        var ingredients = recipe.getIngredients();
        int width = getWidth(recipe);
        for (int i = 0; i < ingredients.size(); i++) {
            var ingredient = ingredients.get(i);
            int slotX = (int) ((xPadding + 1) + (i % width) * SLOT_STEP * scale);
            int slotY = (int) ((yPadding + 1) + (i / width) * SLOT_STEP * scale);
            builder.addSlot(RecipeIngredientRole.INPUT, slotX, slotY)
                    .setCustomRenderer(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, renderer)
                    .addIngredients(ingredient);
        }
    }

    @Override
    protected void draw(ExtremeKineticCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView,
                        GuiGraphics guiGraphics, double mouseX, double mouseY) {
        float scale = getScale(recipe);
        int xPadding = getXPadding(recipe);
        int yPadding = getYPadding(recipe);

        // 有物品的格子垫槽底板
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(xPadding, yPadding, 0);
        var ingredients = recipe.getIngredients();
        int width = getWidth(recipe);
        int height = getHeight(recipe);
        outer:
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int index = row * width + col;
                if (index >= ingredients.size()) {
                    break outer;
                }
                if (ingredients.get(index).isEmpty()) {
                    continue;
                }
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(col * SLOT_STEP * scale, row * SLOT_STEP * scale, 0);
                guiGraphics.pose().scale(scale, scale, scale);
                AllGuiTextures.JEI_SLOT.render(guiGraphics, 0, 0);
                guiGraphics.pose().popPose();
            }
        }
        guiGraphics.pose().popPose();

        // 输出槽底板 + 向下箭头 + 3D 方块动画
        AllGuiTextures.JEI_SLOT.render(guiGraphics, RESULT_X - 1, RESULT_Y - 1);
        AllGuiTextures.JEI_DOWN_ARROW.render(guiGraphics, 128, 59);
        crafter.draw(guiGraphics, 129, 25);
    }

    // ===== 布局公式(与分级分类逐行一致) =====

    private static int getWidth(ExtremeKineticCraftingRecipe recipe) {
        return recipe.getWidth();
    }

    private static int getHeight(ExtremeKineticCraftingRecipe recipe) {
        return recipe.getHeight();
    }

    private static float getScale(ExtremeKineticCraftingRecipe recipe) {
        int maxSide = Math.max(getWidth(recipe), getHeight(recipe));
        return Math.min(1.0F, MAX_SIZE / (SLOT_STEP * (float) maxSide));
    }

    private static int getXPadding(ExtremeKineticCraftingRecipe recipe) {
        return 53 - (int) (getScale(recipe) * getWidth(recipe) * SLOT_STEP * 0.5D);
    }

    private static int getYPadding(ExtremeKineticCraftingRecipe recipe) {
        return 53 - (int) (getScale(recipe) * getHeight(recipe) * SLOT_STEP * 0.5D);
    }

    // 输入物品缩放渲染器(与分级分类同一实现)
    private record ScaleAwareRenderer(float scale)
            implements mezz.jei.api.ingredients.IIngredientRenderer<net.minecraft.world.item.ItemStack> {

        private ScaleAwareRenderer {
            if (scale <= 0.0F) {
                scale = 1.0F;
            }
        }

        @Override
        public void render(GuiGraphics guiGraphics, @NotNull net.minecraft.world.item.ItemStack stack) {
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            if (!stack.isEmpty()) {
                var modelViewStack = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
                modelViewStack.pushMatrix();
                com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                var font = getFontRenderer(minecraft, stack);
                guiGraphics.renderItem(stack, 0, 0);
                guiGraphics.renderItemDecorations(font, stack, 0, 0, null);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                modelViewStack.popMatrix();
                com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            }
            poseStack.popPose();
        }

        @Override
        public List<net.minecraft.network.chat.Component> getTooltip(net.minecraft.world.item.ItemStack stack,
                                                                     net.minecraft.world.item.TooltipFlag flag) {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.world.entity.player.Player player = minecraft.player;
            try {
                return stack.getTooltipLines(
                        net.minecraft.world.item.Item.TooltipContext.of(minecraft.level),
                        player, flag);
            } catch (RuntimeException | LinkageError e) {
                List<net.minecraft.network.chat.Component> list = new java.util.ArrayList<>();
                net.minecraft.network.chat.MutableComponent crash =
                        net.minecraft.network.chat.Component.translatable("jei.tooltip.error.crash");
                list.add(crash.withStyle(net.minecraft.ChatFormatting.RED));
                return list;
            }
        }

        @Override
        public int getWidth() {
            return (int) (16.0F * scale);
        }

        @Override
        public int getHeight() {
            return (int) (16.0F * scale);
        }
    }
}
