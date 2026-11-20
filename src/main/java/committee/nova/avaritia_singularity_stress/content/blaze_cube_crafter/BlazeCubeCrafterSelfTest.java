package committee.nova.avaritia_singularity_stress.content.blaze_cube_crafter;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeConnectedInputHandler;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeCrafterHelper;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler.GroupedItems;
import committee.nova.avaritia_singularity_stress.content.recipes.BlazeCubeCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe;

import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * B26 炽骨立方合成器服务端自检（专用服务端，默认关闭）。
 * <p>
 * 验证两件事：① 炽骨与终焉系列<b>独立组网</b>（箭头链/扳手库存/连接纹理共用 canConnect），
 * 终焉动力合成器与锻造器仍互通；② 配方<b>只放行前两档工作台</b>，拒绝末地/终焉档与本模组动能合成；
 * ③ B31 炽骨专属类型 blaze_cube_crafting 两合成器都接受、工作台分桶不可做、终焉动能优先级在前。
 * <p>
 * 触发：{@code -Davaritia.blaze_cube_crafter.selftest=true}，等 20 tick 后跑一次并 halt。
 * 配方用例通过 {@code RecipeManager.replaceRecipes} 注入受控测试配方（只影响这台一次性测试服）。
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID)
public final class BlazeCubeCrafterSelfTest {

    private static final String ENABLE_PROPERTY = "avaritia.blaze_cube_crafter.selftest";
    private static boolean finished;

