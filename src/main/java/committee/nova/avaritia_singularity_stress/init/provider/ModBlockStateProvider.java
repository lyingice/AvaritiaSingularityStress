package committee.nova.avaritia_singularity_stress.init.provider;

import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;

/**
 * 终焉合成器家族的方块状态与模型生成。
 * <p>
 * 两种方块(动力合成器 / 锻造器)结构完全相同,只有 #3/#4 两张纹理不同,
 * 因此用同一套生成逻辑、传入不同方块名即可。
 */
public class ModBlockStateProvider extends BlockStateProvider {

    private static final String INTEGRATION_NAMESPACE = "avaritia_integration";
    private static final String CREATE_NAMESPACE = "create";
    private static final String CUTOUT = "minecraft:cutout";
    private static final String CUTOUT_MIPPED = "minecraft:cutout_mipped";

    public ModBlockStateProvider(
            PackOutput output,
            ExistingFileHelper existingFileHelper
    ) {
        super(output, AvaritiaSingularityStressMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // 两种方块共用的部件:只为机械目录生成一次。
        // 锻造器的 VisualSet 直接引用这两个模型常量,
        // 因为它们的纹理(#5 外壳 / neutron_cogwheel)两种方块完全相同。
        generateSharedPartials();

        generateCrafter("extreme_mechanical_crafter",
                ModBlocks.EXTREME_MECHANICAL_CRAFTER.get());
        generateCrafter("extreme_smithing_crafter",
                ModBlocks.EXTREME_SMITHING_CRAFTER.get());
        generateBlazeCubeCrafter();
        generateExtremeDrill();
        generateExtremeWaterWheel();
        generateSingularitySpeedController();
        //generateSingularityStabilizer();已弃用
        generateKineticNeutronMachines();
    }

    /**
     * B2/B4 动能机器(收集器/压缩机,各四档)的模型与方块状态。
     * <p>
     * 沿用无尽贪婪机器贴图的六个面:南面为动画前脸,东西为左右侧面,顶面独立,
     * 北面与底面用 frame。四档的贴图前缀依次为 基础/致密/精英/极限。
     */
    private void generateKineticNeutronMachines() {
        // 收集器:基础→collector,致密→dense_collector,精英→double_collector,极限→triple_collector
        generateMachine(ModBlocks.KINETIC_NEUTRON_COLLECTOR.get(), "collector", "collector", false);
        generateMachine(ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get(), "collector", "dense_collector", false);
        generateMachine(ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get(), "collector", "double_collector", false);
        generateMachine(ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get(), "collector", "triple_collector", false);

        // 压缩机:基础→compressor,致密→dense_compressor,精英→double_compressor,极限→triple_compressor
        generateMachine(ModBlocks.KINETIC_NEUTRON_COMPRESSOR.get(), "compressor", "compressor", true);
        generateMachine(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get(), "compressor", "dense_compressor", true);
        generateMachine(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get(), "compressor", "double_compressor", true);
        generateMachine(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get(), "compressor", "triple_compressor", true);
    }

    /**
     * 生成一台动能机器(cube 结构)的模型与 blockstate。
     *
     * @param block       目标方块
     * @param dir         贴图目录(machine 下的 collector / compressor)
     * @param prefix      该档位的贴图前缀(基础档即目录名本身)
     * @param compressor  压缩机专用:高阶档顶面改用 compressor_top_2
     */
    private void generateMachine(net.minecraft.world.level.block.Block block, String dir, String prefix,
                                 boolean compressor) {
        String base = "block/machine/" + dir + "/";
        boolean baseTier = prefix.equals(dir);

        String topFile = baseTier
                ? (compressor ? "compressor_top" : prefix + "_top")
                : (compressor ? "compressor_top_2" : prefix + "_top");

        ModelFile model = models()
                .withExistingParent(base + blockName(block), mcLoc("block/cube"))
                .texture("particle", modLoc(base + prefix + "_front"))
                .texture("up", modLoc(base + topFile))
                .texture("down", modLoc(base + "frame"))
                .texture("north", modLoc(base + prefix + "_front"))
                .texture("south", modLoc(base + "frame_back"))
                .texture("east", modLoc(base + prefix + "_side_right"))
                .texture("west", modLoc(base + prefix + "_side_left"))
                .renderType(ResourceLocation.fromNamespaceAndPath("minecraft", "cutout"));
        getVariantBuilder(block)
                .forAllStates(state -> new ConfiguredModel[]{
                        new ConfiguredModel(model, 0, yRotation(state), false)
                });
    }

    private static String blockName(net.minecraft.world.level.block.Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    /**
     * 生成两种终焉方块共用、内容完全一致的部件模型。
     */
    private void generateSharedPartials() {
        // 皮带框:使用 #5 外壳纹理
        models()
                .withExistingParent(
                        "block/extreme_mechanical_crafter/belt",
                        ResourceLocation.fromNamespaceAndPath(
                                CREATE_NAMESPACE, "block/mechanical_crafter/belt")
                )
                .texture("5", integrationLoc("block/create/crystal_matrix_casing"))
                .renderType(ResourceLocation.fromNamespaceAndPath("minecraft", "cutout"));

        // 无轴齿轮:使用本模组的中子齿轮纹理
        models()
                .withExistingParent(
                        "block/extreme_mechanical_crafter/cogwheel",
                        ResourceLocation.fromNamespaceAndPath(CREATE_NAMESPACE, "block/cogwheel_shaftless")
                )
                .texture("1_2", modLoc("block/neutron_cogwheel"))
                .texture("particle", modLoc("block/neutron_cogwheel"));
    }

    /**
     * 生成一个终焉合成器方块的完整资源:主体模型、动态部件模型、blockstate。
     *
     * @param name  方块注册名,同时决定模型目录与 #3/#4 的纹理目录
     * @param block 对应方块,用于生成 16 个 facing×pointing 变体
     */
    private void generateCrafter(String name, Block block) {
        ModelFile body = models()
                .withExistingParent(
                        "block/" + name + "/block",
                        ResourceLocation.fromNamespaceAndPath(
                                CREATE_NAMESPACE, "block/mechanical_crafter/block")
                )
                .texture("3", modLoc("block/" + name + "/crafter_thingies"))
                .texture("4", modLoc("block/" + name + "/crafter_side"))
                .texture("5", integrationLoc("block/create/crystal_matrix_casing"))
                .texture("7", modLoc("block/crystal_block"))
                .texture("8", integrationLoc("block/create/matrix_mixer_base_inside"))
                .texture("particle", integrationLoc("block/create/crystal_matrix_casing"))
                // 与原版机械合成器的静态层一致(create AllBlocks.MECHANICAL_CRAFTER 用 cutoutMipped)
                .renderType(ResourceLocation.fromNamespaceAndPath("minecraft", "cutout_mipped"));

        // 使用各自 crafter_thingies 的动态部件
        generateThingiesPartial("block/" + name + "/lid", "block/mechanical_crafter/lid", name);
        generateThingiesPartial("block/" + name + "/arrow", "block/mechanical_crafter/arrow", name);
        generateThingiesPartial("block/" + name + "/belt_animated",
                "block/mechanical_crafter/belt_animated", name);

        generateBlockState(block, body);
    }

    /**
     * B26 炽骨立方合成器:主体 + 动态部件 + 皮带框 + blockstate。
     * <p>
     * 贴图引用用户自制目录 {@code blaze_cube_mechanical_crafter}(模型输出仍在 blaze_cube_crafter/):
     * #3=crafter_thingies #4=crafter_side #5=blaze_cube_casing #7=blaze_cube_gearbox #8=crafter_inside。
     * 齿轮 partial 是手写 Blockbench 模型(blaze_cube_crafter/cogwheel.json),仍用 blaze_cube_crafter/crafter_cogwheel,
     * 因用户目录未提供齿轮贴图。
     */
    private void generateBlazeCubeCrafter() {
        String name = "blaze_cube_crafter";
        String tex = "blaze_cube_mechanical_crafter";
        Block block = ModBlocks.BLAZE_CUBE_CRAFTER.get();
        ResourceLocation casing = modLoc("block/" + tex + "/blaze_cube_casing");

        ModelFile body = models()
                .withExistingParent(
                        "block/" + name + "/block",
                        ResourceLocation.fromNamespaceAndPath(
                                CREATE_NAMESPACE, "block/mechanical_crafter/block")
                )
                .texture("3", modLoc("block/" + tex + "/crafter_thingies"))
                .texture("4", modLoc("block/" + tex + "/crafter_side"))
                .texture("5", casing)
                .texture("7", modLoc("block/" + tex + "/blaze_cube_gearbox"))
                .texture("8", modLoc("block/" + tex + "/crafter_inside"))
                .texture("particle", casing)
                .renderType(ResourceLocation.fromNamespaceAndPath("minecraft", "cutout_mipped"));

        generateThingiesPartial("block/" + name + "/lid", "block/mechanical_crafter/lid", tex);
        generateThingiesPartial("block/" + name + "/arrow", "block/mechanical_crafter/arrow", tex);
        generateThingiesPartial("block/" + name + "/belt_animated",
                "block/mechanical_crafter/belt_animated", tex);

        // 皮带框:键5用炽骨专属外壳
        models()
                .withExistingParent(
                        "block/" + name + "/belt",
                        ResourceLocation.fromNamespaceAndPath(
                                CREATE_NAMESPACE, "block/mechanical_crafter/belt")
                )
                .texture("5", casing)
                .renderType(ResourceLocation.fromNamespaceAndPath("minecraft", "cutout"));

        generateBlockState(block, body);
    }

    /**
     * B27 终焉钻头 blockstate:主体模型是手写的模组本地模型(复制 Create 几何 + 本地贴图),
     * 这里只生成 12 个 facing×waterlogged 变体,朝向旋转与 Create 钻头一致。
     */
    private void generateExtremeDrill() {
        ModelFile model = new ModelFile.UncheckedModelFile(modLoc("block/extreme_drill/block"));
        getVariantBuilder(ModBlocks.EXTREME_DRILL.get())
                .forAllStates(state -> directionalConfigured(model,
                        state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)));
    }

    /** B28 终焉水车 blockstate:6 个 facing 变体,外壳模型为模组本地手写模型。 */
    private void generateExtremeWaterWheel() {
        ModelFile model = new ModelFile.UncheckedModelFile(modLoc("block/extreme_water_wheel/block"));
        getVariantBuilder(ModBlocks.EXTREME_WATER_WHEEL.get())
                .forAllStates(state -> directionalConfigured(model,
                        state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)));
    }

