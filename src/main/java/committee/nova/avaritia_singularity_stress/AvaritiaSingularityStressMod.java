package committee.nova.avaritia_singularity_stress;

import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity.SingularityConfig;
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
        NeoForge.EVENT_BUS.register(new WirelessLifecycle());
        ModPackets.init();
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