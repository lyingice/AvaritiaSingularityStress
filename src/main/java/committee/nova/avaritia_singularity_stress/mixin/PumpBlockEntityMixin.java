// ============================================================================
// [已雪藏 · SHELVED] B11 专用 Mixin: PumpBlockEntityMixin
// 无线流体传输器(B11)实现难度过高，整体注释保留待后续重启。
// 恢复时需要同步取消注释:
//   ModBlocks / ModItems / ModBlockEntities / ModCreativeTabs /
//   ModLootTableProvider / ModModelProvider / ModLanguageProvider(En/ZhCn) /
//   WirelessGuiKind / WirelessConfig / WirelessDebugCommand / mixins.json
// ============================================================================
// package committee.nova.avaritia_singularity_stress.mixin;
// 
// import java.util.IdentityHashMap;
// import java.util.List;
// import java.util.Map;
// 
// import com.llamalad7.mixinextras.sugar.Local;
// import com.simibubi.create.content.fluids.FluidPropagator;
// import com.simibubi.create.content.fluids.FluidTransportBehaviour;
// import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
// 
// import committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter.WirelessFluidBridge;
// 
// import net.createmod.catnip.data.Pair;
// import net.createmod.catnip.math.BlockFace;
// import net.minecraft.core.BlockPos;
// import net.minecraft.core.Direction;
// import net.minecraft.world.level.BlockGetter;
// import net.minecraft.world.level.Level;
// import net.minecraft.world.level.block.entity.BlockEntity;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.Redirect;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// 
// /**
//  * 让 {@code PumpBlockEntity.distributePressureTo} 真正能穿过 B11 无线流体传输器。
//  * <p>
//  * 这个方法有<b>两段</b>算法，必须同时改，否则桥接不成立：
//  * <ol>
//  *   <li><b>建图 BFS</b>（{@link #avaritia_singularity_stress$bridgeGraphHop} +
//  *       {@link #avaritia_singularity_stress$skipBridgeNode}）：
//  *       以<b>原距离</b>补齐 {@code pipeGraph} 的两条边并把对端接入点放进 frontier，
//  *       然后让 Create 自己的 {@code if (pipeBehaviour == null) continue;} 跳过本轮剩余逻辑
//  *       —— 也就是设计定稿说的"做同样的注入，同时补齐 pipeGraph 的边"。
//  *       <p>
//  *       为什么不能用 {@code ci.cancel()}：那是"从整个方法 return"，
//  *       会把刚入队的对端条目连同整个 BFS 一起丢掉（在 {@code FluidPropagatorMixin} 上实测过）。
//  *       <p>
//  *       为什么要跳过剩余逻辑：不跳的话，Create 会按老规则把 B11 自己当成图节点/端点
//  *       （{@code targets.add(blockFace)} / 以 distance+1 再入队一次），
//  *       那是多余的，甚至会给 B11 的管道连接加上压强。</li>
//  *   <li><b>找端点 DFS</b>（{@link #avaritia_singularity_stress$bridgeDfsNode}）：
//  *       {@code searchForEndpointRecursively} 在 {@code PumpBlockEntity:257} 处
//  *       <b>自己用 {@code currentPos.relative(nextFacing)} 重算下一格</b>，并不读 {@code pipeGraph} 里的坐标。
//  *       只改建图的话，DFS 会走到 B11 本身而不是对端接入点，
//  *       {@code pipeGraph.containsKey(B11)} 为假、整条支路被判失败，压强永远过不了桥。
//  *       这一处只需替换坐标（DFS 不做距离/范围判定），所以用 {@code @Redirect} 即可。</li>
//  * </ol>
//  * 三处都调用 {@link WirelessFluidBridge}，保证 BFS 与 DFS 对同一条虚拟边给出同一个坐标。
//  * <p>
//  * 该方法只在服务端跑：{@code PumpBlockEntity.tick()} 在 {@code level.isClientSide && !isVirtual()} 时直接 return。
//  */
// @Mixin(PumpBlockEntity.class)
// public abstract class PumpBlockEntityMixin {
// 
//     /**
//      * 建图 BFS：跨桥时以原距离补边并入队。
//      * <p>
//      * <b>{@code ordinal = 2} 是必须的</b>：{@code distributePressureTo} 里有 <b>3 处</b>
//      * {@code BlockFace.getConnectedPos()}（字节码偏移 122 / 196 / 375，对应源码里
//      * {@code start.getConnectedPos()} 两次与循环体内的 {@code blockFace.getConnectedPos()} 一次）。
//      * 只有第 3 处（{@code ordinal = 2}）在循环体开头、而且那时 {@code blockFace} 与 {@code entry} 才在作用域内
//      * —— 不指定 ordinal 时注入会落到前两处，{@code @Local} 找不到 {@code blockFace}/{@code entry} 而整个 Mixin 应用失败。
//      * <p>
//      * 局部变量按类型取（MixinExtras {@code @Local}）：{@code blockFace} 是方法里<b>第二个</b>
//      * {@code BlockFace}（第一个是 {@code start}），必须写 {@code ordinal = 1}；{@code entry} 是唯一的 Pair 局部
//      * （注意 {@code BlockFace} 本身继承 {@code Pair}，但两者 LVT 描述符不同，不会混淆），
//      * {@code pipeGraph} 是此刻唯一在作用域内的 Map，{@code frontier} 是唯一的 List，
//      * {@code pull} 是此刻唯一在作用域内的 boolean。
//      */
//     @Inject(
//             method = "distributePressureTo",
//             at = @At(
//                     value = "INVOKE",
//                     target = "Lnet/createmod/catnip/math/BlockFace;getConnectedPos()Lnet/minecraft/core/BlockPos;",
//                     ordinal = 2,
//                     shift = At.Shift.AFTER))
//     private void avaritia_singularity_stress$bridgeGraphHop(
//             CallbackInfo ci,
//             @Local(ordinal = 1) BlockFace blockFace,
//             @Local boolean pull,
//             @Local Pair<Integer, BlockPos> entry,
//             @Local Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph,
//             @Local List<Pair<Integer, BlockPos>> frontier) {
//         Level level = ((BlockEntity) (Object) this).getLevel();
//         if (level == null) {
//             return;
//         }
//         BlockPos currentPos = blockFace.getPos();
//         Direction face = blockFace.getFace();
//         BlockPos peerEntry = WirelessFluidBridge.peerEntry(level, currentPos, face);
//         if (peerEntry == null) {
//             return;
//         }
// 
//         // 跨桥：距离保持原值（不加 1），也不受 distance + 1 >= maxDistance 的截断
//         int distance = entry.getFirst();
//         pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
//                 .getSecond()
//                 .put(face, pull);
//         pipeGraph.computeIfAbsent(peerEntry, $ -> Pair.of(distance, new IdentityHashMap<>()))
//                 .getSecond()
//                 .put(face.getOpposite(), !pull);
//         frontier.add(Pair.of(distance, peerEntry));
//     }
// 
//     /**
//      * 建图 BFS 的"收尾"：跨桥这一跳已经由 {@link #avaritia_singularity_stress$bridgeGraphHop} 处理完，
//      * 这里把 {@code connectedPos}（= B11 自己）的管道行为读成 null，
//      * 借 Create 原有的 {@code if (pipeBehaviour == null) continue;} 跳过本轮剩余逻辑。
//      * <p>
//      * <b>{@code ordinal = 1}</b>：{@code distributePressureTo} 里 {@code FluidPropagator.getPipe} 有 3 处
//      * （字节码偏移 311 外层 / 477 内层 / 881 施压循环），内层那一处是第 2 个。
//      */
//     @Redirect(
//             method = "distributePressureTo",
//             at = @At(
//                     value = "INVOKE",
//                     target = "Lcom/simibubi/create/content/fluids/FluidPropagator;getPipe(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lcom/simibubi/create/content/fluids/FluidTransportBehaviour;",
//                     ordinal = 1))
//     private FluidTransportBehaviour avaritia_singularity_stress$skipBridgeNode(BlockGetter reader,
//                                                                               BlockPos connectedPos) {
//         if (reader instanceof Level level && WirelessFluidBridge.isBridgeTarget(level, connectedPos)) {
//             return null;
//         }
//         return FluidPropagator.getPipe(reader, connectedPos);
//     }
// 
//     /** 找端点 DFS：下一格同样要跨桥，否则与建图结果不一致。 */
//     @Redirect(
//             method = "searchForEndpointRecursively",
//             at = @At(
//                     value = "INVOKE",
//                     target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"))
//     private BlockPos avaritia_singularity_stress$bridgeDfsNode(BlockPos currentPos, Direction nextFacing) {
//         Level level = ((BlockEntity) (Object) this).getLevel();
//         if (level != null) {
//             BlockPos bridged = WirelessFluidBridge.peerEntry(level, currentPos, nextFacing);
//             if (bridged != null) {
//                 return bridged;
//             }
//         }
//         return currentPos.relative(nextFacing);
//     }
// }