    /** Create 六向动能方块朝向旋转(与 create:blockstates/mechanical_drill.json、water_wheel.json 一致)。 */
    private static ConfiguredModel[] directionalConfigured(ModelFile model, Direction facing) {
        int x = switch (facing) {
            case DOWN -> 180;
            case UP -> 0;
            default -> 90;
        };
        int y = switch (facing) {
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> 0;
        };
        return new ConfiguredModel[]{new ConfiguredModel(model, x, y, false)};
    }

    /**
     * 生成一个使用 crafter_thingies(#3) 的动态部件模型。
     */
    private void generateThingiesPartial(String modelPath, String createParentPath, String textureDir) {
        models()
                .withExistingParent(
                        modelPath,
                        ResourceLocation.fromNamespaceAndPath(CREATE_NAMESPACE, createParentPath)
                )
                .texture("3", modLoc("block/" + textureDir + "/crafter_thingies"))
                .renderType(ResourceLocation.fromNamespaceAndPath("minecraft", "cutout"));
    }

    private void generateBlockState(Block block, ModelFile model) {
        getVariantBuilder(block)
                .forAllStates(state -> new ConfiguredModel[]{
                        new ConfiguredModel(
                                model,
                                0,
                                yRotation(state),
                                false
                        )
                });
    }
    /**
     * B6 奇点转速控制器:复用 Create 转速控制器的模型几何,
     * 覆盖父模型的全部纹理槽位,全部指向本模组资源
     * (textures/block/singularity_rotation_speed_controller/,可自行重新贴图)。
     * <p>
     * 槽位含义(与 create:block/rotation_speed_controller/block 一致):
     * 0=主体面板 4=齿轮箱侧板 5=顶盖 6=底座 7=漏斗口 particle=底座
     */
    private void generateSingularitySpeedController() {
        String base = "block/singularity_rotation_speed_controller/";
        ModelFile model = models()
                .withExistingParent(
                        "block/singularity_rotation_speed_controller/block",
                        ResourceLocation.fromNamespaceAndPath(
                                CREATE_NAMESPACE, "block/rotation_speed_controller/block")
                )
                .texture("0", modLoc(base + "controller_body"))
                .texture("4", modLoc(base + "gearbox"))
                .texture("5", modLoc(base + "controller_top"))
                .texture("6", modLoc(base + "base"))
                .texture("7", modLoc(base + "funnel"))
                .texture("particle", modLoc(base + "base"));

        getVariantBuilder(ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get())
                .forAllStates(state -> {
                    Direction.Axis axis = state.getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS);
                    return new ConfiguredModel[]{
                            new ConfiguredModel(model, 0, axis == Direction.Axis.X ? 90 : 0, false)
                    };
                });
    }

    /**
     * B14 奇点稳定器:沿用 Create 轴的模型几何与三向旋转
     * (x=90/90, y=0, z=90/180,与 create:blockstates/shaft.json 完全一致)。
     */
    private void generateSingularityStabilizer() {
        // 方块模型为手写的整体旋转模型(src/main/resources/.../models/block/singularity_stabilizer.json),
        // 不再由 datagen 生成,这里只引用(UncheckedModelFile 不做存在性校验)。
        ModelFile model = new ModelFile.UncheckedModelFile(modLoc("block/singularity_stabilizer"));

        getVariantBuilder(ModBlocks.SINGULARITY_STABILIZER.get())
                .forAllStates(state -> {
                    Direction.Axis axis = state.getValue(RotatedPillarKineticBlock.AXIS);
                    return switch (axis) {
                        case X -> new ConfiguredModel[]{new ConfiguredModel(model, 90, 90, false)};
                        case Z -> new ConfiguredModel[]{new ConfiguredModel(model, 90, 180, false)};
                        default -> new ConfiguredModel[]{new ConfiguredModel(model)};
                    };
                });
    }

    private static int yRotation(BlockState state) {
        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    private ResourceLocation integrationLoc(String path) {
        return ResourceLocation.fromNamespaceAndPath(INTEGRATION_NAMESPACE, path);
    }
}
