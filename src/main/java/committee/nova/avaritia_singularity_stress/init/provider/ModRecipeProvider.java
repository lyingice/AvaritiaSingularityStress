package committee.nova.avaritia_singularity_stress.init.provider;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticSmithingRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 终焉动能合成 / 终焉动能锻造的配方数据生成(仅基建)。
 * <p>
 * 默认 {@link #generate()} 为空 —— 不产出任何示例配方。
 * 需要时在 generate() 里调用 builder,例如:
 * <pre>{@code
 * extremeKineticCrafting(Items.X, 1)
 *     .key('A', Items.IRON_INGOT)
 *     .patternLine("AA")
 *     .patternLine("AA")
 *     .disallowMirrored()
 *     .build(writer);
 *
 * extremeKineticSmithing(Items.Y)
 *     .template(Items.A)
 *     .base(Items.B)
 *     .addition(Ingredient.of(Items.C, Items.D, Items.E))
 *     .build(writer, ResourceLocation...);
 * }</pre>
 * 也可以完全绕开 datagen,直接写数据包 JSON:
 * <pre>{@code
 * // data/<ns>/recipe/<name>.json
 * {"type": "avaritia_singularity_stress:extreme_kinetic_crafting",
 *  "pattern": ["AA","AA"], "key": {"A": {"item": "..."}},
 *  "result": {"id": "...", "count": 1}, "accept_mirrored": false}
 *
 * {"type": "avaritia_singularity_stress:extreme_kinetic_smithing",
 *  "template": {"item": "..."}, "base": {"item": "..."},
 *  "addition": [{"item": "a"}, {"item": "b"}, {"item": "c"}],
 *  "result": {"id": "...", "count": 1}}
 * }</pre>
 */
public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // 仅基建:默认不生成任何配方。
    }

    // ===== 终焉动能合成 builder =====

    public static ExtremeKineticCraftingRecipeBuilder extremeKineticCrafting(ItemLike result, int count) {
        return new ExtremeKineticCraftingRecipeBuilder(result, count);
    }

    public static ExtremeKineticCraftingRecipeBuilder extremeKineticCrafting(ItemLike result) {
        return extremeKineticCrafting(result, 1);
    }

    // ===== 终焉动能锻造 builder =====

    public static ExtremeKineticSmithingRecipeBuilder extremeKineticSmithing(ItemLike result) {
        return new ExtremeKineticSmithingRecipeBuilder(result);
    }

    // ===== 终焉动能合成 =====

    public static class ExtremeKineticCraftingRecipeBuilder {
        private final Item result;
        private final int count;
        private final List<String> pattern = Lists.newArrayList();
        private final Map<Character, Ingredient> key = Maps.newLinkedHashMap();
        private boolean acceptMirrored = true;

        private ExtremeKineticCraftingRecipeBuilder(ItemLike result, int count) {
            this.result = result.asItem();
            this.count = count;
        }

        public ExtremeKineticCraftingRecipeBuilder key(char symbol, TagKey<Item> tag) {
            return key(symbol, Ingredient.of(tag));
        }

        public ExtremeKineticCraftingRecipeBuilder key(char symbol, ItemLike item) {
            return key(symbol, Ingredient.of(item));
        }

        public ExtremeKineticCraftingRecipeBuilder key(char symbol, Ingredient ingredient) {
            if (this.key.containsKey(symbol)) {
                throw new IllegalArgumentException("Symbol '" + symbol + "' is already defined!");
            }
            if (symbol == ' ') {
                throw new IllegalArgumentException("Symbol ' ' (whitespace) is reserved");
            }
            this.key.put(symbol, ingredient);
            return this;
        }

        public ExtremeKineticCraftingRecipeBuilder patternLine(String line) {
            if (!this.pattern.isEmpty() && line.length() != this.pattern.getFirst().length()) {
                throw new IllegalArgumentException("Pattern must be the same width on every line!");
            }
            this.pattern.add(line);
            return this;
        }

        /** 禁止水平镜像匹配(默认允许)。 */
        public ExtremeKineticCraftingRecipeBuilder disallowMirrored() {
            this.acceptMirrored = false;
            return this;
        }

        public void build(RecipeOutput output) {
            build(output, ResourceLocation.fromNamespaceAndPath(
                    AvaritiaSingularityStressMod.MODID, resultId()));
        }

        public void build(RecipeOutput output, ResourceLocation id) {
            validate(id);
            ExtremeKineticCraftingRecipe recipe = new ExtremeKineticCraftingRecipe(
                    "",
                    CraftingBookCategory.MISC,
                    ShapedRecipePattern.of(key, pattern),
                    new ItemStack(result, count),
                    true,
                    acceptMirrored);
            output.accept(id, recipe, null);
        }

        private String resultId() {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this.result).getPath();
        }

        private void validate(ResourceLocation recipeId) {
            if (pattern.isEmpty()) {
                throw new IllegalStateException("No pattern is defined for recipe " + recipeId + "!");
            }
            Set<Character> unused = Sets.newHashSet(key.keySet());
            unused.remove(' ');
            for (String line : pattern) {
                for (int i = 0; i < line.length(); ++i) {
                    char c = line.charAt(i);
                    if (!key.containsKey(c) && c != ' ') {
                        throw new IllegalStateException(
                                "Pattern in recipe " + recipeId + " uses undefined symbol '" + c + "'");
                    }
                    unused.remove(c);
                }
            }
            if (!unused.isEmpty()) {
                throw new IllegalStateException(
                        "Ingredients are defined but not used in pattern for recipe " + recipeId);
            }
        }
    }

    // ===== 终焉动能锻造 =====

    public static class ExtremeKineticSmithingRecipeBuilder {
        private final Item result;
        private Ingredient template = Ingredient.EMPTY;
        private Ingredient base = Ingredient.EMPTY;
        private Ingredient additions = Ingredient.EMPTY;

        private ExtremeKineticSmithingRecipeBuilder(ItemLike result) {
            this.result = result.asItem();
        }

        public ExtremeKineticSmithingRecipeBuilder template(TagKey<Item> tag) {
            return template(Ingredient.of(tag));
        }

        public ExtremeKineticSmithingRecipeBuilder template(ItemLike item) {
            return template(Ingredient.of(item));
        }

        public ExtremeKineticSmithingRecipeBuilder template(Ingredient ingredient) {
            this.template = ingredient;
            return this;
        }

        public ExtremeKineticSmithingRecipeBuilder base(TagKey<Item> tag) {
            return base(Ingredient.of(tag));
        }

        public ExtremeKineticSmithingRecipeBuilder base(ItemLike item) {
            return base(Ingredient.of(item));
        }

        public ExtremeKineticSmithingRecipeBuilder base(Ingredient ingredient) {
            this.base = ingredient;
            return this;
        }

        /**
         * 三个添加物共用一个 Ingredient;
         * 传多候选 Ingredient(如 Ingredient.of(a, b, c))即为 OR 列表。
         */
        public ExtremeKineticSmithingRecipeBuilder addition(Ingredient ingredient) {
            this.additions = ingredient;
            return this;
        }

        public void build(RecipeOutput output) {
            build(output, ResourceLocation.fromNamespaceAndPath(
                    AvaritiaSingularityStressMod.MODID,
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this.result).getPath()));
        }

        public void build(RecipeOutput output, ResourceLocation id) {
            ExtremeKineticSmithingRecipe recipe = new ExtremeKineticSmithingRecipe(
                    template, base, additions, new ItemStack(result));
            output.accept(id, recipe, null);
        }
    }
}
