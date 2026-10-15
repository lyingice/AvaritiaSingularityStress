package committee.nova.avaritia_singularity_stress.init.registry;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_smithing_crafter.ExtremeSmithingCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector.KineticNeutronCollectorBlock;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor.KineticNeutronCompressorBlock;
import committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller.SingularityRotationSpeedControllerBlock;
import committee.nova.avaritia_singularity_stress.content.singularity_stabilizer.SingularityStabilizerBlock;
// [B11 雪藏] import committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter.WirelessFluidTransmitterBlock;
import committee.nova.avaritia_singularity_stress.content.wireless.item_funnel.WirelessItemFunnelBlock;
import committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge.WirelessStressBridgeBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AvaritiaSingularityStressMod.MODID);

    public static final Supplier<Block> EXTREME_MECHANICAL_CRAFTER = BLOCKS.register(
            "extreme_mechanical_crafter",
            () -> new ExtremeMechanicalCrafterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                            .strength(5.0F, 6.0F)
                            .noOcclusion()
            )
    );

    /** B7 终焉锻造器:行为同终焉合成器,额外启用终焉锻造配方。 */
    public static final Supplier<Block> EXTREME_SMITHING_CRAFTER = BLOCKS.register(
            "extreme_smithing_crafter",
            () -> new ExtremeSmithingCrafterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                            .strength(5.0F, 6.0F)
                            .noOcclusion()
            )
    );
    /** B6 奇点压缩器:在转速控制器基础上把输出端动力标记为奇点应力 SSU(修订版:量化压缩占用)。 */
    public static final Supplier<Block> SINGULARITY_ROTATION_SPEED_CONTROLLER = BLOCKS.register(
            "singularity_rotation_speed_controller",
            () -> new SingularityRotationSpeedControllerBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
            )
    );

    /**
     * B14 奇点稳定器:轴式动能方块,接入应力网络任意处即抑制全网 B6 的品控损耗。
     */
    public static final Supplier<Block> SINGULARITY_STABILIZER = BLOCKS.register(
            "singularity_stabilizer",
            () -> new SingularityStabilizerBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                            .strength(4.0F, 8.0F)
                            .noOcclusion()
            )
    );

    // ==================================================================
    // B2 动能中子态素收集器(基础 / 致密 / 精英 / 极限)
    // ==================================================================

    public static final Supplier<Block> KINETIC_NEUTRON_COLLECTOR = BLOCKS.register(
            "kinetic_neutron_collector",
            () -> new KineticNeutronCollectorBlock(machineProperties())
    );
    public static final Supplier<Block> KINETIC_NEUTRON_COLLECTOR_DENSE = BLOCKS.register(
            "dense_kinetic_neutron_collector",
            () -> new KineticNeutronCollectorBlock(machineProperties())
    );
    public static final Supplier<Block> KINETIC_NEUTRON_COLLECTOR_ELITE = BLOCKS.register(
            "elite_kinetic_neutron_collector",
            () -> new KineticNeutronCollectorBlock(machineProperties())
    );
    public static final Supplier<Block> KINETIC_NEUTRON_COLLECTOR_ULTIMATE = BLOCKS.register(
            "ultimate_kinetic_neutron_collector",
            () -> new KineticNeutronCollectorBlock(machineProperties())
    );

    // ==================================================================
    // B4 动能中子态素压缩机(基础 / 致密 / 精英 / 极限)
    // ==================================================================

    public static final Supplier<Block> KINETIC_NEUTRON_COMPRESSOR = BLOCKS.register(
            "kinetic_neutron_compressor",
            () -> new KineticNeutronCompressorBlock(machineProperties())
    );
    public static final Supplier<Block> KINETIC_NEUTRON_COMPRESSOR_DENSE = BLOCKS.register(
            "dense_kinetic_neutron_compressor",
            () -> new KineticNeutronCompressorBlock(machineProperties())
    );
    public static final Supplier<Block> KINETIC_NEUTRON_COMPRESSOR_ELITE = BLOCKS.register(
            "elite_kinetic_neutron_compressor",
            () -> new KineticNeutronCompressorBlock(machineProperties())
    );
    public static final Supplier<Block> KINETIC_NEUTRON_COMPRESSOR_ULTIMATE = BLOCKS.register(
            "ultimate_kinetic_neutron_compressor",
            () -> new KineticNeutronCompressorBlock(machineProperties())
    );


    /** 动能机器通用方块属性:金属质感、透明模型(动画前脸需要)。 */
    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .strength(4.0F, 8.0F)
                .noOcclusion();
    }

    // ==================================================================
    // 无线系列 B9 / B10 / B11
    // ==================================================================

    /** B9 无线物品传输漏斗:一个注册名,抽取端/接收端由 EXTRACTING 属性区分。 */
    public static final Supplier<Block> WIRELESS_ITEM_FUNNEL = BLOCKS.register(
            "wireless_item_funnel",
            () -> new WirelessItemFunnelBlock(machineProperties())
    );

    /** B10 无线应力传输器·输出端(Sender):output=true,扣源网容量。 */
    public static final Supplier<Block> WIRELESS_STRESS_BRIDGE_SENDER = BLOCKS.register(
            "wireless_stress_bridge_sender",
            () -> new WirelessStressBridgeBlock(wirelessSolidProperties(), true)
    );

    /** B10 无线应力传输器·接收端(Receiver):output=false,给自身网络加容量。 */
    public static final Supplier<Block> WIRELESS_STRESS_BRIDGE_RECEIVER = BLOCKS.register(
            "wireless_stress_bridge_receiver",
            () -> new WirelessStressBridgeBlock(wirelessSolidProperties(), false)
    );

    // [B11 雪藏] B11 无线流体传输器：整体雪藏，注册取消（恢复见 fluid_transmitter 包头部说明）。
    // /** B11 无线流体传输器·输出端(Sender):output=true。 */
    // public static final Supplier<Block> WIRELESS_FLUID_TRANSMITTER_SENDER = BLOCKS.register(
    //         "wireless_fluid_transmitter_sender",
    //         () -> new WirelessFluidTransmitterBlock(wirelessSolidProperties(), true)
    // );
    //
    // /** B11 无线流体传输器·接收端(Receiver):output=false。 */
    // public static final Supplier<Block> WIRELESS_FLUID_TRANSMITTER_RECEIVER = BLOCKS.register(
    //         "wireless_fluid_transmitter_receiver",
    //         () -> new WirelessFluidTransmitterBlock(wirelessSolidProperties(), false)
    // );

    /**
     * 无线系列通用方块属性:金属质感、满方块。
     * <p>
     * 与 {@link #machineProperties()} 的区别是<b>不加 noOcclusion</b>:
     * B10/B11 的模型是完整 16³ 立方体(六个面都带 cullface),按满方块注册才能正常遮挡邻面。
     * B9 是漏斗形状,必须 noOcclusion,所以仍用 {@link #machineProperties()}。
     */
    private static BlockBehaviour.Properties wirelessSolidProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .strength(4.0F, 8.0F);
    }

    public static void init() {
        // 仅用于触发类加载
    }
}
