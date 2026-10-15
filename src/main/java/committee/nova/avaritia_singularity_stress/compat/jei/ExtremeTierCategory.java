package committee.nova.avaritia_singularity_stress.compat.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ExtremeTierCategory extends CreateRecipeCategory<ITierCraftingRecipe> {

    private static final int MAX_SIZE = 100;
    private static final int SLOT_STEP = 19;

    private final AnimatedExtremeCrafter crafter = new AnimatedExtremeCrafter();

    public ExtremeTierCategory(Info<ITierCraftingRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder,
                             ITierCraftingRecipe recipe, mezz.jei.api.recipe.IFocusGroup focuses) {
        // 输出槽:原版同位 (134,81)
        builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, 134, 81)
                .addItemStack(recipe.getResultItem(
                        Minecraft.getInstance().level.registryAccess()));

        int xPadding = getXPadding(recipe);
        int yPadding = getYPadding(recipe);
        float scale = getScale(recipe);
        ScaleAwareRenderer renderer = new ScaleAwareRenderer(scale);

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int width = getWidth(recipe);
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            int slotX = (int) ((xPadding + 1) + (i % width) * SLOT_STEP * scale);
            int slotY = (int) ((yPadding + 1) + (i / width) * SLOT_STEP * scale);
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.INPUT, slotX, slotY)
                    .setCustomRenderer(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, renderer)
                    .addIngredients(ingredient);
        }
    }

    @Override
    protected void draw(ITierCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView,
                        GuiGraphics guiGraphics, double mouseX, double mouseY) {
        float scale = getScale(recipe);
        int xPadding = getXPadding(recipe);
        int yPadding = getYPadding(recipe);

        // 有物品的格子垫槽底板(物品由自定义渲染器缩放绘制,JEI 不再自带背景)
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(xPadding, yPadding, 0);
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
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

        // 输出槽底板 + 向下箭头 + 3D 方块动画(原版同位)
        AllGuiTextures.JEI_SLOT.render(guiGraphics, 133, 80);
        AllGuiTextures.JEI_DOWN_ARROW.render(guiGraphics, 128, 59);
        crafter.draw(guiGraphics, 129, 25);
    }

    // ===== 布局公式(与原版逐行一致) =====

    private static int getWidth(ITierCraftingRecipe recipe) {
        if (recipe instanceof ShapedTableCraftingRecipe shapedTable) {
            return shapedTable.pattern.width();
        }
        // shapeless 无网格尺寸:按方形近似排布,避免竖向溢出
        return squareSide(recipe);
    }

    private static int getHeight(ITierCraftingRecipe recipe) {
        if (recipe instanceof ShapedTableCraftingRecipe shapedTable) {
            return shapedTable.pattern.height();
        }
        return squareSide(recipe);
    }

    private static int squareSide(ITierCraftingRecipe recipe) {
        int count = recipe.getIngredients().size();
        return Math.max(1, (int) Math.ceil(Math.sqrt(count)));
    }

    private static float getScale(ITierCraftingRecipe recipe) {
        int maxSide = Math.max(getWidth(recipe), getHeight(recipe));
        return Math.min(1.0F, MAX_SIZE / (SLOT_STEP * (float) maxSide));
    }

    private static int getXPadding(ITierCraftingRecipe recipe) {
        return 53 - (int) (getScale(recipe) * getWidth(recipe) * SLOT_STEP * 0.5D);
    }

    private static int getYPadding(ITierCraftingRecipe recipe) {
        return 53 - (int) (getScale(recipe) * getHeight(recipe) * SLOT_STEP * 0.5D);
    }

    // 输入物品缩放渲染器(tooltip 按原版由 renderer 提供)
    private record ScaleAwareRenderer(float scale) implements IIngredientRenderer<ItemStack> {

        private ScaleAwareRenderer {
            if (scale <= 0.0F) {
                scale = 1.0F;
            }
        }

        @Override
        public void render(GuiGraphics guiGraphics, @NotNull ItemStack stack) {
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            if (!stack.isEmpty()) {
                var modelViewStack = RenderSystem.getModelViewStack();
                modelViewStack.pushMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.enableDepthTest();
                Minecraft minecraft = Minecraft.getInstance();
                Font font = getFontRenderer(minecraft, stack);
                guiGraphics.renderItem(stack, 0, 0);
                guiGraphics.renderItemDecorations(font, stack, 0, 0, null);
                RenderSystem.disableBlend();
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            poseStack.popPose();
        }

        @Override
        public List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            try {
                return stack.getTooltipLines(
                        net.minecraft.world.item.Item.TooltipContext.of(minecraft.level),
                        player, flag);
            } catch (RuntimeException | LinkageError e) {
                List<Component> list = new ArrayList<>();
                MutableComponent crash = Component.translatable("jei.tooltip.error.crash");
                list.add(crash.withStyle(ChatFormatting.RED));
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