    private BlazeCubeCrafterSelfTest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || finished) {
            return;
        }
        if (event.getServer().getTickCount() < 20) {
            return;
        }
        finished = true;
        runAll(event.getServer());
    }

    private static void runAll(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> result = new ArrayList<>();
        int pass = 0;
        int total = 30;
        try {
            // 清理测试区：世界跨运行持久化，残留 BE（如已合并的库存组）会干扰断言。
            for (BlockPos p : BlockPos.betweenClosed(-1, 99, -1, 10, 122, 25)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.getEntitiesOfClass(ItemEntity.class, new AABB(-1, 99, -1, 11, 123, 26))
                    .forEach(ItemEntity::discard);

            Block blaze = ModBlocks.BLAZE_CUBE_CRAFTER.get();
            Block mech = ModBlocks.EXTREME_MECHANICAL_CRAFTER.get();
            Block smith = ModBlocks.EXTREME_SMITHING_CRAFTER.get();

            // ---------- 注册 ----------
            ExtremeMechanicalCrafterBlockEntity b0 = place(level, new BlockPos(0, 100, 0), blaze);
            pass += check(result, "注册·放置得到炽骨实体", b0 instanceof BlazeCubeCrafterBlockEntity);

            // ---------- canConnect 矩阵 ----------
            BlockState bs = blaze.defaultBlockState();
            BlockState ms = mech.defaultBlockState();
            BlockState ss = smith.defaultBlockState();
            pass += check(result, "canConnect 炽骨↔炽骨", ExtremeCrafterHelper.canConnect(bs, bs));
            pass += check(result, "canConnect 炽骨↔终焉动力=否", !ExtremeCrafterHelper.canConnect(bs, ms));
            pass += check(result, "canConnect 炽骨↔终焉锻造=否", !ExtremeCrafterHelper.canConnect(bs, ss));
            pass += check(result, "canConnect 终焉动力↔锻造=是", ExtremeCrafterHelper.canConnect(ms, ss));
            pass += check(result, "canConnect 终焉动力↔终焉动力=是", ExtremeCrafterHelper.canConnect(ms, ms));

            // ---------- 箭头链隔离（垂直堆叠，同朝向同 pointing，几何上本可互指）----------
            ExtremeMechanicalCrafterBlockEntity lowBlaze = place(level, new BlockPos(0, 100, 8), blaze);
            place(level, new BlockPos(0, 101, 8), mech);
            pass += check(result, "箭头链 炽骨→终焉动力 被隔断",
                    ExtremeRecipeGridHandler.getTargetingCrafter(lowBlaze) == null);

            ExtremeMechanicalCrafterBlockEntity lowBlaze2 = place(level, new BlockPos(0, 100, 12), blaze);
            ExtremeMechanicalCrafterBlockEntity topBlaze2 = place(level, new BlockPos(0, 101, 12), blaze);
            pass += check(result, "箭头链 炽骨→炽骨 连通",
                    ExtremeRecipeGridHandler.getTargetingCrafter(lowBlaze2) == topBlaze2);

            ExtremeMechanicalCrafterBlockEntity lowMech = place(level, new BlockPos(0, 100, 16), mech);
            ExtremeMechanicalCrafterBlockEntity topSmith = place(level, new BlockPos(0, 101, 16), smith);
            pass += check(result, "箭头链 终焉动力→锻造 仍连通",
                    ExtremeRecipeGridHandler.getTargetingCrafter(lowMech) == topSmith);

            // ---------- 前置链隔离 ----------
            ExtremeMechanicalCrafterBlockEntity topMech = place(level, new BlockPos(0, 101, 20), mech);
            ExtremeMechanicalCrafterBlockEntity lowBlaze3 = place(level, new BlockPos(0, 100, 20), blaze);
            pass += check(result, "前置链 终焉动力 不含炽骨前置",
                    !ExtremeRecipeGridHandler.getPrecedingCrafters(topMech).contains(lowBlaze3));

            ExtremeMechanicalCrafterBlockEntity topBlaze4 = place(level, new BlockPos(0, 101, 24), blaze);
            ExtremeMechanicalCrafterBlockEntity lowBlaze4 = place(level, new BlockPos(0, 100, 24), blaze);
            pass += check(result, "前置链 炽骨 含炽骨前置",
                    ExtremeRecipeGridHandler.getPrecedingCrafters(topBlaze4).contains(lowBlaze4));

            // ---------- 扳手 shouldConnect ----------
            BlockPos ref = new BlockPos(8, 100, 0);
            place(level, ref, blaze);
            place(level, ref.east(), mech);
            pass += check(result, "扳手 炽骨→终焉动力 拒绝",
                    !ExtremeConnectedInputHandler.shouldConnect(level, ref, Direction.EAST, Direction.EAST));
            place(level, ref.east(), blaze);
            pass += check(result, "扳手 炽骨→炽骨 允许",
                    ExtremeConnectedInputHandler.shouldConnect(level, ref, Direction.EAST, Direction.EAST));

            // ---------- 库存组 toggleConnection ----------
            BlockPos g1 = new BlockPos(8, 100, 8);
            BlockPos g2 = g1.east();
            ExtremeMechanicalCrafterBlockEntity c1 = place(level, g1, blaze);
            place(level, g2, mech);
            ExtremeConnectedInputHandler.toggleConnection(level, g1, g2);
            pass += check(result, "库存组 跨系列拒绝合并",
                    c1.getInput().isController() && c1.getInput().getData().size() == 1);

            BlockPos g3 = new BlockPos(8, 100, 12);
            BlockPos g4 = g3.east();
            ExtremeMechanicalCrafterBlockEntity c3 = place(level, g3, blaze);
            ExtremeMechanicalCrafterBlockEntity c4 = place(level, g4, blaze);
            ExtremeConnectedInputHandler.toggleConnection(level, g3, g4);
            pass += check(result, "库存组 同系列正常合并",
                    !(c3.getInput().isController() && c4.getInput().isController()));

            // ---------- 配方档位白名单（受控注入）----------
            ExtremeMechanicalCrafterBlockEntity rb = place(level, new BlockPos(0, 120, 0), blaze);
            ExtremeMechanicalCrafterBlockEntity rm = place(level, new BlockPos(4, 120, 0), mech);

            // 每档 2 个断言（炽骨按白名单、终焉动力恒接受），共 8 个
            pass += tierCase(level, result, rb, rm, 1, true);
            pass += tierCase(level, result, rb, rm, 2, true);
            pass += tierCase(level, result, rb, rm, 3, false);
            pass += tierCase(level, result, rb, rm, 4, false);

            // ---------- 拒绝本模组动能合成 ----------
            ShapedRecipePattern pattern = ShapedRecipePattern.of(
                    Map.of('A', Ingredient.of(Items.IRON_INGOT)), "A");
            ExtremeKineticCraftingRecipe kinetic = new ExtremeKineticCraftingRecipe(
                    "", CraftingBookCategory.MISC, pattern, new ItemStack(Items.DIAMOND), true, true);
            setRecipes(level, holder("selftest_kinetic", kinetic));
            setIronGrid(rb);
            setIronGrid(rm);
            pass += check(result, "动能合成 炽骨拒绝",
                    ExtremeRecipeGridHandler.tryToApplyRecipe(level, rb) == null);
            pass += check(result, "动能合成 终焉动力接受",
                    ExtremeRecipeGridHandler.tryToApplyRecipe(level, rm) != null);

            // ---------- B31 炽骨专属配方类型 ----------
            BlazeCubeCraftingRecipe blazeCube = new BlazeCubeCraftingRecipe(
                    "", CraftingBookCategory.MISC, pattern, new ItemStack(Items.GOLD_INGOT), true, true);
            setRecipes(level, holder("selftest_blaze_cube", blazeCube));
            setIronGrid(rb);
            setIronGrid(rm);
            pass += check(result, "炽骨类型 炽骨接受",
                    ExtremeRecipeGridHandler.tryToApplyRecipe(level, rb) != null);
            pass += check(result, "炽骨类型 终焉动力接受",
                    ExtremeRecipeGridHandler.tryToApplyRecipe(level, rm) != null);
            pass += check(result, "炽骨类型 工作台分桶不可做",
                    level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                            CraftingInput.of(1, 1, List.of(new ItemStack(Items.IRON_INGOT))), level).isEmpty());

            // 两类型同料共存：终焉动力动能优先，炽骨走专属类型（产物不同可区分）
            level.getRecipeManager().replaceRecipes(List.of(
                    holder("selftest_kinetic", kinetic), holder("selftest_blaze_cube", blazeCube)));
            setIronGrid(rb);
            setIronGrid(rm);
            ItemStack rmGot = ExtremeRecipeGridHandler.tryToApplyRecipe(level, rm);
            ItemStack rbGot = ExtremeRecipeGridHandler.tryToApplyRecipe(level, rb);
            pass += check(result, "优先级 终焉动力动能在前", rmGot != null && rmGot.is(Items.DIAMOND));
            pass += check(result, "优先级 炽骨取专属类型", rbGot != null && rbGot.is(Items.GOLD_INGOT));

            AvaritiaSingularityStressMod.LOGGER.info("[blaze-selftest] RESULT {}/{}", pass, total);
            for (String line : result) {
                AvaritiaSingularityStressMod.LOGGER.info("[blaze-selftest] {}", line);
            }
            AvaritiaSingularityStressMod.LOGGER.info("[blaze-selftest] DONE {}",
                    pass == total ? "ALL PASS" : "HAS FAILURE");
        } catch (Exception e) {
            AvaritiaSingularityStressMod.LOGGER.error("[blaze-selftest] 异常", e);
        } finally {
            server.halt(false);
        }
    }

    /** 单一档位用例：注入该档无序配方，炽骨期望 allow，终焉动力恒接受。返回 2 个断言的通过数。 */
    private static int tierCase(ServerLevel level, List<String> out,
                                ExtremeMechanicalCrafterBlockEntity blaze,
                                ExtremeMechanicalCrafterBlockEntity mech,
                                int tier, boolean blazeAllowed) {
        NonNullList<Ingredient> ing = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT));
        ShapelessTableCraftingRecipe recipe =
                new ShapelessTableCraftingRecipe(ing, new ItemStack(Items.DIAMOND), tier);
        setRecipes(level, holder("selftest_tier_" + tier, recipe));
        setIronGrid(blaze);
        setIronGrid(mech);
        boolean blazeGot = ExtremeRecipeGridHandler.tryToApplyRecipe(level, blaze) != null;
        boolean mechGot = ExtremeRecipeGridHandler.tryToApplyRecipe(level, mech) != null;
        int p = 0;
        p += check(out, "档位" + tier + " 炽骨" + (blazeAllowed ? "接受" : "拒绝"), blazeGot == blazeAllowed);
        p += check(out, "档位" + tier + " 终焉动力接受", mechGot);
        return p;
    }

    private static void setIronGrid(ExtremeMechanicalCrafterBlockEntity be) {
        GroupedItems gi = be.getGroupedItems();
        gi.grid.clear();
        gi.grid.put(Pair.of(0, 0), new ItemStack(Items.IRON_INGOT));
        gi.statsReady = false;
    }

    private static void setRecipes(ServerLevel level, RecipeHolder<?> holder) {
        level.getRecipeManager().replaceRecipes(List.of(holder));
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> holder(String name, T recipe) {
        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, name), recipe);
    }

    /** 放一台合成器（朝北、pointing UP），返回 BE。 */
    private static ExtremeMechanicalCrafterBlockEntity place(ServerLevel level, BlockPos pos, Block block) {
        level.getChunk(pos);
        BlockState state = block.defaultBlockState()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(ExtremeMechanicalCrafterBlock.POINTING, Pointing.UP);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        return (ExtremeMechanicalCrafterBlockEntity) level.getBlockEntity(pos);
    }

    private static int check(List<String> out, String name, boolean ok) {
        out.add(name + " -> " + (ok ? "PASS" : "FAIL"));
        return ok ? 1 : 0;
    }
}
