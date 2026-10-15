// ============================================================================
// [已雪藏 · SHELVED] B11 专用 Mixin: FluidPropagatorMixin
// 无线流体传输器(B11)实现难度过高，整体注释保留待后续重启。
// 恢复时需要同步取消注释:
//   ModBlocks / ModItems / ModBlockEntities / ModCreativeTabs /
//   ModLootTableProvider / ModModelProvider / ModLanguageProvider(En/ZhCn) /
//   WirelessGuiKind / WirelessConfig / WirelessDebugCommand / mixins.json
// ============================================================================
// package committee.nova.avaritia_singularity_stress.mixin;
// 
// import java.util.List;
// 
// import com.llamalad7.mixinextras.sugar.Local;
// import com.simibubi.create.content.fluids.FluidPropagator;
// 
// import committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter.WirelessFluidBridge;
// 
// import net.createmod.catnip.data.Pair;
// import net.minecraft.core.BlockPos;
// import net.minecraft.core.Direction;
// import net.minecraft.world.level.Level;
// import net.minecraft.world.level.LevelAccessor;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// 
// /**
//  * 让 {@code FluidPropagator.propagateChangedPipe} 的 BFS 能穿过 B11 无线流体传输器。
//  * <p>
//  * 注入位置：循环体的头一句 {@code BlockPos target = currentPos.relative(direction);} 之后。
//  * <p>
//  * <b>为什么不是"替换目标格"、也不是"取消本轮"</b>：
//  * <ol>
//  *   <li>{@code distance + 1} 是 Create 在循环体里自己算的（{@code frontier.add(Pair.of(distance + 1, target))}），
//  *       只替换坐标改不了它 —— 而设计定稿要求<b>跨桥这一跳不加距离</b>；</li>
//  *   <li>Create 在加 frontier 之前还有一道
//  *       {@code if (distance >= getPumpRange() && !targetPipe.hasAnyPressure()) continue;}
//  *       的截断。要真正"豁免这一跳"，桥两端之间的距离必须由
//  *       {@code WirelessConfig.fluidTransmitterRange()} 在配对时单独保证，不由 {@code getPumpRange()} 限制；</li>
//  *   <li><b>绝不能用 {@code ci.cancel()}</b>：那是"从整个方法 return"，
//  *       会把刚入队的对端条目连同整个 BFS 一起丢掉（实测：对端永远不被访问）。
//  *       所以这里只做"追加一条以原距离入队的边"，然后让循环自然继续 ——
//  *       目标本身（B11）后面会被 Create 自己按原有规则处理，而它只有一个流动面（指回来路），是死胡同。</li>
//  * </ol>
//  * 该 BFS 只在"方块变化 / 泵状态变化"时被调用，不是每 tick。
//  */
// @Mixin(FluidPropagator.class)
// public abstract class FluidPropagatorMixin {
// 
//     /**
//      * 局部变量按类型用 MixinExtras {@code @Local} 取，避免依赖局部变量表的顺序：
//      * <ul>
//      *   <li>{@code world} 是方法参数里唯一的 {@link LevelAccessor}；</li>
//      *   <li>{@code direction} 是循环变量里唯一的 {@link Direction}；</li>
//      *   <li>{@code pair} 是本轮从 frontier 取出的条目，其 {@code getSecond()} 就是 {@code currentPos}
//      *       （Create 在循环体开头正是这么赋的），因此不需要再捕获 BlockPos；</li>
//      *   <li>{@code frontier} 是方法里唯一的 {@link List}。</li>
//      * </ul>
//      */
//     @Inject(
//             method = "propagateChangedPipe",
//             at = @At(
//                     value = "INVOKE",
//                     target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;",
//                     shift = At.Shift.AFTER))
//     private static void avaritia_singularity_stress$bridgeFluidHop(
//             CallbackInfo ci,
//             @Local LevelAccessor world,
//             @Local Direction direction,
//             @Local Pair<Integer, BlockPos> pair,
//             @Local List<Pair<Integer, BlockPos>> frontier) {
//         if (!(world instanceof Level level)) {
//             return;
//         }
//         BlockPos currentPos = pair.getSecond();
//         BlockPos peerEntry = WirelessFluidBridge.peerEntry(level, currentPos, direction);
//         if (peerEntry == null) {
//             return;
//         }
//         // 跨桥：距离保持原值（不加 1），也不受 getPumpRange() 截断；本轮其余逻辑照常走
//         frontier.add(Pair.of(pair.getFirst(), peerEntry));
//     }
// }
