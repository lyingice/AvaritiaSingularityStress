package committee.nova.avaritia_singularity_stress.init.provider;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * 终焉合成器家族的物品模型生成。
 * <p>
 * 两种方块结构相同,只有 #3/#4 纹理不同,因此共用一套生成逻辑。
 */
public class ModModelProvider extends ItemModelProvider {
    public ModModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AvaritiaSingularityStressMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        generateCrafterItem("extreme_mechanical_crafter");
        generateCrafterItem("extreme_smithing_crafter");
        withExistingParent("neutron_extendo_grip", modLoc("item/neutron_extendo_grip/item"));
        // B6 奇点转速控制器:覆盖 Create 物品展示模型的全部纹理槽位,
        // 与方块模型共用 textures/block/singularity_rotation_speed_controller/ 下的贴图。
        // 槽位:1_0=主体面板 1_5=顶盖 0=轴 3=轴端 4=齿轮箱侧板 7=漏斗口 9=底座 particle=齿轮箱侧板
        withExistingParent(
                "singularity_rotation_speed_controller",
                ResourceLocation.fromNamespaceAndPath("create", "block/rotation_speed_controller/item"))
                .texture("1_0", modLoc("block/singularity_rotation_speed_controller/controller_body"))
                .texture("1_5", modLoc("block/singularity_rotation_speed_controller/controller_top"))
                .texture("0", modLoc("block/singularity_rotation_speed_controller/shaft"))
                .texture("3", modLoc("block/singularity_rotation_speed_controller/shaft_top"))
                .texture("4", modLoc("block/singularity_rotation_speed_controller/gearbox"))
                .texture("7", modLoc("block/singularity_rotation_speed_controller/funnel"))
                .texture("9", modLoc("block/singularity_rotation_speed_controller/base"))
                .texture("particle", modLoc("block/singularity_rotation_speed_controller/gearbox"));

        // B14 奇点稳定器:物品模型引用 rotor 旋转几何模型。
        withExistingParent("singularity_stabilizer", modLoc("block/singularity_stabilizer/rotor"));
        // B2 收集器四档 / B4 压缩机四档:物品模型直接引用各自的方块模型
        machineItem("kinetic_neutron_collector", "collector");
        machineItem("dense_kinetic_neutron_collector", "collector");
        machineItem("elite_kinetic_neutron_collector", "collector");
        machineItem("ultimate_kinetic_neutron_collector", "collector");
        machineItem("kinetic_neutron_compressor", "compressor");
        machineItem("dense_kinetic_neutron_compressor", "compressor");
        machineItem("elite_kinetic_neutron_compressor", "compressor");
        machineItem("ultimate_kinetic_neutron_compressor", "compressor");
        singleTexture(
                "wireless_frequency_card",
                ResourceLocation.withDefaultNamespace("item/generated"),
                "layer0",
                modLoc("item/wireless_frequency_card")
        );
        // B9 无线物品传输漏斗:物品模型继承 Create 的"物品形态"漏斗 create:block/funnel/item ——
        // 也就是 Create 自家黄铜漏斗物品模型(create:item/brass_funnel)用的同一个 parent。
        // 它自带物品几何 + 完整的 display 显示变换(GUI / 第一三人称 / 地面 / 展示框)。
        // 【曾经的写法是 parent = 本模组的方块模型】,而那个模型挂在 create:block/funnel/block_horizontal 上:
        // 那是"摆在世界里、横放、direction 面还带 -22.5° 倾斜"的形态,而且整条链上没有任何 display 段,
        // 于是背包与手持里既没有正确的角度也没有正确的缩放 —— 看着就是不对的。
        // 贴图按 Create 的同名槽位一一对应(尺寸也已核对:base/direction/back=32²,block/redstone=16²)。
        withExistingParent("wireless_item_funnel",
                ResourceLocation.fromNamespaceAndPath("create", "block/funnel/item"))
                .texture("back", modLoc("block/machine/wireless/funnel_back"))
                .texture("base", modLoc("block/machine/wireless/funnel_base"))
                .texture("block", modLoc("block/machine/wireless/funnel_inner"))
                .texture("direction", modLoc("block/machine/wireless/funnel_direction"))
                .texture("particle", modLoc("block/machine/wireless/funnel_inner"))
                .texture("redstone", modLoc("block/machine/wireless/funnel_redstone"));
        // B10 两个注册名共用同一份方块模型（block/wireless_stress_bridge 是完整立方体，
        // 继承 minecraft:block/block 已自带物品显示变换）
        withExistingParent("wireless_stress_bridge_sender", modLoc("block/wireless_stress_bridge"));
        withExistingParent("wireless_stress_bridge_receiver", modLoc("block/wireless_stress_bridge"));
        // [B11 雪藏] B11 无线流体传输器：整体雪藏，不再生成物品模型。
        // // B11 两个注册名共用同一份方块模型
        // withExistingParent("wireless_fluid_transmitter_sender", modLoc("block/wireless_fluid_transmitter"));
        // withExistingParent("wireless_fluid_transmitter_receiver", modLoc("block/wireless_fluid_transmitter"));
    }

    /** 机器物品模型:与方块模型同名同路径,继承其显示变换。 */
    private void machineItem(String name, String dir) {
        withExistingParent(name, modLoc("block/machine/" + dir + "/" + name));
    }

    /**
     * 生成一个终焉合成器的物品模型。
     *
     * @param name 方块/物品注册名,同时决定 #3/#4 的纹理目录
     */
    private void generateCrafterItem(String name) {
        withExistingParent(
                name,
                ResourceLocation.fromNamespaceAndPath(
                        "create",
                        "block/mechanical_crafter/item"
                )
        )
                .texture("3", modLoc("block/" + name + "/crafter_thingies"))
                .texture("4", modLoc("block/" + name + "/crafter_side"))
                .texture(
                        "5",
                        ResourceLocation.fromNamespaceAndPath(
                                "avaritia_integration",
                                "block/create/crystal_matrix_casing"
                        )
                )
                .texture("7", modLoc("block/crystal_block"))
                .texture(
                        "8",
                        ResourceLocation.fromNamespaceAndPath(
                                "avaritia_integration",
                                "block/create/matrix_mixer_base_inside"
                        )
                )
                .texture("9", modLoc("block/neutron_cogwheel"))
                .texture(
                        "particle",
                        ResourceLocation.fromNamespaceAndPath(
                                "avaritia_integration",
                                "block/create/crystal_matrix_casing"
                        )
                );
    }
}
