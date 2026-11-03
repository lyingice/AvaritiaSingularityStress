package committee.nova.avaritia_singularity_stress.ponder_demo;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 两个可以直接抄的剧本：
 * <ul>
 *   <li>{@link #collectorBasics} —— 只用 ponder 原生 API 的最小场景，适合当模板；</li>
 *   <li>{@link #collectorKinetic} —— 用 CreateSceneBuilder 的增强版，演示转速、旋转指示与手持物提示。</li>
 * </ul>
 * 剧本方法签名固定为 {@code (SceneBuilder, SceneBuildingUtil)}，是 PonderStoryBoard 的方法引用。
 */
public class ExampleScenes {

    /** 标签 id：<ns>:neutron_machines，会生成 lang key <ns>.ponder.tag.neutron_machines */
    public static final ResourceLocation TAG_NEUTRON_MACHINES =
            ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "neutron_machines");

    // ---------------------------------------------------------------- 注册

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // 组件 key：这里用方块注册名；若你有 ItemProviderEntry 之类的封装，可再 withKeyFunction 一次
        ResourceLocation collector = BuiltInRegistries.BLOCK.getKey(ModBlocks.KINETIC_NEUTRON_COLLECTOR.get());

        // addStoryBoard(组件, 剧本 id, 剧本, 标签...)
        // 剧本 id 同时决定：schematic 路径 assets/<ns>/ponder/<id>.nbt
        helper.addStoryBoard(collector, "neutron_collector", ExampleScenes::collectorBasics, TAG_NEUTRON_MACHINES)
                .orderAfter("create:shaft/relay");   // 让它在 Create 的传动杆场景之后排
    }

    public static void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TAG_NEUTRON_MACHINES)
                .addToIndex()
                .item(ModBlocks.KINETIC_NEUTRON_COLLECTOR.get(), true, false)
                .title("Neutron Machines")
                .description("Machines that need rotational force to condense neutron matter")
                .register();
    }

    public static void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("needs_rotation", "These machines only run when driven by rotational force");
    }

    // ---------------------------------------------------------------- 剧本 1：最小可用

    /**
     * 最小场景：底板 + 逐段显示机器 + 一句文字 + 成功提示。
     * 目标文件：assets/<ns>/ponder/neutron_collector.nbt（结构原点是 (0,0,0)，见手册第 5 章）
     */
    public static void collectorBasics(SceneBuilder builder, SceneBuildingUtil util) {
        // 标题的第一个参数只影响 lang key：<ns>.ponder.<这个字符串>.header / .text_N
        builder.title("neutron_collector", "Collecting neutron matter");
        builder.configureBasePlate(0, 0, 5);
        builder.scaleSceneView(1);
        builder.idle(5);

        // 先铺底板（第 0 层），再让机器从下面升起来
        builder.world().showSection(util.select().layer(0), Direction.UP);
        builder.idle(5);

        BlockPos collector = util.grid().at(2, 1, 2);
        builder.world().showSection(util.select().position(collector), Direction.DOWN);
        builder.idle(10);

        // 文字：60 tick = 3 秒；attachKeyFrame 后，玩家可以把进度条拖回这一句
        builder.overlay().showText(60)
                .text("Kinetic Neutron Collectors condense matter while rotating")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(collector, Direction.NORTH));
        builder.idle(60);

        builder.effects().indicateSuccess(collector);
        builder.idle(20);

        // 收尾：不调用它，场景会一直停在最后一帧
        builder.markAsFinished();
    }

    // ---------------------------------------------------------------- 剧本 2：Create 增强版

    /**
     * Create 增强版：用 CreateSceneBuilder 拿到 setKineticSpeed / rotationDirectionIndicator 等工具，
     * 演示「加一段结构 → 给转速 → 指针提示 → 玩家操作提示」的完整节奏。
     */
    public static void collectorKinetic(SceneBuilder builder, SceneBuildingUtil util) {
        // CreateSceneBuilder 是 PonderSceneBuilder 的子类，构造时包一层 base builder
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("neutron_collector_kinetic", "Feeding it rotational force");
        scene.configureBasePlate(0, 0, 5);
        scene.setSceneOffsetY(-1);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos collector = util.grid().at(2, 1, 2);
        BlockPos shaft = util.grid().at(2, 1, 4);
        Selection drive = util.select().fromTo(shaft, shaft.west().east());

        scene.world().showSection(util.select().position(collector), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(drive, Direction.SOUTH);
        scene.idle(10);

        // 给这一片方块设定转速（Create 专用），并放出转速指示粒子
        scene.world().setKineticSpeed(drive, 32);
        scene.effects().rotationDirectionIndicator(shaft);
        scene.idle(20);

        scene.overlay().showText(60)
                .text("A shaft here supplies the collector with rotational force")
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.MEDIUM)
                .pointAt(util.vector().blockSurface(shaft, Direction.NORTH));
        scene.idle(60);

        // 手持扳手右键的提示（InputElementBuilder 链式）
        scene.overlay().showControls(util.vector().topOf(collector), Pointing.DOWN, 30)
                .rightClick()
                .withItem(new ItemStack(com.simibubi.create.AllItems.WRENCH.get()));
        scene.idle(30);

        scene.overlay().showText(50)
                .sharedText("needs_rotation")
                .colored(PonderPalette.INPUT)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(collector));
        scene.idle(50);

        scene.world().modifyKineticSpeed(drive, f -> f * 2);
        scene.idle(20);
        scene.effects().indicateRedstone(collector);
        scene.markAsFinished();
    }
}
