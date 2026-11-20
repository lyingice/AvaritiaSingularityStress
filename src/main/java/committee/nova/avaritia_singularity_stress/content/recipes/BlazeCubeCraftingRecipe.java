package committee.nova.avaritia_singularity_stress.content.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import committee.nova.avaritia_singularity_stress.init.registry.ModExtremeRecipeTypes;
import net.minecraft.core.NonNullList;
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
import org.jetbrains.annotations.NotNull;

/**
 * B31 炽骨立方专属配方类型（{@code blaze_cube_crafting}）。
 * <p>
 * 炽骨立方合成器的专属子类型；终焉动力合成器同样支持（作为其配方族的一个子类型），
 * 原版工作台<b>无法</b>合成——独立 RecipeType 分桶，不在 {@code minecraft:crafting} 内。
 * <p>
 * 结构与 {@link ExtremeKineticCraftingRecipe} 同构：继承原版有序合成，
 * {@code accept_mirrored} 为 false 时禁用水平镜像匹配，只允许平移。
 * <p>
 * JSON 形态：
 * <pre>{@code
 * {
 *   "type": "avaritia_singularity_stress:blaze_cube_crafting",
 *   "pattern": ["AA", "AA"],
 *   "key": { "A": {"item": "..."} },
 *   "result": {"id": "...", "count": 1},
 *   "accept_mirrored": false
 * }
 * }</pre>
 */
public class BlazeCubeCraftingRecipe extends ShapedRecipe {

    private final boolean acceptMirrored;

    public BlazeCubeCraftingRecipe(String group, CraftingBookCategory category,
                                   ShapedRecipePattern pattern, ItemStack result,
                                   boolean showNotification, boolean acceptMirrored) {
        super(group, category, pattern, result, showNotification);
        this.acceptMirrored = acceptMirrored;
    }

    private static BlazeCubeCraftingRecipe fromShaped(ShapedRecipe recipe, boolean acceptMirrored) {
        // 1.21.1 中 ShapedRecipe.pattern 是 public final,无需 accessor。
        return new BlazeCubeCraftingRecipe(
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

    /** 与 ExtremeKineticCraftingRecipe 相同的紧凑网格平移/镜像匹配语义。 */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (acceptMirrored) {
            return this.pattern.matches(input);
        }
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
        return ModExtremeRecipeTypes.BLAZE_CUBE_CRAFTING_TYPE.get();
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModExtremeRecipeTypes.BLAZE_CUBE_CRAFTING_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<BlazeCubeCraftingRecipe> {
        public static final MapCodec<BlazeCubeCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        RecipeSerializer.SHAPED_RECIPE.codec().forGetter(t -> t),
                        Codec.BOOL.optionalFieldOf("accept_mirrored", true).forGetter(BlazeCubeCraftingRecipe::acceptsMirrored)
                ).apply(instance, BlazeCubeCraftingRecipe::fromShaped));

        public static final StreamCodec<RegistryFriendlyByteBuf, BlazeCubeCraftingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        ShapedRecipe.Serializer.STREAM_CODEC, i -> i,
                        ByteBufCodecs.BOOL, BlazeCubeCraftingRecipe::acceptsMirrored,
                        BlazeCubeCraftingRecipe::fromShaped);

        @Override
        public @NotNull MapCodec<BlazeCubeCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, BlazeCubeCraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}