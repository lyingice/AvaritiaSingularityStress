package committee.nova.avaritia_singularity_stress.init.registry;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class ModPartialModels {
    // ============ 使用完整路径（带命名空间） ============
    // 示例：引用 Create 自带的模型
    //public static final PartialModel EXAMPLE_CREATE_COGS =
    //        PartialModel.of(ResourceLocation.parse("create:block/cogwheel/block"));
    // ============ 使用辅助方法（推荐） ============
    // 自己的模型放在 assets/[modid]/block/ 目录下
    private static PartialModel block(String path) {
        return PartialModel.of(
                ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "block/" + path)
        );
    }

    private static PartialModel entity(String path) {
        return PartialModel.of(
                ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "entity/" + path)
        );
    }

    private static PartialModel item(String path) {
        return PartialModel.of(
                ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "item/" + path)
        );
    }

    // ============ I2 星空延拓之手 PartialModel ============
    public static final PartialModel NEUTRON_EXTENDO_GRIP_COG =
            item("neutron_extendo_grip/cog");
    public static final PartialModel NEUTRON_EXTENDO_GRIP_THIN_SHORT =
            item("neutron_extendo_grip/thin_short");
    public static final PartialModel NEUTRON_EXTENDO_GRIP_WIDE_SHORT =
            item("neutron_extendo_grip/wide_short");
    public static final PartialModel NEUTRON_EXTENDO_GRIP_THIN_LONG =
            item("neutron_extendo_grip/thin_long");
    public static final PartialModel NEUTRON_EXTENDO_GRIP_WIDE_LONG =
            item("neutron_extendo_grip/wide_long");
    public static final PartialModel NEUTRON_EXTENDO_GRIP_HAND_POINTING =
            item("neutron_extendo_grip/hand_pointing");
    public static final PartialModel NEUTRON_EXTENDO_GRIP_HAND_PUNCHING =
            item("neutron_extendo_grip/hand_punching");
    public static final PartialModel NEUTRON_EXTENDO_GRIP_HAND_HOLDING =
            item("neutron_extendo_grip/hand_holding");

    // ============ 终焉动能合成器 PartialModel ============
    public static final PartialModel EXTREME_MECHANICAL_CRAFTER_LID =
            block("extreme_mechanical_crafter/lid");
    public static final PartialModel EXTREME_MECHANICAL_CRAFTER_ARROW =
            block("extreme_mechanical_crafter/arrow");
    public static final PartialModel EXTREME_MECHANICAL_CRAFTER_BELT_FRAME =
            block("extreme_mechanical_crafter/belt");
    public static final PartialModel EXTREME_MECHANICAL_CRAFTER_BELT =
            block("extreme_mechanical_crafter/belt_animated");
    public static final PartialModel EXTREME_MECHANICAL_CRAFTER_COGWHEEL =
            block("extreme_mechanical_crafter/cogwheel");

    // ============ 终焉锻造器 PartialModel ============
    // 只生成使用 smithing 专属纹理的三个部件;
    // belt(#5 外壳) 与 cogwheel(neutron_cogwheel) 纹理与合成器完全相同,
    // 直接复用上面的常量,不重复生成资源。
    public static final PartialModel EXTREME_SMITHING_CRAFTER_LID =
            block("extreme_smithing_crafter/lid");
    public static final PartialModel EXTREME_SMITHING_CRAFTER_ARROW =
            block("extreme_smithing_crafter/arrow");
    public static final PartialModel EXTREME_SMITHING_CRAFTER_BELT =
            block("extreme_smithing_crafter/belt_animated");
    // ============ B14 奇点稳定器 PartialModel ============
    // 旋转几何独立成 rotor 模型;blockstate 模型是空壳,chunk 网格不画任何东西
    public static final PartialModel SINGULARITY_STABILIZER =
            block("singularity_stabilizer/rotor");

    // ============ 在这里声明你的 PartialModel ============

    // 示例：奇异应力发生器的部件
    // public static final PartialModel SINGULARITY_GENERATOR_COGS = block("singularity_generator/cogs");
    // public static final PartialModel SINGULARITY_GENERATOR_SHAFT = block("singularity_generator/shaft");
    // public static final PartialModel SINGULARITY_GENERATOR_CORE = block("singularity_generator/core");

    // 示例：奇异水轮的部件
    // public static final PartialModel SINGULARITY_WATER_WHEEL = block("singularity_water_wheel/wheel");
    // public static final PartialModel LARGE_SINGULARITY_WATER_WHEEL = block("large_singularity_water_wheel/block");
    // public static final PartialModel LARGE_SINGULARITY_WATER_WHEEL_EXTENSION = block("large_singularity_water_wheel/block_extension");

    // 示例：奇异动力背罐的部件
    // public static final PartialModel SINGULARITY_BACKTANK_COGS = block("singularity_backtank/cogs");
    // public static final PartialModel SINGULARITY_BACKTANK_SHAFT = block("singularity_backtank/shaft");

    // ============ 初始化方法 ============
    public static void init() {
        // 仅用于触发类加载，让 static 字段完成初始化
        // Flywheel 会在注册时自动处理 PartialModel
    }
}