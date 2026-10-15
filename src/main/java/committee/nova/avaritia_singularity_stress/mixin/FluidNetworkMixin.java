// ============================================================================
// [已雪藏 · SHELVED] B11 专用 Mixin: FluidNetworkMixin
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
// import com.simibubi.create.content.fluids.FluidNetwork;
// 
// import committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter.WirelessFluidBridge;
// import committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter.WirelessFluidTransmitterBlock;
// import committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter.WirelessFluidTransmitterBlockEntity;
// 
// import net.createmod.catnip.math.BlockFace;
// import net.minecraft.core.BlockPos;
// import net.minecraft.core.Direction;
// import net.minecraft.world.level.Level;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Shadow;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Redirect;
// 
// /**
//  * B11 的第三个注入点：让<b>真正搬运流体</b>的那条算法也能跨过桥。
//  * <p>
//  * <b>为什么必须有这一处</b>：Create 6 的流体系统有三条各自独立的算法，
//  * 前两条（{@code FluidPropagator.propagateChangedPipe} 管拓扑/发现泵/清压强、
//  * {@code PumpBlockEntity.distributePressureTo} + {@code searchForEndpointRecursively} 管压强与端点）
//  * 已经由另外两个 Mixin 接上了桥，但**流体本身**是由 {@link FluidNetwork} 搬的，
//  * 而它只按坐标找相邻格：
//  * <pre>
//  *   BlockFace adjacentLocation = new BlockFace(blockFace.getPos(), side);
//  *   PipeConnection adjacent = get(adjacentLocation);      // get() = 按 pos 取行为、按 face 取连接
//  *   ...
//  *   queued.add(adjacentLocation.getOpposite());
//  * </pre>
//  * 于是"桥"这种不相邻传送对它完全不可见：压强能过去，流体一滴都过不去
//  * （实测现象：护目镜显示桥 READY、等待流体传播，但分液池不被抽取）。
//  * <p>
//  * <b>注入点为什么选这里</b>：{@code tick()} 里有两处 {@code List.add}
//  * （字节码偏移 561 = {@code targets.add}、596 = {@code queued.add}），
//  * {@code ordinal = 1} 就是 {@code queued.add(adjacentLocation.getOpposite())} ——
//  * 也就是"路由准备走进某一格"的那一步。用 {@code @Redirect} 而不是 {@code @Inject}，
//  * 是因为 redirect 的 handler 第一个参数就是被调用方法的接收者，可以直接拿到 {@code queued} 这个 List，
//  * 不必去 {@code @Shadow} 它。
//  * <p>
//  * <b>补的那条边是什么</b>：{@code FluidNetwork} 的走法是"沿下游"——被穿过的格子，其"进入边"必须是
//  * {@code flow.inbound}（流体从上一格进入这一格）。所以跨桥后要排进队列的不是对端 B11 本身，
//  * 而是<b>对端接入面上"指向对端 B11"的那个连接</b>（对端 B11 把流体交付给接入管，
//  * 对这根接入管来说正是 inbound）。它与本地"从接入面走进 B11"的那一步同构。
//  * <p>
//  * 触发条件很窄：只有路由真正走进本模组 B11 那一格时才会补边，
//  * 而 Create 自己的 {@code visited} 保证每格只入队一次，所以不会重复补。
//  */
// @Mixin(FluidNetwork.class)
// public abstract class FluidNetworkMixin {
// 
//     /**
//      * {@code FluidNetwork.world} 是包级私有字段，这里用同样的访问级别 {@code @Shadow} 进来
//      * （Mixin 会把本类合并进目标类，包内访问成立）。
//      */
//     @Shadow
//     Level world;
// 
//     @Redirect(
//             method = "tick",
//             at = @At(
//                     value = "INVOKE",
//                     target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
//                     ordinal = 1))
//     private boolean avaritia_singularity_stress$bridgeFluidHop(List<BlockFace> queued, Object added) {
//         BlockFace blockFace = (BlockFace) added;
//         boolean result = queued.add(blockFace);
// 
//         if (!(world.getBlockEntity(blockFace.getPos()) instanceof WirelessFluidTransmitterBlockEntity self)) {
//             return result;
//         }
//         Direction facing = self.getBlockState().getValue(WirelessFluidTransmitterBlock.FACING);
//         // 只认"从接入面走进 B11"这一种进入方式（blockFace.getFace() 就是从本格指向接入面的方向）
//         if (blockFace.getFace() != facing) {
//             return result;
//         }
// 
//         BlockPos entry = blockFace.getPos().relative(facing);
//         BlockPos peerEntry = WirelessFluidBridge.peerEntry(world, entry, facing.getOpposite());
//         if (peerEntry == null) {
//             return result;
//         }
// 
//         // 找对端 B11：它一定紧贴 peerEntry，且 FACING 指回 peerEntry
//         for (Direction direction : Direction.values()) {
//             if (world.getBlockEntity(peerEntry.relative(direction)) instanceof WirelessFluidTransmitterBlockEntity peer
//                     && peer.getBlockState().getValue(WirelessFluidTransmitterBlock.FACING) == direction.getOpposite()) {
//                 queued.add(new BlockFace(peerEntry, direction));
//                 break;
//             }
//         }
//         return result;
//     }
// }
