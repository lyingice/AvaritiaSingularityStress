package committee.nova.avaritia_singularity_stress.content.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import committee.nova.avaritia_singularity_stress.init.registry.ModExtremeRecipeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.minecraft.core.NonNullList;
import org.jetbrains.annotations.NotNull;

/**
 * 终焉动能合成配方(终焉合成器专属,非工作台配方)。
 * <p>
 * 结构与 Create 的 {@code MechanicalCraftingRecipe} 同构:
 * 继承原版有序合成,唯一差异是 {@code accept_mirrored} 字段 ——
 * 为 false 时禁用水平镜像匹配,只允许平移。
 * <p>
 * JSON 形态(与 create:mechanical_crafting 一致,多一个布尔字段):
 * <pre>{@code
 * {
 *   "type": "avaritia_singularity_stress:extreme_kinetic_crafting",
 *   "pattern": ["AA", "AA"],
 *   "key": { "A": {"item": "..."} },
 *   "result": {"id": "...", "count": 1},
 *   "accept_mirrored": false
 * }
 * }</pre>
 */
public class ExtremeKineticCraftingRecipe extends ShapedRecipe {

    private final boolean acceptMirrored;

    public ExtremeKineticCraftingRecipe(String group, CraftingBookCategory category,
                                        ShapedRecipePattern pattern, ItemStack result,
                                        boolean showNotification, boolean acceptMirrored) {
        super(group, category, pattern, result, showNotification);
        this.acceptMirrored = acceptMirrored;
    }

    private static ExtremeKineticCraftingRecipe fromShaped(ShapedRecipe recipe, boolean acceptMirrored) {
        // 1.21.1 中 ShapedRecipe.pattern 是 public final,无需 accessor。
        return new ExtremeKineticCraftingRecipe(
                recipe.getGroup(),
                recipe.category(),
                recipe.pattern,
                recipe.getResultItem(null),
                recipe.showNotification(),
                acceptMirrored);
    }

    public boolean acceptsMirrored() {
        return acceptMirrored;
    }

    /**
     * 对紧凑网格匹配(由 ExtremeKineticCraftingInput.of 裁剪 + 翻转产出)。
     * 本配方只在专属 RecipeType 分桶内被遍历,因此这里直接接受
     * CraftingInput,无需类型嗅探。
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (acceptMirrored) {
            // 原版 pattern.matches 自动尝试水平镜像
            return this.pattern.matches(input);
        }

        // 与 Create matchesSpecific 相同:只做平移匹配,不做镜像
        for (int i = 0; i <= input.width() - this.getWidth(); ++i) {
            for (int j = 0; j <= input.height() - this.getHeight(); ++j) {
                if (this.matchesSpecific(input, i, j)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesSpecific(CraftingInput input, int offsetX, int offsetY) {
        NonNullList<Ingredient> ingredients = this.getIngredients();
        int width = this.getWidth();
        int height = this.getHeight();
        for (int i = 0; i < input.width(); ++i) {
            for (int j = 0; j < input.height(); ++j) {
                int k = i - offsetX;
                int l = j - offsetY;
                Ingredient ingredient = Ingredient.EMPTY;
                if (k >= 0 && l >= 0 && k < width && l < height) {
                    ingredient = ingredients.get(k + l * width);
                }
                if (!ingredient.test(input.getItem(i + j * input.width()))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModExtremeRecipeTypes.EXTREME_KINETIC_CRAFTING_TYPE.get();
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModExtremeRecipeTypes.EXTREME_KINETIC_CRAFTING_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<ExtremeKineticCraftingRecipe> {
        public static final MapCodec<ExtremeKineticCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        RecipeSerializer.SHAPED_RECIPE.codec().forGetter(t -> t),
                        Codec.BOOL.optionalFieldOf("accept_mirrored", true).forGetter(ExtremeKineticCraftingRecipe::acceptsMirrored)
                ).apply(instance, ExtremeKineticCraftingRecipe::fromShaped));

        public static final StreamCodec<RegistryFriendlyByteBuf, ExtremeKineticCraftingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ShapedRecipe.Serializer.STREAM_CODEC, i -> i,
                        ByteBufCodecs.BOOL, ExtremeKineticCraftingRecipe::acceptsMirrored,
                        ExtremeKineticCraftingRecipe::fromShaped);

        @Override
        public @NotNull MapCodec<ExtremeKineticCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, ExtremeKineticCraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
