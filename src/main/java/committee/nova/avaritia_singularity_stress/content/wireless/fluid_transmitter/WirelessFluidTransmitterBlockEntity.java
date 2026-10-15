// ============================================================================
// [已雪藏 · SHELVED] B11 方块实体 WirelessFluidTransmitterBlockEntity
// 无线流体传输器(B11)实现难度过高，整体注释保留待后续重启。
// 恢复时需要同步取消注释:
//   ModBlocks / ModItems / ModBlockEntities / ModCreativeTabs /
//   ModLootTableProvider / ModModelProvider / ModLanguageProvider(En/ZhCn) /
//   WirelessGuiKind / WirelessConfig / WirelessDebugCommand / mixins.json
// ============================================================================
// package committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter;
// 
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Locale;
// import java.util.Objects;
// import java.util.UUID;
// 
// import javax.annotation.Nullable;
// 
// import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
// import com.simibubi.create.content.fluids.FluidPropagator;
// import com.simibubi.create.content.fluids.FluidTransportBehaviour;
// import com.simibubi.create.content.fluids.PipeConnection;
// import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
// import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
// 
// import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
// import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
// import committee.nova.avaritia_singularity_stress.content.wireless.IWirelessConfigHolder;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessDevice;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessGuiKind;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessKey;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessNetwork;
// import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
// 
// import net.createmod.catnip.lang.LangBuilder;
// import net.minecraft.ChatFormatting;
// import net.minecraft.core.BlockPos;
// import net.minecraft.core.Direction;
// import net.minecraft.core.HolderLookup;
// import net.minecraft.core.registries.BuiltInRegistries;
// import net.minecraft.nbt.CompoundTag;
// import net.minecraft.network.chat.Component;
// import net.minecraft.world.level.block.entity.BlockEntityType;
// import net.minecraft.world.level.block.state.BlockState;
// 
// /**
//  * B11 无线流体传输器的方块实体（方案 A：两个注册名、一个方块类、一个 BE 类 + 两个 Mixin）。
//  * <p>
//  * 它<b>不提供任何压强</b>，也不搬运流体 —— 真正干活的是两条注入进 Create 流体传播的 Mixin
//  * （{@code FluidPropagatorMixin} / {@code PumpBlockEntityMixin}）：BFS 走到 B11 时直接跳到对端
//  * B11 的接入点，把两段真管道在算法上接成一条。所以本 BE 只负责三件事：
//  * <ol>
//  *   <li>挂 {@link WirelessFluidTransmitterBehaviour}，让 Create 的流体系统能"看见" B11
//  *       （见该类的注释）；</li>
//  *   <li>登记进 {@link WirelessNetwork}，供 {@code WirelessService.findPeer} 找对端；</li>
//  *   <li>存频率 / 可见性 / 所有者（共享底层）。</li>
//  * </ol>
//  * B11 不需要周期性无线 tick：跨桥发生在流体 BFS 内部，由"方块变化 / 泵转速变化"驱动
//  * （{@code propagateChangedPipe} 内部会 wipePressure()，绝不能每 tick 调）。
//  */
// public class WirelessFluidTransmitterBlockEntity extends SmartBlockEntity
//         implements IFrequencyBindable, WirelessDevice, IWirelessConfigHolder, IHaveGoggleInformation {
// 
//     private int frequency = NO_FREQUENCY;
// 
//     private ChannelVisibility visibility = ChannelVisibility.PRIVATE;
// 
//     @Nullable
//     private UUID owner;
// 
//     // ===================== 护目镜诊断数据（服务端每 5 tick 算，随 NBT 同步给客户端） =====================
// 
//     /** 桥接诊断结果：桥不成立时它直接告诉玩家卡在哪一条。 */
//     private WirelessFluidBridge.BridgeStatus debugStatus = WirelessFluidBridge.BridgeStatus.UNBOUND;
// 
//     /** 找到的对端坐标（没有则为 null）。 */
//     @Nullable
//     private BlockPos debugPeerPos;
// 
//     /** 两端端口不对置时，本端端口"应该"朝的方向（其余情况为 null）。 */
//     @Nullable
//     private Direction debugRequiredFacing;
// 
//     private static final String KEY_STATUS = "BridgeStatus";
//     private static final String KEY_PEER = "BridgePeer";
//     private static final String KEY_REQUIRED_FACING = "BridgeRequiredFacing";
// 
//     public WirelessFluidTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
//         super(type, pos, state);
//     }
// 
//     /**
//      * 注册用工厂。
//      * <p>
//      * 方案 A 要求两个注册名各配一个 BE 类型，而两个方块共用本类，
//      * 所以按状态的 {@code output} 选出对应类型（每个类型只注册在自己的方块上，必然一致）。
//      */
//     public static WirelessFluidTransmitterBlockEntity create(BlockPos pos, BlockState state) {
//         BlockEntityType<WirelessFluidTransmitterBlockEntity> type =
//                 state.getValue(WirelessFluidTransmitterBlock.OUTPUT)
//                         ? ModBlockEntities.WIRELESS_FLUID_TRANSMITTER_SENDER.get()
//                         : ModBlockEntities.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get();
//         return new WirelessFluidTransmitterBlockEntity(type, pos, state);
//     }
// 
//     @Override
//     public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
//         behaviours.add(new WirelessFluidTransmitterBehaviour(this));
//     }
// 
//     @Override
//     public void initialize() {
//         super.initialize();
//         WirelessNetwork.add(this);
//     }
// 
//     @Override
//     public void invalidate() {
//         WirelessNetwork.remove(this);
//         super.invalidate();
//     }
// 
//     /** @return true = 输出端（Sender），false = 接收端（Receiver）。 */
//     public boolean isOutputSide() {
//         return getBlockState().getValue(WirelessFluidTransmitterBlock.OUTPUT);
//     }
// 
//     // ===================== 界面布局提示（客户端 GUI 读，见 WirelessGuiKind） =====================
// 
//     /** B11 只有频率 + 频道可见性，没有"数量"字段。 */
//     @Override
//     public WirelessGuiKind guiKind() {
//         return WirelessGuiKind.FLUID_TRANSMITTER;
//     }
// 
//     @Override
//     public boolean guiOutputSide() {
//         return isOutputSide();
//     }
// 
//     // ===================== WirelessDevice =====================
// 
//     @Override
//     public WirelessKey getKey() {
//         return hasFrequency() ? WirelessKey.of(frequency, owner, visibility) : null;
//     }
// 
//     @Override
//     public boolean isAlive() {
//         return !isRemoved() && level != null && !level.isClientSide;
//     }
// 
//     /**
//      * 需要每 5 tick 跑一次：不是为了搬运流体（那由 Create 的 BFS 在方块变化时做），
//      * 而是为了把"桥接诊断结果"算出来同步给客户端护目镜 —— 桥接条件依赖服务端才有的
//      * 频道表与泵扫描，客户端算不出来。
//      * <p>
//      * 注意：未绑定频率的设备不会进频道表，因而不会收到 tick；那种情况下护目镜只显示"未绑定频率"。
//      */
//     @Override
//     public boolean wantsWirelessTick() {
//         return true;
//     }
// 
//     @Override
//     public void tickWireless() {
//         if (level == null || level.isClientSide) {
//             return;
//         }
//         WirelessFluidBridge.Diagnosis diagnosis = WirelessFluidBridge.diagnose(level, this);
//         if (diagnosis.status() != debugStatus || !Objects.equals(diagnosis.peerPos(), debugPeerPos)
//                 || diagnosis.requiredFacing() != debugRequiredFacing) {
//             debugStatus = diagnosis.status();
//             debugPeerPos = diagnosis.peerPos();
//             debugRequiredFacing = diagnosis.requiredFacing();
//             sendData();
//         }
//     }
// 
//     // ===================== 护目镜 =====================
// 
//     /**
//      * B11 的护目镜信息。
//      * <p>
//      * B11 不消耗也不提供应力，原版那条链（{@code KineticBlockEntity} / {@code GeneratingKineticBlockEntity}）
//      * 对它本来就没有任何输出；这里给出的是"桥到底成不成立"的排查清单。
//      */
//     @Override
//     public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
//         lang().add(getBlockState().getBlock().getName()).style(ChatFormatting.GOLD).forGoggles(tooltip);
// 
//         if (!hasFrequency()) {
//             lang().translate("wireless.goggles.unbound").style(ChatFormatting.RED).forGoggles(tooltip, 1);
//         } else {
//             lang().translate("wireless.goggles.frequency", String.valueOf(getFrequency()),
//                             lang().translate("gui.avaritia_singularity_stress.visibility."
//                                     + visibility.name().toLowerCase(Locale.ROOT)))
//                     .style(ChatFormatting.GRAY)
//                     .forGoggles(tooltip, 1);
//         }
// 
//         lang().translate("wireless.goggles.status", lang().translate(statusLangKey()))
//                 .style(statusColor())
//                 .forGoggles(tooltip, 1);
// 
//         // 管道必须贴在这一面：B11 只有一个流动面
//         lang().translate("wireless.goggles.facing",
//                         getBlockState().getValue(WirelessFluidTransmitterBlock.FACING).getName())
//                 .style(ChatFormatting.DARK_GRAY)
//                 .forGoggles(tooltip, 1);
// 
//         if (debugPeerPos != null) {
//             lang().translate("wireless.goggles.peer", debugPeerPos.toShortString())
//                     .style(ChatFormatting.DARK_GRAY)
//                     .forGoggles(tooltip, 1);
//         }
//         if (debugRequiredFacing != null) {
//             lang().translate("wireless.goggles.required_facing", debugRequiredFacing.getName())
//                     .style(ChatFormatting.YELLOW)
//                     .forGoggles(tooltip, 1);
//         }
//         return true;
//     }
// 
//     private String statusLangKey() {
//         return "wireless.fluid.status." + debugStatus.name().toLowerCase(Locale.ROOT);
//     }
// 
//     private ChatFormatting statusColor() {
//         return switch (debugStatus) {
//             case READY -> ChatFormatting.GREEN;
//             case UNBOUND -> ChatFormatting.RED;
//             default -> ChatFormatting.GOLD;
//         };
//     }
// 
//     /** 与 B6/B10 一致：不能用 CreateLang.translate（它会前缀成 create.）。 */
//     private static LangBuilder lang() {
//         return new LangBuilder(AvaritiaSingularityStressMod.MODID);
//     }
// 
//     // ===================== IFrequencyBindable / IWirelessConfigHolder =====================
// 
//     @Override
//     public int getFrequency() {
//         return frequency;
//     }
// 
//     @Override
//     public void setFrequency(int frequency) {
//         int clamped = IFrequencyBindable.clampFrequency(frequency);
//         if (this.frequency == clamped) {
//             return;
//         }
//         this.frequency = clamped;
//         setChanged();
//         sendData();
//         WirelessNetwork.move(this);
//     }
// 
//     @Override
//     public ChannelVisibility getVisibility() {
//         return visibility;
//     }
// 
//     @Override
//     public void setVisibility(ChannelVisibility visibility) {
//         if (this.visibility == visibility) {
//             return;
//         }
//         this.visibility = visibility;
//         setChanged();
//         sendData();
//         WirelessNetwork.move(this);
//     }
// 
//     @Override
//     public UUID getOwner() {
//         return owner;
//     }
// 
//     @Override
//     public void setOwner(UUID owner) {
//         this.owner = owner;
//         setChanged();
//         sendData();
//         WirelessNetwork.move(this);
//     }
// 
//     // ===================== 持久化 =====================
// 
//     @Override
//     protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
//         super.write(tag, registries, clientPacket);
//         writeFrequency(tag, registries, clientPacket);
//         // 诊断数据只进客户端同步包，不落盘（每 5 tick 重算）
//         if (clientPacket) {
//             tag.putInt(KEY_STATUS, debugStatus.ordinal());
//             if (debugPeerPos != null) {
//                 tag.putLong(KEY_PEER, debugPeerPos.asLong());
//             }
//             if (debugRequiredFacing != null) {
//                 tag.putInt(KEY_REQUIRED_FACING, debugRequiredFacing.ordinal());
//             }
//         }
//     }
// 
//     @Override
//     protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
//         super.read(tag, registries, clientPacket);
//         readFrequency(tag, registries, clientPacket);
//         if (clientPacket) {
//             WirelessFluidBridge.BridgeStatus[] values = WirelessFluidBridge.BridgeStatus.values();
//             int ordinal = tag.getInt(KEY_STATUS);
//             debugStatus = ordinal >= 0 && ordinal < values.length
//                     ? values[ordinal] : WirelessFluidBridge.BridgeStatus.UNBOUND;
//             debugPeerPos = tag.contains(KEY_PEER) ? BlockPos.of(tag.getLong(KEY_PEER)) : null;
//             Direction[] directions = Direction.values();
//             int facingOrdinal = tag.contains(KEY_REQUIRED_FACING) ? tag.getInt(KEY_REQUIRED_FACING) : -1;
//             debugRequiredFacing = facingOrdinal >= 0 && facingOrdinal < directions.length
//                     ? directions[facingOrdinal] : null;
//         }
//     }
// 
//     // ===================== 调试指令 /wireless_debug 专用（只读） =====================
// 
//     /**
//      * 把本方块实体的全部相关参数整理成多行文本，供 {@code /wireless_debug} 打进系统日志。
//      * <p>
//      * <b>只读</b>：不写 NBT、不发包、不改压强。{@code getConnection()} 会按需建立
//      * {@code interfaces} 条目（Create 自身也是这么惰性建立的），不影响流体行为。
//      */
//     public List<String> debugDump() {
//         List<String> out = new ArrayList<>();
//         BlockState state = getBlockState();
//         Direction facing = state.getValue(WirelessFluidTransmitterBlock.FACING);
//         out.add("类型=B11 无线流体传输器 " + (isOutputSide() ? "输出端 SENDER" : "接收端 RECEIVER")
//                 + " state=" + state + " FACING=" + facing
//                 + " 接入点(这里必须贴着真管道)=" + getBlockPos().relative(facing));
//         out.add("频率=" + (hasFrequency() ? String.valueOf(frequency) : "未绑定(-1)")
//                 + " 可见性=" + visibility
//                 + " owner=" + (owner == null ? "null" : owner)
//                 + " key=" + getKey());
//         out.add("护目镜诊断缓存: status=" + debugStatus.name() + " peerPos=" + debugPeerPos
//                 + " requiredFacing=" + debugRequiredFacing);
// 
//         FluidTransportBehaviour pipe = getBehaviour(FluidTransportBehaviour.TYPE);
//         if (pipe == null) {
//             out.add("流体行为: 缺失！B11 必须挂 WirelessFluidTransmitterBehaviour，"
//                     + "否则 Create 的流体系统根本看不见它（管道不会连过来）");
//         } else {
//             out.add("流体行为=" + pipe.getClass().getSimpleName() + " hasAnyPressure=" + pipe.hasAnyPressure());
//             for (Direction direction : Direction.values()) {
//                 boolean flow = pipe.canHaveFlowToward(state, direction);
//                 PipeConnection connection = pipe.getConnection(direction);
//                 out.add("   dir=" + direction + " canHaveFlowToward=" + flow + (connection == null
//                         ? " connection=null"
//                         : " connection[in=" + connection.getPressure().getFirst()
//                                 + ", out=" + connection.getPressure().getSecond()
//                                 + ", hasFlow=" + connection.hasFlow() + "]"));
//             }
//         }
// 
//         for (Direction direction : Direction.values()) {
//             BlockPos neighbourPos = getBlockPos().relative(direction);
//             BlockState neighbour = level.getBlockState(neighbourPos);
//             StringBuilder sb = new StringBuilder("   邻居 ").append(direction).append(' ').append(neighbourPos)
//                     .append(" = ").append(BuiltInRegistries.BLOCK.getKey(neighbour.getBlock()));
//             FluidTransportBehaviour neighbourPipe = FluidPropagator.getPipe(level, neighbourPos);
//             if (neighbourPipe != null) {
//                 sb.append(" [管道行为 ").append(neighbourPipe.getClass().getSimpleName())
//                         .append(" 朝本块连通=")
//                         .append(neighbourPipe.canHaveFlowToward(neighbour, direction.getOpposite()))
//                         .append(']');
//             }
//             out.add(sb.toString());
//         }
// 
//         out.add("范围: fluidTransmitterRange=" + WirelessConfig.fluidTransmitterRange()
//                 + " create.getPumpRange()=" + FluidPropagator.getPumpRange());
//         out.addAll(WirelessFluidBridge.explain(level, getBlockPos()));
//         return out;
//     }
// }
