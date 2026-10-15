package committee.nova.avaritia_singularity_stress.init.registry;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticSmithingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 终焉合成器家族的专属配方类型注册。
 * <p>
 * 与 Create 的 {@code create:mechanical_crafting} 同构:JSON 顶层的
 * {@code "type"} 字段使用 <b>serializer</b> 的注册名;
 * RecipeType 注册名只用于运行期分桶。
 */
public class ModExtremeRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, AvaritiaSingularityStressMod.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, AvaritiaSingularityStressMod.MODID);

    // ===== 终焉动能合成:shaped 网格(最大 9x9,可选镜像) =====
    public static final Supplier<RecipeType<ExtremeKineticCraftingRecipe>> EXTREME_KINETIC_CRAFTING_TYPE =
            TYPES.register("extreme_kinetic_crafting",
                    () -> RecipeType.simple(id("extreme_kinetic_crafting")));

    public static final Supplier<RecipeSerializer<ExtremeKineticCraftingRecipe>> EXTREME_KINETIC_CRAFTING_SERIALIZER =
            SERIALIZERS.register("extreme_kinetic_crafting",
                    ExtremeKineticCraftingRecipe.Serializer::new);

    // ===== 终焉动能锻造:模板 / 基座 / 3 添加物 =====
    public static final Supplier<RecipeType<ExtremeKineticSmithingRecipe>> EXTREME_KINETIC_SMITHING_TYPE =
            TYPES.register("extreme_kinetic_smithing",
                    () -> RecipeType.simple(id("extreme_kinetic_smithing")));

    public static final Supplier<RecipeSerializer<ExtremeKineticSmithingRecipe>> EXTREME_KINETIC_SMITHING_SERIALIZER =
            SERIALIZERS.register("extreme_kinetic_smithing",
                    ExtremeKineticSmithingRecipe.Serializer::new);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, path);
    }

    public static void register(IEventBus modEventBus) {
        // 大于 3x3 的 pattern 解码前必须扩容(与 Create AllRecipeTypes.register 同一调用,幂等)。
        ShapedRecipePattern.setCraftingSize(9, 9);
        TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
    }

    private ModExtremeRecipeTypes() {
    }
}
