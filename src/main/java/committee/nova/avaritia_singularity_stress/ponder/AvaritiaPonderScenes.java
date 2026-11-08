package committee.nova.avaritia_singularity_stress.ponder;

import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import committee.nova.avaritia_singularity_stress.init.registry.ModItems;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 本模组思索场景的注册总入口。
 * <p>
 * 四场分两类：
 * <ul>
 *   <li><b>包壳两场</b>共用同一套分镜（{@link #encasingCogwheels}），
 *       只有「外壳是谁」和「包壳方块是谁」不同，分镜就写在下面；</li>
 *   <li><b>传送带 / 无线两场</b>分别见 {@link } 与
 *       {@link }，它们演示的机制与包壳无关。</li>
 * </ul>
 * <p>
 * 下面先说包壳两场：
 * <ul>
 *   <li>炽骨立方机壳 → {@code blaze_cube_encased_cogwheel} / {@code ..._large_cogwheel}</li>
 *   <li>水晶矩阵机壳 → {@code crystal_matrix_encased_cogwheel} / {@code ..._large_cogwheel}</li>
 * </ul>
 * 机壳方块本身不一定属于本模组（水晶矩阵的来自 {@code avaritia_integration}），
 * 所以「手持什么物品」用 {@link EncasedBlock#getCasing()} 反查，不硬编码命名空间。
 * <p>
 * <b>分镜规则</b>（对齐 Create 官方 {@code create:cogwheel_casing}）：
 * <ul>
 *   <li>动力部件连成一条线（轴-小齿轮-轴-大齿轮-轴），孤立单格不转也不画；</li>
 *   <li>每次 {@code setBlocks} 后必须补 {@code setKineticSpeed}，否则被换掉那格当场停转；</li>
 *   <li>先演后讲，整场只留一句台词，零形容词。</li>
 * </ul>
 */
public final class AvaritiaPonderScenes {

    private AvaritiaPonderScenes() {
    }

    /** 全模组共用的分类标签：无尽贪婪。lang key 为 {@code <ns>.ponder.tag.avaritia}。 */
    public static final ResourceLocation TAG_AVARITIA =
            ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "avaritia");

    /** 剧本 id 同时决定 lang key 中段和 .nbt 文件名。 */
    public static final String SCENE_BLAZE_CUBE = "blaze_cube_casing";
    public static final String SCENE_CRYSTAL_MATRIX = "crystal_matrix_casing";

    /** 与 .nbt 对齐的布局常量（5x5 底板，动力线在 y=1 / z=2，x 0..4）。 */
    private static final int BASE_SIZE = 5;
    private static final int MACHINE_Y = 1;
    private static final int LANE_Z = 2;

    // ------------------------------------------------------------------ 注册

    public static void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {


        PonderStoryBoard blazeBoard = (scene, util) -> encasingCogwheels(scene, util, SCENE_BLAZE_CUBE,
                "Blaze Cube Casing",
                ModBlocks.BLAZE_CUBE_ENCASED_COGWHEEL.get(),
                ModBlocks.BLAZE_CUBE_ENCASED_LARGE_COGWHEEL.get());

        PonderStoryBoard crystalBoard = (scene, util) -> encasingCogwheels(scene, util, SCENE_CRYSTAL_MATRIX,
                "Crystal Matrix Casing",
                ModBlocks.CRYSTAL_MATRIX_ENCASED_COGWHEEL.get(),
                ModBlocks.CRYSTAL_MATRIX_ENCASED_LARGE_COGWHEEL.get());

        // 炽骨立方：机壳本体 + 两种齿轮箱，三个入口都能开出同一场（共用 .nbt 与 lang）
        helper.forComponents(
                        itemId(ModItems.BLAZE_CUBE_CASING),
                        itemId(ModItems.BLAZE_CUBE_ENCASED_COGWHEEL),
                        itemId(ModItems.BLAZE_CUBE_ENCASED_LARGE_COGWHEEL))
                .addStoryBoard(SCENE_BLAZE_CUBE, blazeBoard, TAG_AVARITIA);

        // 水晶矩阵：机壳方块属于 avaritia_integration，从包壳方块反查它的物品 id，
        // 这样手持机壳按 W 也能开思索，而不是只有齿轮箱能开
        List<ResourceLocation> crystalComponents = new ArrayList<>();
        ResourceLocation crystalCasingId = casingItemId(ModBlocks.CRYSTAL_MATRIX_ENCASED_COGWHEEL.get());
        if (crystalCasingId != null) {
            crystalComponents.add(crystalCasingId);
        }
        crystalComponents.add(itemId(ModItems.CRYSTAL_MATRIX_ENCASED_COGWHEEL));
        crystalComponents.add(itemId(ModItems.CRYSTAL_MATRIX_ENCASED_LARGE_COGWHEEL));
        helper.forComponents(crystalComponents)
                .addStoryBoard(SCENE_CRYSTAL_MATRIX, crystalBoard, TAG_AVARITIA);
    }

    public static void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        // 标签图标用「无尽催化剂」（Re-Avaritia 的物品）；万一那个模组没装，
        // 退回本模组的炽骨立方机壳，避免标签没图标
        Item icon = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("avaritia", "infinity_catalyst"));
        if (icon == Items.AIR) {
            icon = ModItems.BLAZE_CUBE_CASING.get();
        }

        helper.registerTag(TAG_AVARITIA)
                .addToIndex()
                .item(icon, true, false)
                .title("Avaritia")
                .description("Blocks and components added by this mod")
                .register();

        // 两个机壳本体也要作为「关联词条」挂在标签下。
        // 场景注册时的 forComponents(...) 只会把「有场景的组件」带进标签，
        // 这里显式补一次，保证它们稳定出现在标签页里。
        //.add(itemId(ModItems.))
        helper.addToTag(TAG_AVARITIA)
                .add(itemId(ModItems.BLAZE_CUBE_CASING));

        // 水晶矩阵机壳属于 avaritia_integration，从包壳方块反查物品 id
        ResourceLocation crystalCasing = casingItemId(ModBlocks.CRYSTAL_MATRIX_ENCASED_COGWHEEL.get());
        if (crystalCasing != null) {
            helper.addTagToComponent(crystalCasing, TAG_AVARITIA);
        }
        helper.addToTag(TAG_AVARITIA)
                .add(itemId(ModItems.EXTREME_MECHANICAL_CRAFTER))
                .add(itemId(ModItems.EXTREME_SMITHING_CRAFTER))
                .add(itemId(ModItems.NEUTRON_EXTENDO_GRIP))
                .add(itemId(ModItems.SINGULARITY_ROTATION_SPEED_CONTROLLER))
                .add(itemId(ModItems.WIRELESS_ITEM_FUNNEL))
                .add(itemId(ModItems.WARP_TUNNEL))
                .add(itemId(ModItems.WIRELESS_STRESS_BRIDGE_RECEIVER))
                .add(itemId(ModItems.WIRELESS_STRESS_BRIDGE_SENDER));
    }

    // ------------------------------------------------------------------ 分镜

    /**
     * 两场共用：一条动力线转起来 → 右键包小齿轮 → 右键包大齿轮 → 一句话收尾。
     *
     * @param sceneId  剧本 id，`scene.title` 用它拼 lang key
     * @param casingName 外壳的英文名，拼进默认台词（编辑模式下才显示，正式文案走 lang）
     */
    private static void encasingCogwheels(SceneBuilder builder, SceneBuildingUtil util, String sceneId,
                                          String casingName, Block encasedCog, Block encasedLargeCog) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title(sceneId, "Encasing Cogwheels");
        scene.configureBasePlate(0, 0, BASE_SIZE);
        scene.idle(5);

        // 一条连通的动力线：轴 小齿轮 轴 大齿轮 轴
        Selection powerLine = util.select().fromTo(0, MACHINE_Y, LANE_Z, 4, MACHINE_Y, LANE_Z);
        Selection smallCogSpot = util.select().position(1, MACHINE_Y, LANE_Z);
        Selection largeCogSpot = util.select().position(3, MACHINE_Y, LANE_Z);
        BlockPos smallCogPos = util.grid().at(1, MACHINE_Y, LANE_Z);
        BlockPos largeCogPos = util.grid().at(3, MACHINE_Y, LANE_Z);

        // 手持的机壳物品从包壳方块反查，兼容外壳来自别的模组的情况
        ItemStack casingStack = casingStackOf(encasedCog);

        // ---------------- 底板 ----------------
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // ---------------- 动力线落下并转起来 ----------------
        scene.world().showSection(powerLine, Direction.DOWN);
        scene.idle(20);
        scene.world().setKineticSpeed(powerLine, 32);
        scene.effects().rotationDirectionIndicator(smallCogPos);
        scene.idle(20);

        // ---------------- 包住小齿轮 ----------------
        scene.overlay().showControls(util.vector().topOf(smallCogPos), Pointing.DOWN, 50)
                .rightClick()
                .withItem(casingStack);
        scene.idle(7);
        scene.world().setBlocks(smallCogSpot, axisX(encasedCog), true);
        // 新方块实体没有速度，必须重给，否则被包住的那一格当场停转
        scene.world().setKineticSpeed(powerLine, 32);
        scene.effects().indicateSuccess(smallCogPos);
        scene.idle(25);

        // ---------------- 包住大齿轮 ----------------
        scene.overlay().showControls(util.vector().topOf(largeCogPos), Pointing.DOWN, 50)
                .rightClick()
                .withItem(casingStack);
        scene.idle(7);
        scene.world().setBlocks(largeCogSpot, axisX(encasedLargeCog), true);
        scene.world().setKineticSpeed(powerLine, 32);
        scene.effects().indicateSuccess(largeCogPos);
        scene.idle(25);

        // ---------------- 一句话收尾（先演后讲）----------------
        scene.overlay().showText(80)
                .text(casingName + " can be used to decorate cogwheels")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(smallCogPos));
        scene.idle(90);

        scene.markAsFinished();
    }

    // ------------------------------------------------------------------ 小工具

    /** 包壳方块一律沿 X 轴摆。 */
    private static BlockState axisX(Block block) {
        return block.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
    }

    /** 从包壳方块反查它的外壳方块，再换成物品。外壳可能在别的模组里。 */
    private static ItemStack casingStackOf(Block encasedBlock) {
        if (encasedBlock instanceof EncasedBlock encased) {
            Block casing = encased.getCasing();
            if (casing != null) {
                return new ItemStack(casing.asItem());
            }
        }
        return ItemStack.EMPTY;
    }

    private static ResourceLocation itemId(Supplier<? extends Item> item) {
        return BuiltInRegistries.ITEM.getKey(item.get());
    }

    /** 从包壳方块反查外壳物品的 id；不是包壳方块时返回 null。 */
    private static ResourceLocation casingItemId(Block encasedBlock) {
        if (encasedBlock instanceof EncasedBlock encased) {
            Block casing = encased.getCasing();
            if (casing != null) {
                return BuiltInRegistries.ITEM.getKey(casing.asItem());
            }
        }
        return null;
    }
}
