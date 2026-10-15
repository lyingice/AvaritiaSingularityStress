package committee.nova.avaritia_singularity_stress.content.recipes;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import committee.nova.avaritia_singularity_stress.init.registry.ModExtremeRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 终焉动能锻造配方(含终焉锻造器的合成链专属,非锻造台配方)。
 * <p>
 * 槽位语义与原版终焉锻造台一致:一个模板、一个基座、三个添加物,
 * 三个添加物共用同一个 Ingredient(允许 JSON 数组 = OR 候选)。
 * <p>
 * JSON 形态:
 * <pre>{@code
 * {
 *   "type": "avaritia_singularity_stress:extreme_kinetic_smithing",
 *   "template": {"item": "..."},
 *   "base": {"item": "..."},
 *   "addition": [{"item": "a"}, {"item": "b"}, {"item": "c"}],
 *   "result": {"id": "...", "count": 1}
 * }
 * }</pre>
 * 与无尽贪婪的 {@code avaritia:extreme_smithing} 的差别:
 * {@code assemble} 直接返回 result(不做基座组件透传),语义更可预期。
 */
public class ExtremeKineticSmithingRecipe implements net.minecraft.world.item.crafting.Recipe<ExtremeKineticSmithingInput> {

    public final Ingredient template;
    public final Ingredient base;
    public final Ingredient additions;
    public final ItemStack result;

    public ExtremeKineticSmithingRecipe(Ingredient template, Ingredient base, Ingredient additions, ItemStack result) {
        this.template = template;
        this.base = base;
        this.additions = additions;
        this.result = result;
    }

    @Override
    public boolean matches(@NotNull ExtremeKineticSmithingInput input, @NotNull Level level) {
        return this.template.test(input.getItem(0))
                && this.base.test(input.getItem(1))
                && this.additions.test(input.getItem(2))
                && this.additions.test(input.getItem(3))
                && this.additions.test(input.getItem(4));
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull ExtremeKineticSmithingInput input, HolderLookup.@NotNull Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return this.result.copy();
    }

    public boolean isTemplateIngredient(ItemStack stack) {
        return this.template.test(stack);
    }

    public boolean isBaseIngredient(ItemStack stack) {
        return this.base.test(stack);
    }

    public boolean isAdditionIngredient(ItemStack stack) {
        return this.additions.test(stack);
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModExtremeRecipeTypes.EXTREME_KINETIC_SMITHING_TYPE.get();
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModExtremeRecipeTypes.EXTREME_KINETIC_SMITHING_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<ExtremeKineticSmithingRecipe> {
        public static final MapCodec<ExtremeKineticSmithingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Ingredient.CODEC.fieldOf("template").forGetter(r -> r.template),
                        Ingredient.CODEC.fieldOf("base").forGetter(r -> r.base),
                        Ingredient.CODEC.fieldOf("addition").forGetter(r -> r.additions),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result)
                ).apply(instance, ExtremeKineticSmithingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ExtremeKineticSmithingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, r -> r.template,
                        Ingredient.CONTENTS_STREAM_CODEC, r -> r.base,
                        Ingredient.CONTENTS_STREAM_CODEC, r -> r.additions,
                        ItemStack.STREAM_CODEC, r -> r.result,
                        ExtremeKineticSmithingRecipe::new);

        @Override
        public @NotNull MapCodec<ExtremeKineticSmithingRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, ExtremeKineticSmithingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
