package committee.nova.avaritia_singularity_stress.init.registry;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.blaze_cube_crafter.BlazeCubeCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_connector.ExtremeConnectorBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_drill.ExtremeDrillBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_water_wheel.ExtremeWaterWheelBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_smithing_crafter.ExtremeSmithingCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel.WarpTunnelBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector.KineticNeutronCollectorBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor.KineticNeutronCompressorBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller.SingularityRotationSpeedControllerBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity_stabilizer.SingularityStabilizerBlockEntity;
// [B11 雪藏] import committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter.WirelessFluidTransmitterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.wireless.item_funnel.WirelessItemFunnelBlockEntity;
import committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge.WirelessStressBridgeBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AvaritiaSingularityStressMod.MODID);

    public static final Supplier<BlockEntityType<ExtremeMechanicalCrafterBlockEntity>> EXTREME_MECHANICAL_CRAFTER =
            BLOCK_ENTITIES.register(
                    "extreme_mechanical_crafter",
                    () -> BlockEntityType.Builder.of(
                            ExtremeMechanicalCrafterBlockEntity::create,
                            ModBlocks.EXTREME_MECHANICAL_CRAFTER.get()
                    ).build(null)
            );

    public static final Supplier<BlockEntityType<ExtremeSmithingCrafterBlockEntity>> EXTREME_SMITHING_CRAFTER =
            BLOCK_ENTITIES.register(
                    "extreme_smithing_crafter",
                    () -> BlockEntityType.Builder.of(
                            ExtremeSmithingCrafterBlockEntity::create,
                            ModBlocks.EXTREME_SMITHING_CRAFTER.get()
                    ).build(null)
            );

    /** B26 炽骨立方合成器:独立实体类型,保留自己的库存与动画。 */
    public static final Supplier<BlockEntityType<BlazeCubeCrafterBlockEntity>> BLAZE_CUBE_CRAFTER =
            BLOCK_ENTITIES.register(
                    "blaze_cube_crafter",
                    () -> BlockEntityType.Builder.of(
                            BlazeCubeCrafterBlockEntity::create,
                            ModBlocks.BLAZE_CUBE_CRAFTER.get()
                    ).build(null)
            );

    /** B27 终焉钻头。 */
    public static final Supplier<BlockEntityType<ExtremeDrillBlockEntity>> EXTREME_DRILL =
            BLOCK_ENTITIES.register(
                    "extreme_drill",
                    () -> BlockEntityType.Builder.of(
                            ExtremeDrillBlockEntity::create,
                            ModBlocks.EXTREME_DRILL.get()
                    ).build(null)
            );

    /** B28 终焉水车。 */
    public static final Supplier<BlockEntityType<ExtremeWaterWheelBlockEntity>> EXTREME_WATER_WHEEL =
            BLOCK_ENTITIES.register(
                    "extreme_water_wheel",
                    () -> BlockEntityType.Builder.of(
                            ExtremeWaterWheelBlockEntity::create,
                            ModBlocks.EXTREME_WATER_WHEEL.get()
                    ).build(null)
            );

    /** B30 终焉连接器（被动网络节点）。 */
    public static final Supplier<BlockEntityType<ExtremeConnectorBlockEntity>> EXTREME_CONNECTOR =
            BLOCK_ENTITIES.register(
                    "extreme_connector",
                    () -> BlockEntityType.Builder.of(
                            ExtremeConnectorBlockEntity::create,
                            ModBlocks.EXTREME_CONNECTOR.get()
                    ).build(null)
            );

    /** B29 终焉动能合成终端。 */
    public static final Supplier<BlockEntityType<ExtremeCraftingTerminalBlockEntity>> EXTREME_CRAFTING_TERMINAL =
            BLOCK_ENTITIES.register(
                    "extreme_crafting_terminal",
                    () -> BlockEntityType.Builder.of(
                            ExtremeCraftingTerminalBlockEntity::create,
                            ModBlocks.EXTREME_CRAFTING_TERMINAL.get()
                    ).build(null)
            );
    public static final Supplier<BlockEntityType<SingularityRotationSpeedControllerBlockEntity>> SINGULARITY_ROTATION_SPEED_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "singularity_rotation_speed_controller",
                    () -> BlockEntityType.Builder.of(
                            SingularityRotationSpeedControllerBlockEntity::create,
                            ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get()
                    ).build(null)
            );

    /** B14 奇点稳定器:轴式动能方块,占用 0,存在即抑制全网 B6 品控损耗。 */
    public static final Supplier<BlockEntityType<SingularityStabilizerBlockEntity>> SINGULARITY_STABILIZER =
            BLOCK_ENTITIES.register(
                    "singularity_stabilizer",
                    () -> BlockEntityType.Builder.of(
                            SingularityStabilizerBlockEntity::create,
                            ModBlocks.SINGULARITY_STABILIZER.get()
                    ).build(null)
            );

    /** B2 收集器四档共用同一个方块实体类型。 */
    public static final Supplier<BlockEntityType<KineticNeutronCollectorBlockEntity>> KINETIC_NEUTRON_COLLECTOR =
            BLOCK_ENTITIES.register(
                    "kinetic_neutron_collector",
                    () -> BlockEntityType.Builder.of(
                            KineticNeutronCollectorBlockEntity::create,
                            ModBlocks.KINETIC_NEUTRON_COLLECTOR.get(),
                            ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get(),
                            ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get(),
                            ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get()
                    ).build(null)
            );

    /** B4 压缩机四档共用同一个方块实体类型。 */
    public static final Supplier<BlockEntityType<KineticNeutronCompressorBlockEntity>> KINETIC_NEUTRON_COMPRESSOR =
            BLOCK_ENTITIES.register(
                    "kinetic_neutron_compressor",
                    () -> BlockEntityType.Builder.of(
                            KineticNeutronCompressorBlockEntity::create,
                            ModBlocks.KINETIC_NEUTRON_COMPRESSOR.get(),
                            ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get(),
                            ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get(),
                            ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get()
                    ).build(null)
            );


    // ==================================================================
    // 无线系列 B9 / B10 / B11
    // 注:B10/B11 各有两个注册名,按要求各配一个独立的 BE 类型(方案 C / 方案 A),
    //    两个类型复用同一个 BE 类,由状态的 output 属性决定具体类型。
    // ==================================================================

    /** B9 无线物品传输漏斗:单注册名。 */
    public static final Supplier<BlockEntityType<WirelessItemFunnelBlockEntity>> WIRELESS_ITEM_FUNNEL =
            BLOCK_ENTITIES.register(
                    "wireless_item_funnel",
                    () -> BlockEntityType.Builder.of(
                            WirelessItemFunnelBlockEntity::create,
                            ModBlocks.WIRELESS_ITEM_FUNNEL.get()
                    ).build(null)
            );

    /** B15 空间折跃隧道:单注册名、单端口。 */
    public static final Supplier<BlockEntityType<WarpTunnelBlockEntity>> WARP_TUNNEL =
            BLOCK_ENTITIES.register(
                    "warp_tunnel",
                    () -> BlockEntityType.Builder.of(
                            WarpTunnelBlockEntity::create,
                            ModBlocks.WARP_TUNNEL.get()
                    ).build(null)
            );

    // [B17 中子传动杆已删除] 原本这里注册 NEUTRON_SHAFT。用户改用 B18 的封装方案,
    // 删掉整个方块与它的方块实体类型;恢复时照下面 CRYSTAL_ENCASED_SHAFT 的写法加回。

    /** B18 水晶矩阵机壳包壳轴:同样复用 Create 的 BracketedKineticBlockEntity。 */
    public static final Supplier<BlockEntityType<BracketedKineticBlockEntity>> CRYSTAL_ENCASED_SHAFT =
            BLOCK_ENTITIES.register(
                    "crystal_matrix_encased_shaft",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new BracketedKineticBlockEntity(
                                    ModBlockEntities.CRYSTAL_ENCASED_SHAFT.get(), pos, state),
                            ModBlocks.CRYSTAL_MATRIX_ENCASED_SHAFT.get()
                    ).build(null)
            );


    /** B19 炽骨立方机壳包壳轴：复用 Create 的 BracketedKineticBlockEntity。 */
    public static final Supplier<BlockEntityType<BracketedKineticBlockEntity>> BLAZE_CUBE_ENCASED_SHAFT =
            BLOCK_ENTITIES.register(
                    "blaze_cube_encased_shaft",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new BracketedKineticBlockEntity(
                                    ModBlockEntities.BLAZE_CUBE_ENCASED_SHAFT.get(), pos, state),
                            ModBlocks.BLAZE_CUBE_ENCASED_SHAFT.get()
                    ).build(null)
            );
    /** B19 炽骨立方机壳包壳齿轮（小 / 大）：各用自己的类型（T07：不能沿用 Create 的类型）。 */
    public static final Supplier<BlockEntityType<SimpleKineticBlockEntity>> BLAZE_ENCASED_COGWHEEL =
            BLOCK_ENTITIES.register(
                    "blaze_cube_encased_cogwheel",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new SimpleKineticBlockEntity(
                                    ModBlockEntities.BLAZE_ENCASED_COGWHEEL.get(), pos, state),
                            ModBlocks.BLAZE_CUBE_ENCASED_COGWHEEL.get()
                    ).build(null)
            );

    public static final Supplier<BlockEntityType<SimpleKineticBlockEntity>> BLAZE_ENCASED_LARGE_COGWHEEL =
            BLOCK_ENTITIES.register(
                    "blaze_cube_encased_large_cogwheel",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new SimpleKineticBlockEntity(
                                    ModBlockEntities.BLAZE_ENCASED_LARGE_COGWHEEL.get(), pos, state),
                            ModBlocks.BLAZE_CUBE_ENCASED_LARGE_COGWHEEL.get()
                    ).build(null)
            );

    /** B18 水晶矩阵机壳包壳齿轮（小 / 大）：必须各有一个自己的方块实体类型。
     *  不能沿用 Create 的 ENCASED_COGWHEEL——那个类型只登记了 Create 自己的两个方块，
     *  NeoForge 的 BlockEntity.validateBlockState 会判定"类型不承认该方块"并直接崩服。 */
    public static final Supplier<BlockEntityType<SimpleKineticBlockEntity>> CRYSTAL_ENCASED_COGWHEEL =
            BLOCK_ENTITIES.register(
                    "crystal_matrix_encased_cogwheel",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new SimpleKineticBlockEntity(
                                    ModBlockEntities.CRYSTAL_ENCASED_COGWHEEL.get(), pos, state),
                            ModBlocks.CRYSTAL_MATRIX_ENCASED_COGWHEEL.get()
                    ).build(null)
            );

    public static final Supplier<BlockEntityType<SimpleKineticBlockEntity>> CRYSTAL_ENCASED_LARGE_COGWHEEL =
            BLOCK_ENTITIES.register(
                    "crystal_matrix_encased_large_cogwheel",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new SimpleKineticBlockEntity(
                                    ModBlockEntities.CRYSTAL_ENCASED_LARGE_COGWHEEL.get(), pos, state),
                            ModBlocks.CRYSTAL_MATRIX_ENCASED_LARGE_COGWHEEL.get()
                    ).build(null)
            );

    /** B10 无线应力传输器·输出端。 */
    public static final Supplier<BlockEntityType<WirelessStressBridgeBlockEntity>> WIRELESS_STRESS_BRIDGE_SENDER =
            BLOCK_ENTITIES.register(
                    "wireless_stress_bridge_sender",
                    () -> BlockEntityType.Builder.of(
                            WirelessStressBridgeBlockEntity::create,
                            ModBlocks.WIRELESS_STRESS_BRIDGE_SENDER.get()
                    ).build(null)
            );

    /** B10 无线应力传输器·接收端。 */
    public static final Supplier<BlockEntityType<WirelessStressBridgeBlockEntity>> WIRELESS_STRESS_BRIDGE_RECEIVER =
            BLOCK_ENTITIES.register(
                    "wireless_stress_bridge_receiver",
                    () -> BlockEntityType.Builder.of(
                            WirelessStressBridgeBlockEntity::create,
                            ModBlocks.WIRELESS_STRESS_BRIDGE_RECEIVER.get()
                    ).build(null)
            );

    // [B11 雪藏] B11 无线流体传输器方块实体：整体雪藏。
    // /** B11 无线流体传输器·输出端。 */
    // public static final Supplier<BlockEntityType<WirelessFluidTransmitterBlockEntity>> WIRELESS_FLUID_TRANSMITTER_SENDER =
    //         BLOCK_ENTITIES.register(
    //                 "wireless_fluid_transmitter_sender",
    //                 () -> BlockEntityType.Builder.of(
    //                         WirelessFluidTransmitterBlockEntity::create,
    //                         ModBlocks.WIRELESS_FLUID_TRANSMITTER_SENDER.get()
    //                 ).build(null)
    //         );
    //
    // /** B11 无线流体传输器·接收端。 */
    // public static final Supplier<BlockEntityType<WirelessFluidTransmitterBlockEntity>> WIRELESS_FLUID_TRANSMITTER_RECEIVER =
    //         BLOCK_ENTITIES.register(
    //                 "wireless_fluid_transmitter_receiver",
    //                 () -> BlockEntityType.Builder.of(
    //                         WirelessFluidTransmitterBlockEntity::create,
    //                         ModBlocks.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get()
    //                 ).build(null)
    //         );


    public static void init() {
        // 仅用于触发类加载
    }
}
