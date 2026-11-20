package committee.nova.avaritia_singularity_stress;

import committee.nova.avaritia_singularity_stress.content.extreme_drill.ExtremeDrillConfig;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetics.link.BlazeCubeEncasedShaftBlock;
import committee.nova.avaritia_singularity_stress.content.kinetics.link.BlazeCubeEncasedCogwheelBlock;
import committee.nova.avaritia_singularity_stress.content.kinetics.link.CrystalMatrixEncasedCogwheelBlock;
import committee.nova.avaritia_singularity_stress.content.kinetics.link.CrystalMatrixEncasedShaftBlock;
import committee.nova.avaritia_singularity_stress.content.singularity.SingularityConfig;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessLifecycle;
import committee.nova.avaritia_singularity_stress.content.wireless.debug.WirelessDebugCommand;
import committee.nova.avaritia_singularity_stress.init.registry.*;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;

import net.minecraft.server.TickTask;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.PriorityQueue;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;

@Mod("avaritia_singularity_stress")
public class AvaritiaSingularityStressMod {
	public static final Logger LOGGER = LogManager.getLogger(AvaritiaSingularityStressMod.class);
	public static final String MODID = "avaritia_singularity_stress";

    public AvaritiaSingularityStressMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, SingularityConfig.SPEC);
        // 独立文件名：同 modid 第二个 SERVER config 不能共用 -server.toml（ConfigTracker 冲突）
        modContainer.registerConfig(ModConfig.Type.SERVER, ExtremeDrillConfig.SPEC,
                "avaritia_singularity_stress-drill-server.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, WirelessConfig.SPEC);
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModExtremeRecipeTypes.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModTooltips.register();
        ModStress.register();
        modEventBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::registerNetworking);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(new WirelessLifecycle());
        ModPackets.init();
    }

    /**
     * B18 封装映射注册。
     * <p>
     * Create 的封装是"方块替换"：<b>可封装方块</b>（实现 {@code EncasableBlock}，如传动杆）收到右键时，
     * {@code EncasableBlock.tryEncase} 去 {@code EncasingRegistry.getVariants(本方块)} 里找
     * "外壳物品 == 手持物品"的 <b>encased 方块</b>（{@code EncasableBlock.java:27-39}），
     * 找到就调它的 {@code handleEncasing} 原地替换（{@code EncasedShaftBlock.java:82-86}）。
     * 所以新增一种封装只需要：一个 {@code EncasedShaftBlock} 子类 + 这里配对。
     * <p>
     * 时机必须晚于方块注册完成（{@code EncasingRegistry.addVariant} 的 javadoc 明确要求），
     * 所以放 {@code FMLCommonSetupEvent} 的 enqueueWork 里。
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CrystalMatrixEncasedShaftBlock encased =
                    (CrystalMatrixEncasedShaftBlock) ModBlocks.CRYSTAL_MATRIX_ENCASED_SHAFT.get();
            // Create 原版传动杆（B17 中子传动杆已删除，不再单独登记）
            EncasingRegistry.addVariant(AllBlocks.SHAFT.get(), encased);

            // 齿轮：小齿轮 / 大齿轮各配一次
            EncasingRegistry.addVariant(AllBlocks.COGWHEEL.get(),
                    (CrystalMatrixEncasedCogwheelBlock) ModBlocks.CRYSTAL_MATRIX_ENCASED_COGWHEEL.get());
            EncasingRegistry.addVariant(AllBlocks.LARGE_COGWHEEL.get(),
                    (CrystalMatrixEncasedCogwheelBlock) ModBlocks.CRYSTAL_MATRIX_ENCASED_LARGE_COGWHEEL.get());

            // B19 炽骨立方机壳：外壳 = 本模组的 blaze_cube_casing
            EncasingRegistry.addVariant(AllBlocks.SHAFT.get(),
                    (BlazeCubeEncasedShaftBlock) ModBlocks.BLAZE_CUBE_ENCASED_SHAFT.get());
            EncasingRegistry.addVariant(AllBlocks.COGWHEEL.get(),
                    (BlazeCubeEncasedCogwheelBlock) ModBlocks.BLAZE_CUBE_ENCASED_COGWHEEL.get());
            EncasingRegistry.addVariant(AllBlocks.LARGE_COGWHEEL.get(),
                    (BlazeCubeEncasedCogwheelBlock) ModBlocks.BLAZE_CUBE_ENCASED_LARGE_COGWHEEL.get());

            // [TEMP 定位日志] 打印传动杆登记到的全部封壳候选 + 各自的外壳方块，
            // 用来判断「手持机壳右键传动杆」为什么匹配不上。定位完删（搜 "[encasing-debug]"）
            // 注意 getVariants 返回的是 List<Block>（EncasingRegistry.java:25），不是 BlockState
            for (net.minecraft.world.level.block.Block variantBlock : EncasingRegistry.getVariants(AllBlocks.SHAFT.get())) {
                String casingName = variantBlock
                        instanceof com.simibubi.create.content.decoration.encasing.EncasedBlock eb
                                ? String.valueOf(eb.getCasing())
                                : "NOT_ENCASED";
                LOGGER.info("[encasing-debug] shaft variant={} casing={}", variantBlock, casingName);
            }
            LOGGER.info("[encasing-debug] ourCasingBlock={} ourCasingItem={}",
                    ModBlocks.BLAZE_CUBE_CASING.get(), ModItems.BLAZE_CUBE_CASING.get());
        });
    }
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.EXTREME_MECHANICAL_CRAFTER.get(),
                (blockEntity, side) -> ((ExtremeMechanicalCrafterBlockEntity) blockEntity).exposeItemHandler()
        );
        // 终焉锻造器继承同一套方块实体逻辑,同样暴露整组的组合库存
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.EXTREME_SMITHING_CRAFTER.get(),
                (blockEntity, side) -> ((ExtremeMechanicalCrafterBlockEntity) blockEntity).exposeItemHandler()
        );
        // B26 炽骨立方合成器:同样暴露整组的组合库存
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.BLAZE_CUBE_CRAFTER.get(),
                (blockEntity, side) -> ((ExtremeMechanicalCrafterBlockEntity) blockEntity).exposeItemHandler()
        );
        // 动能机器:暴露各自的产出/输入槽,便于漏斗等自动化交互
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.KINETIC_NEUTRON_COLLECTOR.get(),
                (blockEntity, side) ->
                        ((committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector.KineticNeutronCollectorBlockEntity) blockEntity).getInventory()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.KINETIC_NEUTRON_COMPRESSOR.get(),
                (blockEntity, side) ->
                        ((committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor.KineticNeutronCompressorBlockEntity) blockEntity).getInventory()
        );
    }
	private static boolean networkingRegistered = false;
	private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

	private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
	}

	public static <T extends CustomPacketPayload> void addNetworkMessage(CustomPacketPayload.Type<T> id, StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
		if (networkingRegistered)
			throw new IllegalStateException("Cannot register new network messages after networking has been registered");
		MESSAGES.put(id, new NetworkMessage<>(reader, handler));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void registerNetworking(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar(MODID);
		MESSAGES.forEach((id, networkMessage) -> registrar.playBidirectional(id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler()));
		networkingRegistered = true;
	}

	private static final Queue<IntObjectPair<Runnable>> workToBeScheduled = new ConcurrentLinkedQueue<>();
	private static final PriorityQueue<TickTask> workQueue = new PriorityQueue<>(Comparator.comparingInt(TickTask::getTick));

	public static void queueServerWork(int delay, Runnable action) {
		if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER)
			workToBeScheduled.add(new IntObjectImmutablePair<>(delay, action));
	}

	/**
	 * 注册调试指令 {@code /wireless_debug [半径]}：把附近 B10/B11 四类方块的参数打进系统日志。
	 * <p>
	 * 走 {@code NeoForge.EVENT_BUS}（构造函数里已 {@code register(this)}），与下面的 tick() 同一总线。
	 */
	@SubscribeEvent
	public void registerCommands(RegisterCommandsEvent event) {
		WirelessDebugCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public void tick(ServerTickEvent.Post event) {
		int currentTick = event.getServer().getTickCount();
		IntObjectPair<Runnable> work;
		while ((work = workToBeScheduled.poll()) != null) {
			workQueue.add(new TickTask(currentTick + work.leftInt(), work.right()));
		}
		while (!workQueue.isEmpty() && currentTick >= workQueue.peek().getTick()) {
			workQueue.poll().run();
		}
	}
}
