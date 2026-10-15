package committee.nova.avaritia_singularity_stress.compat.jei;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticSmithingRecipe;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import committee.nova.avaritia_singularity_stress.init.registry.ModExtremeRecipeTypes;
import committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.EmptyBackground;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@JeiPlugin
public class ExtremeMechanicalCrafterJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID = ResourceLocation
            .fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "jei_plugin");

    private static final int MIN_TIER = 1;  // 幽冥(sculk)
    private static final int MAX_TIER = 4;  // 终焉(extreme)

    private static final String[] TIER_TITLE_KEYS = {
            "jei.extreme_crafter.tier.sculk",   // tier 1
            "jei.extreme_crafter.tier.nether",  // tier 2
            "jei.extreme_crafter.tier.end",     // tier 3
            "jei.extreme_crafter.tier.extreme"  // tier 4
    };

    /**
     * 各等级对应的无尽贪婪工作台方块(类目图标的副物品,
     * Re-Avaritia 字段名已从其 jar 核实)。tier 0 兜底为幽冥。
     */
    private static final java.util.function.IntFunction<net.minecraft.world.level.block.Block> TIER_TABLE =
            tier -> switch (tier) {
                case 2 -> committee.nova.mods.avaritia.init.registry.ModBlocks.nether_crafting_table.get();
                case 3 -> committee.nova.mods.avaritia.init.registry.ModBlocks.end_crafting_table.get();
                case 4 -> committee.nova.mods.avaritia.init.registry.ModBlocks.extreme_crafting_table.get();
                default -> committee.nova.mods.avaritia.init.registry.ModBlocks.sculk_crafting_table.get();
            };

    /**
     * Re-Avaritia 1.21.1 四个分级工作台原版 JEI type id
     * 用于把终焉合成器登记为它们的催化剂
     * （已从其 jar 字节码核实）
     */
    private static final List<String> AVARITIA_TIER_RECIPE_TYPES = List.of(
            "sculk_craft",   // tier 1
            "nether_craft",  // tier 2
            "end_craft",     // tier 3
            "extreme_craft"  // tier 4
    );

    /**
     * Re-Avaritia 1.21.1 终焉锻造配方的原版 JEI type id
     * (取自其 ExtremeSmithingRecipeCategory 的 recipe type 注册名)。
     */
    private static final String AVARITIA_SMITHING_RECIPE_TYPE = "extreme_smithing";

    private final List<CreateRecipeCategory<ITierCraftingRecipe>> allCategories = new ArrayList<>();
    private CreateRecipeCategory<ExtremeSmithingRecipe> smithingCategory;
    private CreateRecipeCategory<ExtremeKineticCraftingRecipe> kineticCraftingCategory;
    private CreateRecipeCategory<ExtremeKineticSmithingRecipe> kineticSmithingCategory;

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    // ==========================================================
    // 1. 注册自定义分类
    // ==========================================================
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        allCategories.clear();
        for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
            allCategories.add(new ExtremeTierCategory(buildInfo(tier)));
        }
        smithingCategory = new ExtremeSmithingCategory(buildSmithingInfo());
        kineticCraftingCategory = new ExtremeKineticCraftingCategory(buildKineticCraftingInfo());
        kineticSmithingCategory = new ExtremeKineticSmithingCategory(buildKineticSmithingInfo());

        List<IRecipeCategory<?>> categories = new ArrayList<>(allCategories);
        categories.add(smithingCategory);
        categories.add(kineticCraftingCategory);
        categories.add(kineticSmithingCategory);
        registration.addRecipeCategories(categories.toArray(IRecipeCategory[]::new));
    }

    // ==========================================================
    // 2. 注册配方(缺失此步 JEI 按 U 无法查询用途)
    //    CreateRecipeCategory.registerRecipes 会把 Info.recipes
    //    supplier 的内容注册到 Info.recipeType 上
    // ==========================================================
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        allCategories.forEach(c -> c.registerRecipes(registration));
        smithingCategory.registerRecipes(registration);
        kineticCraftingCategory.registerRecipes(registration);
        kineticSmithingCategory.registerRecipes(registration);
    }

    // ==========================================================
    // 3. 注册配方催化剂
    //    (A) 分级分类:两种终焉方块都可用(锻造器会回落到分级配方)
    //    (B) 锻造分类:只有锻造器能启用锻造配方
    //    (C) Avaritia 原版四个分级配方类型 → 终焉合成器
    //    (D) Avaritia 原版锻造配方类型 → 终焉锻造器
    // ==========================================================
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // ---------- (A) 自定义分级分类 ----------
        for (CreateRecipeCategory<ITierCraftingRecipe> category : allCategories) {
            registration.addRecipeCatalyst(
                    new ItemStack(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get()),
                    category.getRecipeType());
            registration.addRecipeCatalyst(
                    new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get()),
                    category.getRecipeType());
        }

        // ---------- (B) 自定义锻造分类 ----------
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get()),
                smithingCategory.getRecipeType());

        // ---------- (B2) 本模组专属分类 ----------
        // 终焉动能合成:两种方块都能执行(锻造器可回落到合成)
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get()),
                kineticCraftingCategory.getRecipeType());
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get()),
                kineticCraftingCategory.getRecipeType());
        // 终焉动能锻造:只有锻造器能启用
        registration.addRecipeCatalyst(
                new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get()),
                kineticSmithingCategory.getRecipeType());

        // ---------- (C) Avaritia 原版分级配方 ----------
        for (String typeId : AVARITIA_TIER_RECIPE_TYPES) {
            registration.getJeiHelpers()
                    .getRecipeType(ResourceLocation.fromNamespaceAndPath("avaritia", typeId))
                    .ifPresent(type -> registration.addRecipeCatalyst(
                            new ItemStack(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get()),
                            type));
        }

        // ---------- (D) Avaritia 原版锻造配方 ----------
        registration.getJeiHelpers()
                .getRecipeType(ResourceLocation.fromNamespaceAndPath("avaritia",
                        AVARITIA_SMITHING_RECIPE_TYPE))
                .ifPresent(type -> registration.addRecipeCatalyst(
                        new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get()),
                        type));
    }

    // ==========================================================
    // 4. 构建分级分类信息
    // ==========================================================
    private CreateRecipeCategory.Info<ITierCraftingRecipe> buildInfo(int tier) {
        int tierIndex = tier - MIN_TIER;
        Supplier<List<RecipeHolder<ITierCraftingRecipe>>> recipes = () -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return List.of();
            }
            List<RecipeHolder<ITierCraftingRecipe>> out = new ArrayList<>();
            for (RecipeHolder<ITierCraftingRecipe> holder : level.getRecipeManager()
                    .getAllRecipesFor(committee.nova.mods.avaritia.init.registry.ModRecipeTypes.CRAFTING_TABLE_RECIPE.get())) {
                if (holder.value().getTier() == tier) {
                    out.add(holder);
                }
                // tier 0(无等级要求)的配方默认进幽冥层
                else if (tier == MIN_TIER && !holder.value().hasRequiredTier()) {
                    out.add(holder);
                }
            }
            return out;
        };
        return new CreateRecipeCategory.Info<>(
                tierRecipeType(tier),
                Component.translatable(TIER_TITLE_KEYS[tierIndex]),
                new EmptyBackground(177, 107),
                new DoubleItemIcon(
                        () -> new ItemStack(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get()),
                        () -> new ItemStack(TIER_TABLE.apply(tier))),
                recipes,
                List.of(() -> new ItemStack(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get())));
    }

    // ==========================================================
    // 5. 构建锻造分类信息
    //    主图标 = 终焉锻造器自身,第二图标 = 终焉锻造台
    // ==========================================================
    private CreateRecipeCategory.Info<ExtremeSmithingRecipe> buildSmithingInfo() {
        Supplier<List<RecipeHolder<ExtremeSmithingRecipe>>> recipes = () -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return List.of();
            }
            return level.getRecipeManager()
                    .getAllRecipesFor(committee.nova.mods.avaritia.init.registry.ModRecipeTypes
                            .EXTREME_SMITHING_RECIPE.get());
        };
        return new CreateRecipeCategory.Info<>(
                smithingRecipeType(),
                Component.translatable("jei.extreme_crafter.smithing"),
                new EmptyBackground(177, 107),
                new DoubleItemIcon(
                        // 主图标:终焉锻造器自己
                        () -> new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get()),
                        // 第二图标:无尽贪婪的终焉锻造台
                        () -> new ItemStack(committee.nova.mods.avaritia.init.registry.ModBlocks
                                .extreme_smithing_table.get())),
                recipes,
                List.of(() -> new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get())));
    }

    // ==========================================================
    // 5b. 构建本模组专属分类信息
    //     终焉动能合成:主图标 = 终焉合成器,副图标 = 终焉锻造器
    //     终焉动能锻造:主图标 = 终焉锻造器,副图标 = 终焉锻造台
    // ==========================================================
    private CreateRecipeCategory.Info<ExtremeKineticCraftingRecipe> buildKineticCraftingInfo() {
        Supplier<List<RecipeHolder<ExtremeKineticCraftingRecipe>>> recipes = () -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return List.of();
            }
            return level.getRecipeManager()
                    .getAllRecipesFor(ModExtremeRecipeTypes.EXTREME_KINETIC_CRAFTING_TYPE.get());
        };
        return new CreateRecipeCategory.Info<>(
                kineticCraftingRecipeType(),
                Component.translatable("jei.extreme_crafter.kinetic_crafting"),
                new EmptyBackground(177, 107),
                new DoubleItemIcon(
                        () -> new ItemStack(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get()),
                        () -> new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get())),
                recipes,
                List.of(
                        () -> new ItemStack(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get()),
                        () -> new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get())));
    }

    private CreateRecipeCategory.Info<ExtremeKineticSmithingRecipe> buildKineticSmithingInfo() {
        Supplier<List<RecipeHolder<ExtremeKineticSmithingRecipe>>> recipes = () -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return List.of();
            }
            return level.getRecipeManager()
                    .getAllRecipesFor(ModExtremeRecipeTypes.EXTREME_KINETIC_SMITHING_TYPE.get());
        };
        return new CreateRecipeCategory.Info<>(
                kineticSmithingRecipeType(),
                Component.translatable("jei.extreme_crafter.kinetic_smithing"),
                new EmptyBackground(177, 107),
                new DoubleItemIcon(
                        () -> new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get()),
                        () -> new ItemStack(committee.nova.mods.avaritia.init.registry.ModBlocks
                                .extreme_smithing_table.get())),
                recipes,
                List.of(() -> new ItemStack(ModBlocks.EXTREME_SMITHING_CRAFTER.get())));
    }

    // ==========================================================
    // 6. 工具方法
    // ==========================================================
    @SuppressWarnings("unchecked")
    private static RecipeType<RecipeHolder<ITierCraftingRecipe>> tierRecipeType(int tier) {
        return (RecipeType<RecipeHolder<ITierCraftingRecipe>>) (RecipeType<?>) RecipeType
                .create(AvaritiaSingularityStressMod.MODID, "extreme_tier_" + tier, RecipeHolder.class);
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<RecipeHolder<ExtremeSmithingRecipe>> smithingRecipeType() {
        return (RecipeType<RecipeHolder<ExtremeSmithingRecipe>>) (RecipeType<?>) RecipeType
                .create(AvaritiaSingularityStressMod.MODID, "extreme_smithing", RecipeHolder.class);
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<RecipeHolder<ExtremeKineticCraftingRecipe>> kineticCraftingRecipeType() {
        return (RecipeType<RecipeHolder<ExtremeKineticCraftingRecipe>>) (RecipeType<?>) RecipeType
                .create(AvaritiaSingularityStressMod.MODID, "extreme_kinetic_crafting", RecipeHolder.class);
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<RecipeHolder<ExtremeKineticSmithingRecipe>> kineticSmithingRecipeType() {
        return (RecipeType<RecipeHolder<ExtremeKineticSmithingRecipe>>) (RecipeType<?>) RecipeType
                .create(AvaritiaSingularityStressMod.MODID, "extreme_kinetic_smithing", RecipeHolder.class);
    }
}
