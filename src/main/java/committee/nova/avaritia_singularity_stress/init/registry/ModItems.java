package committee.nova.avaritia_singularity_stress.init.registry;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.equipment.extendoGrip.NeutronExtendoGripItem;
import committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel.WarpTunnelItem;
import committee.nova.avaritia_singularity_stress.content.wireless.frequency_card.WirelessFrequencyCardItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, AvaritiaSingularityStressMod.MODID);

    public static final Supplier<Item> EXTREME_MECHANICAL_CRAFTER = ITEMS.register(
            "extreme_mechanical_crafter",
            () -> new BlockItem(
                    ModBlocks.EXTREME_MECHANICAL_CRAFTER.get(),
                    new Item.Properties()
            )
    );

    public static final Supplier<Item> EXTREME_SMITHING_CRAFTER = ITEMS.register(
            "extreme_smithing_crafter",
            () -> new BlockItem(
                    ModBlocks.EXTREME_SMITHING_CRAFTER.get(),
                    new Item.Properties()
            )
    );

    public static final Supplier<Item> NEUTRON_EXTENDO_GRIP = ITEMS.register(
            "neutron_extendo_grip",
            () -> new NeutronExtendoGripItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.EPIC))
    );
    public static final Supplier<Item> SINGULARITY_ROTATION_SPEED_CONTROLLER = ITEMS.register(
            "singularity_rotation_speed_controller",
            () -> new BlockItem(
                    ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(),
                    new Item.Properties()
            )
    );

    /** B14 奇点稳定器 */
    public static final Supplier<Item> SINGULARITY_STABILIZER = blockItem(
            "singularity_stabilizer", ModBlocks.SINGULARITY_STABILIZER);

    // ===== B2 收集器四档 =====
    public static final Supplier<Item> KINETIC_NEUTRON_COLLECTOR = blockItem(
            "kinetic_neutron_collector", ModBlocks.KINETIC_NEUTRON_COLLECTOR);
    public static final Supplier<Item> KINETIC_NEUTRON_COLLECTOR_DENSE = blockItem(
            "dense_kinetic_neutron_collector", ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE);
    public static final Supplier<Item> KINETIC_NEUTRON_COLLECTOR_ELITE = blockItem(
            "elite_kinetic_neutron_collector", ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE);
    public static final Supplier<Item> KINETIC_NEUTRON_COLLECTOR_ULTIMATE = blockItem(
            "ultimate_kinetic_neutron_collector", ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE);

    // ===== B4 压缩机四档 =====
    public static final Supplier<Item> KINETIC_NEUTRON_COMPRESSOR = blockItem(
            "kinetic_neutron_compressor", ModBlocks.KINETIC_NEUTRON_COMPRESSOR);
    public static final Supplier<Item> KINETIC_NEUTRON_COMPRESSOR_DENSE = blockItem(
            "dense_kinetic_neutron_compressor", ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE);
    public static final Supplier<Item> KINETIC_NEUTRON_COMPRESSOR_ELITE = blockItem(
            "elite_kinetic_neutron_compressor", ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE);
    public static final Supplier<Item> KINETIC_NEUTRON_COMPRESSOR_ULTIMATE = blockItem(
            "ultimate_kinetic_neutron_compressor", ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE);
    public static final Supplier<Item> WIRELESS_FREQUENCY_CARD = ITEMS.register(
            "wireless_frequency_card",
            () -> new WirelessFrequencyCardItem(new Item.Properties().stacksTo(1))
    );

    // ===== 无线系列 B9 / B10 / B11（注册名必须与 ModBlocks 一致，否则会缺物品/模型） =====

    /** B9 无线物品传输漏斗。 */
    public static final Supplier<Item> WIRELESS_ITEM_FUNNEL = blockItem(
            "wireless_item_funnel", ModBlocks.WIRELESS_ITEM_FUNNEL);

    /** B10 无线应力传输器·输出端 / 接收端。 */
    public static final Supplier<Item> WIRELESS_STRESS_BRIDGE_SENDER = blockItem(
            "wireless_stress_bridge_sender", ModBlocks.WIRELESS_STRESS_BRIDGE_SENDER);
    public static final Supplier<Item> WIRELESS_STRESS_BRIDGE_RECEIVER = blockItem(
            "wireless_stress_bridge_receiver", ModBlocks.WIRELESS_STRESS_BRIDGE_RECEIVER);

    /** B19 炽骨立方机壳（独立机壳方块）。 */
    public static final Supplier<Item> BLAZE_CUBE_CASING = blockItem(
            "blaze_cube_casing", ModBlocks.BLAZE_CUBE_CASING);
    /** B19 炽骨立方机壳包壳轴。 */
    public static final Supplier<Item> BLAZE_CUBE_ENCASED_SHAFT = blockItem(
            "blaze_cube_encased_shaft", ModBlocks.BLAZE_CUBE_ENCASED_SHAFT);
    public static final Supplier<Item> BLAZE_CUBE_ENCASED_COGWHEEL = blockItem(
            "blaze_cube_encased_cogwheel", ModBlocks.BLAZE_CUBE_ENCASED_COGWHEEL);
    public static final Supplier<Item> BLAZE_CUBE_ENCASED_LARGE_COGWHEEL = blockItem(
            "blaze_cube_encased_large_cogwheel", ModBlocks.BLAZE_CUBE_ENCASED_LARGE_COGWHEEL);
    /** B18 水晶矩阵机壳包壳轴。 */
    public static final Supplier<Item> CRYSTAL_MATRIX_ENCASED_SHAFT = blockItem(
            "crystal_matrix_encased_shaft", ModBlocks.CRYSTAL_MATRIX_ENCASED_SHAFT);

    /** B18 水晶矩阵机壳包壳齿轮（小 / 大）。 */
    public static final Supplier<Item> CRYSTAL_MATRIX_ENCASED_COGWHEEL = blockItem(
            "crystal_matrix_encased_cogwheel", ModBlocks.CRYSTAL_MATRIX_ENCASED_COGWHEEL);
    public static final Supplier<Item> CRYSTAL_MATRIX_ENCASED_LARGE_COGWHEEL = blockItem(
            "crystal_matrix_encased_large_cogwheel", ModBlocks.CRYSTAL_MATRIX_ENCASED_LARGE_COGWHEEL);

    /** B17 中子传动杆已删除：改用 B18 的水晶矩阵机壳封装，不再单独提供该物品。 */

    /** B15 空间折跃隧道（自定义 BlockItem：放置时校验"下方必须是水平传送带"）。 */
    public static final Supplier<Item> WARP_TUNNEL = ITEMS.register(
            "warp_tunnel",
            () -> new WarpTunnelItem(ModBlocks.WARP_TUNNEL.get(), new Item.Properties())
    );

    // [B11 雪藏] B11 无线流体传输器物品：整体雪藏。
    // /** B11 无线流体传输器·输出端 / 接收端。 */
    // public static final Supplier<Item> WIRELESS_FLUID_TRANSMITTER_SENDER = blockItem(
    //         "wireless_fluid_transmitter_sender", ModBlocks.WIRELESS_FLUID_TRANSMITTER_SENDER);
    // public static final Supplier<Item> WIRELESS_FLUID_TRANSMITTER_RECEIVER = blockItem(
    //         "wireless_fluid_transmitter_receiver", ModBlocks.WIRELESS_FLUID_TRANSMITTER_RECEIVER);


    private static Supplier<Item> blockItem(String name, Supplier<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void init() {
        // 仅用于触发类加载，实际注册由 DeferredRegister 自动处理
    }
}



