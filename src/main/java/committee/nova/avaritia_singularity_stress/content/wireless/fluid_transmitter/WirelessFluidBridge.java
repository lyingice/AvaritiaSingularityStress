// ============================================================================
// [已雪藏 · SHELVED] B11 跨桥判定 WirelessFluidBridge
// 无线流体传输器(B11)实现难度过高，整体注释保留待后续重启。
// 恢复时需要同步取消注释:
//   ModBlocks / ModItems / ModBlockEntities / ModCreativeTabs /
//   ModLootTableProvider / ModModelProvider / ModLanguageProvider(En/ZhCn) /
//   WirelessGuiKind / WirelessConfig / WirelessDebugCommand / mixins.json
// ============================================================================
// package committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter;
// 
// import java.util.ArrayDeque;
// import java.util.ArrayList;
// import java.util.Deque;
// import java.util.HashSet;
// import java.util.List;
// import java.util.Set;
// 
// import javax.annotation.Nullable;
// 
// import com.simibubi.create.content.fluids.FluidPropagator;
// import com.simibubi.create.content.fluids.FluidTransportBehaviour;
// import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
// 
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessDevice;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessService;
// 
// import net.minecraft.core.BlockPos;
// import net.minecraft.core.Direction;
// import net.minecraft.world.level.Level;
// import net.minecraft.world.level.block.state.BlockState;
// 
// /**
//  * B11 的"虚拟边"判定。
//  * <p>
//  * <b>为什么需要这个类</b>：Create 的流体传播用两条算法，
//  * <ol>
//  *   <li>{@code FluidPropagator.propagateChangedPipe}：一条 BFS，边是
//  *       {@code currentPos.relative(direction)}（相邻格）；</li>
//  *   <li>{@code PumpBlockEntity.distributePressureTo}：先一条 BFS 建 {@code pipeGraph}
//  *       （边由 {@code BlockFace.getConnectedPos()} 给出），再用
//  *       {@code searchForEndpointRecursively} 做 DFS 找端点 —— 而 DFS 在
//  *       {@code PumpBlockEntity:257} 处<b>自己用 {@code currentPos.relative(nextFacing)} 重算下一格</b>，
//  *       并不读 {@code pipeGraph} 里的坐标。</li>
//  * </ol>
//  * 跨桥是"不相邻的传送"，单个 {@link Direction} 表达不了；而且距离与范围判定都写在 Create 自己的循环体里。
//  * 因此两个 Mixin 的做法是：<b>接管这一跳</b>（自己做、取消 Create 本轮剩余逻辑），
//  * 三处注入点都调本类的 {@link #peerEntry}，让 BFS 与 DFS 对同一条虚拟边给出同一个坐标。
//  * <p>
//  * 桥接成立条件（已批准）：
//  * <ol>
//  *   <li>两格都必须是本模组的无线流体传输器，且都绑定了可互配的频道、在对侧
//  *       {@link WirelessConfig#fluidTransmitterRange()} 距离内（一对一，由
//  *       {@link WirelessService#findPeer} 收口）；</li>
//  *   <li>走近的那一格必须正好是 B11 的接入面（{@code FACING} 指向贴着的那根真管道）；</li>
//  *   <li>两端的 {@code FACING} 必须<b>对置</b>（{@code peerFacing == selfFacing.getOpposite()}）——
//  *       否则对端那根管道的连接方向与本端对不上，桥接无从成立；</li>
//  *   <li>两端在 {@link FluidPropagator#getPumpRange()} 内各至少有一台动力泵，且两泵
//  *       {@code PumpBlock.FACING} 同轴。</li>
//  * </ol>
//  * 跨桥这一跳<b>不累计距离</b>，也不受 {@code getPumpRange()} 截断 ——
//  * 桥两端的距离约束由 {@link WirelessConfig#fluidTransmitterRange()} 单独保证。
//  * <p>
//  * 本类只做判定，不搬流体、不提供压强（设计方案 §4.2）。
//  */
// public final class WirelessFluidBridge {
// 
//     private WirelessFluidBridge() {
//     }
// 
//     /** 有界搜索用的一跳。 */
//     private record Hop(int distance, BlockPos pos) {
//     }
// 
//     /**
//      * 统一的"跨桥下一格"。
//      * <p>
//      * 若 {@code from} 沿 {@code face} 的邻格是本模组的无线流体传输器，
//      * 且桥接成立，则返回<b>对端 B11 的接入点</b>（{@code peerPos.relative(peerFacing)}，
//      * 也就是对端贴着的那根真管道）；否则返回 null，调用方按原逻辑处理。
//      *
//      * @param level 服务端维度
//      * @param from  当前管道所在格
//      * @param face  从管道指向 B11 的方向
//      * @return 对端接入点，或 null
//      */
//     @Nullable
//     public static BlockPos peerEntry(Level level, BlockPos from, Direction face) {
//         BlockPos target = from.relative(face);
//         if (!(level.getBlockEntity(target) instanceof WirelessFluidTransmitterBlockEntity self)) {
//             return null;
//         }
// 
//         BlockState selfState = self.getBlockState();
//         Direction selfFacing = selfState.getValue(WirelessFluidTransmitterBlock.FACING);
//         // 走近的这一格必须正好是 B11 的接入面（FACING 指向贴着的那根真管道）
//         if (selfFacing != face.getOpposite()) {
//             return null;
//         }
// 
//         WirelessDevice peerDevice = WirelessService.findPeer(self, WirelessConfig.fluidTransmitterRange());
//         if (!(peerDevice instanceof WirelessFluidTransmitterBlockEntity peer)) {
//             return null;
//         }
// 
//         Direction peerFacing = peer.getBlockState().getValue(WirelessFluidTransmitterBlock.FACING);
//         // 两个锚点必须对置：只有这样，对端那根管道的连接方向才和本端一致
//         if (peerFacing != selfFacing.getOpposite()) {
//             return null;
//         }
// 
//         BlockPos selfEntry = self.getBlockPos().relative(selfFacing);
//         BlockPos peerEntry = peer.getBlockPos().relative(peerFacing);
// 
//         // 两端至少各一个动力泵，且两个泵同轴（已批准的判定方式）
//         BlockPos selfPump = findPump(level, selfEntry);
//         if (selfPump == null) {
//             return null;
//         }
//         BlockPos peerPump = findPump(level, peerEntry);
//         if (peerPump == null) {
//             return null;
//         }
//         Direction.Axis selfAxis = pumpAxis(level, selfPump);
//         Direction.Axis peerAxis = pumpAxis(level, peerPump);
//         if (selfAxis == null || selfAxis != peerAxis) {
//             return null;
//         }
// 
//         return peerEntry;
//     }
// 
//     /**
//      * 该 B11 是否构成一条成立、可跨的桥（只凭这一格判断，来向由它自己的 FACING 推出）。
//      * <p>
//      * 给"只拿得到目标坐标、拿不到来向"的注入点用：B11 唯一可能被走到的方向，
//      * 就是从它 {@code FACING} 侧贴着的那根真管道过来。
//      */
//     public static boolean isBridgeTarget(Level level, BlockPos b11Pos) {
//         if (!(level.getBlockEntity(b11Pos) instanceof WirelessFluidTransmitterBlockEntity self)) {
//             return false;
//         }
//         Direction facing = self.getBlockState().getValue(WirelessFluidTransmitterBlock.FACING);
//         return peerEntry(level, b11Pos.relative(facing), facing.getOpposite()) != null;
//     }
// 
//     /**
//      * 从某一台 B11 自身角度做的完整诊断。
//      * <p>
//      * 与 {@link #peerEntry} 判定<b>同一套条件</b>，但把"哪一步不成立"分开报出来，
//      * 供护目镜显示 —— 桥不工作时玩家最需要知道的就是"卡在哪一条"。
//      * 本方法只用于显示（每 5 tick 一次），不在 BFS/DFS 热路径上。
//      */
//     public record Diagnosis(BridgeStatus status, @Nullable BlockPos peerPos,
//                             @Nullable Direction requiredFacing) {
// 
//         public static final Diagnosis UNBOUND = new Diagnosis(BridgeStatus.UNBOUND, null, null);
//     }
// 
//     /** 桥接诊断结果。对应 {@code avaritia_singularity_stress.wireless.fluid.status.<小写名>}。 */
//     public enum BridgeStatus {
//         /** 没写频率。 */
//         UNBOUND,
//         /** 同频率、同可见性、在范围内的对端不存在。 */
//         NO_PEER,
//         /** 频道里确实有同频对端，但离得太远（超过 {@code fluidTransmitterRange}）。 */
//         PEER_OUT_OF_RANGE,
//         /**
//          * 范围内同频对端不止一台。
//          * <p>
//          * 本方案是<b>一对一</b>：{@code WirelessService.findPeer} 在候选数 ≠ 1 时一律返回 null
//          * （宁可停机也不随机挑一个），所以出现两台以上同频对端时桥必然不成立。
//          */
//         PEER_AMBIGUOUS,
//         /** 找到了对端，但两端接入面不对置。 */
//         FACING_NOT_OPPOSITE,
//         /** 本端接入点这一侧的管道上找不到动力泵。 */
//         NO_LOCAL_PUMP,
//         /** 对端接入点这一侧的管道上找不到动力泵。 */
//         NO_PEER_PUMP,
//         /** 两端都有泵，但两泵朝向轴不同。 */
//         PUMP_AXIS_MISMATCH,
//         /** 全部条件成立，桥可以跨（剩下只等流体传播被触发）。 */
//         READY
//     }
// 
//     /**
//      * 诊断这台 B11 的桥接状态。
//      *
//      * @param level 服务端维度
//      * @param self  被诊断的 B11
//      */
//     public static Diagnosis diagnose(Level level, WirelessFluidTransmitterBlockEntity self) {
//         if (!self.hasFrequency()) {
//             return Diagnosis.UNBOUND;
//         }
// 
//         double range = WirelessConfig.fluidTransmitterRange();
//         List<WirelessDevice> inRange = WirelessService.candidates(self, range);
//         if (inRange.isEmpty()) {
//             // 区分"频道里就没设备"和"有设备但超距"：后者玩家只要靠近或调配置就行
//             List<WirelessDevice> all = WirelessService.candidates(self, Double.MAX_VALUE);
//             if (!all.isEmpty()) {
//                 return new Diagnosis(BridgeStatus.PEER_OUT_OF_RANGE, all.get(0).getBlockPos(), null);
//             }
//             return new Diagnosis(BridgeStatus.NO_PEER, null, null);
//         }
//         if (inRange.size() > 1) {
//             // 一对一语义：多台同频对端 = 歧义 = 停机（见 BridgeStatus.PEER_AMBIGUOUS）
//             return new Diagnosis(BridgeStatus.PEER_AMBIGUOUS, inRange.get(0).getBlockPos(), null);
//         }
//         if (!(inRange.get(0) instanceof WirelessFluidTransmitterBlockEntity peer)) {
//             return new Diagnosis(BridgeStatus.NO_PEER, null, null);
//         }
// 
//         Direction selfFacing = self.getBlockState().getValue(WirelessFluidTransmitterBlock.FACING);
//         Direction peerFacing = peer.getBlockState().getValue(WirelessFluidTransmitterBlock.FACING);
//         if (peerFacing != selfFacing.getOpposite()) {
//             // 把"本端端口应该朝哪"算出来一起报：玩家照着转端口就行，不用试
//             return new Diagnosis(BridgeStatus.FACING_NOT_OPPOSITE, peer.getBlockPos(),
//                     peerFacing.getOpposite());
//         }
// 
//         BlockPos selfEntry = self.getBlockPos().relative(selfFacing);
//         BlockPos peerEntry = peer.getBlockPos().relative(peerFacing);
// 
//         BlockPos selfPump = findPump(level, selfEntry);
//         if (selfPump == null) {
//             return new Diagnosis(BridgeStatus.NO_LOCAL_PUMP, peer.getBlockPos(), null);
//         }
//         BlockPos peerPump = findPump(level, peerEntry);
//         if (peerPump == null) {
//             return new Diagnosis(BridgeStatus.NO_PEER_PUMP, peer.getBlockPos(), null);
//         }
//         Direction.Axis selfAxis = pumpAxis(level, selfPump);
//         Direction.Axis peerAxis = pumpAxis(level, peerPump);
//         if (selfAxis == null || selfAxis != peerAxis) {
//             return new Diagnosis(BridgeStatus.PUMP_AXIS_MISMATCH, peer.getBlockPos(), null);
//         }
//         return new Diagnosis(BridgeStatus.READY, peer.getBlockPos(), null);
//     }
// 
//     /**
//      * 从 B11 的接入点出发，在 {@link FluidPropagator#getPumpRange()} 范围内沿真管道找一台动力泵。
//      */
//     @Nullable
//     private static BlockPos findPump(Level level, BlockPos start) {
//         int maxDistance = FluidPropagator.getPumpRange();
//         Set<BlockPos> visited = new HashSet<>();
//         Deque<Hop> frontier = new ArrayDeque<>();
//         frontier.add(new Hop(0, start));
// 
//         while (!frontier.isEmpty()) {
//             Hop hop = frontier.poll();
//             if (hop.distance() > maxDistance) {
//                 continue;
//             }
//             if (!visited.add(hop.pos())) {
//                 continue;
//             }
//             if (level.getBlockEntity(hop.pos()) instanceof PumpBlockEntity) {
//                 return hop.pos();
//             }
//             FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, hop.pos());
//             if (pipe == null) {
//                 continue;
//             }
//             BlockState state = level.getBlockState(hop.pos());
//             for (Direction direction : FluidPropagator.getPipeConnections(state, pipe)) {
//                 frontier.add(new Hop(hop.distance() + 1, hop.pos().relative(direction)));
//             }
//         }
//         return null;
//     }
// 
//     /**
//      * 供调试指令 {@code /wireless_debug} 使用的只读诊断明细。
//      * <p>
//      * 门禁判定<b>完全复用 {@link #diagnose}</b>（不重复实现），这里只额外补上
//      * "接入点在哪、那里是什么方块、两端各找到哪台泵、泵的轴是什么"这类中间量，
//      * 方便直接看出卡在哪一步。不做任何修改。
//      */
//     public static List<String> explain(Level level, BlockPos b11Pos) {
//         List<String> out = new ArrayList<>();
//         if (!(level.getBlockEntity(b11Pos) instanceof WirelessFluidTransmitterBlockEntity self)) {
//             out.add("bridge: 该坐标不是无线流体传输器方块实体");
//             return out;
//         }
// 
//         Diagnosis diagnosis = diagnose(level, self);
//         out.add("bridge 门禁: status=" + diagnosis.status() + " peerPos=" + diagnosis.peerPos()
//                 + " requiredFacing=" + diagnosis.requiredFacing() + "   (status=READY 才成立)");
// 
//         Direction selfFacing = self.getBlockState().getValue(WirelessFluidTransmitterBlock.FACING);
//         BlockPos selfEntry = b11Pos.relative(selfFacing);
//         out.add("bridge 本端: FACING=" + selfFacing + " 接入点=" + selfEntry
//                 + " 该处方块=" + level.getBlockState(selfEntry).getBlock());
// 
//         if (diagnosis.peerPos() == null) {
//             return out;
//         }
//         if (!(level.getBlockEntity(diagnosis.peerPos()) instanceof WirelessFluidTransmitterBlockEntity peer)) {
//             out.add("bridge 对端: peerPos 处不是本模组 B11 方块实体（可能已拆/未加载）");
//             return out;
//         }
// 
//         Direction peerFacing = peer.getBlockState().getValue(WirelessFluidTransmitterBlock.FACING);
//         BlockPos peerEntry = peer.getBlockPos().relative(peerFacing);
//         out.add("bridge 对端: pos=" + peer.getBlockPos() + " FACING=" + peerFacing
//                 + " 接入点=" + peerEntry + " 该处方块=" + level.getBlockState(peerEntry).getBlock());
// 
//         BlockPos selfPump = findPump(level, selfEntry);
//         BlockPos peerPump = findPump(level, peerEntry);
//         out.add("bridge 泵: getPumpRange()=" + FluidPropagator.getPumpRange()
//                 + " 本端泵=" + selfPump + " axis=" + (selfPump == null ? "-" : pumpAxis(level, selfPump))
//                 + pumpSpeedText(level, selfPump)
//                 + " | 对端泵=" + peerPump + " axis=" + (peerPump == null ? "-" : pumpAxis(level, peerPump))
//                 + pumpSpeedText(level, peerPump)
//                 + "   (两端各需至少一台且同轴；转速=0 的泵不会产生任何压强)");
//         return out;
//     }
// 
//     /** @return 该泵的转速文本；不是泵或取不到时返回空串。 */
//     private static String pumpSpeedText(Level level, @Nullable BlockPos pos) {
//         if (pos == null) {
//             return "";
//         }
//         if (level.getBlockEntity(pos) instanceof PumpBlockEntity pump) {
//             return " 转速=" + pump.getSpeed();
//         }
//         return " 转速=?";
//     }
// 
//     /** @return 动力泵的朝向轴；该格不是动力泵时返回 null。 */
//     @Nullable
//     private static Direction.Axis pumpAxis(Level level, BlockPos pos) {
//         BlockState state = level.getBlockState(pos);
//         if (!(state.getBlock() instanceof com.simibubi.create.content.fluids.pump.PumpBlock)) {
//             return null;
//         }
//         return state.getValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING).getAxis();
//     }
// }
