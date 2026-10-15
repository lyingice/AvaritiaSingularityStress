// ============================================================================
// [已雪藏 · SHELVED] B11 流体行为 WirelessFluidTransmitterBehaviour
// 无线流体传输器(B11)实现难度过高，整体注释保留待后续重启。
// 恢复时需要同步取消注释:
//   ModBlocks / ModItems / ModBlockEntities / ModCreativeTabs /
//   ModLootTableProvider / ModModelProvider / ModLanguageProvider(En/ZhCn) /
//   WirelessGuiKind / WirelessConfig / WirelessDebugCommand / mixins.json
// ============================================================================
// package committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter;
// 
// import com.simibubi.create.content.fluids.FluidTransportBehaviour;
// import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
// 
// import net.minecraft.core.Direction;
// import net.minecraft.world.level.block.state.BlockState;
// 
// /**
//  * B11 无线流体传输器的流体行为。
//  * <p>
//  * 有它之后 B11 才会被 Create 的流体系统"看见"：
//  * <ul>
//  *   <li>{@code FluidPropagator.getPipe(...)} = {@code BlockEntityBehaviour.get(..., FluidTransportBehaviour.TYPE)}，
//  *       没有这个行为就拿不到，BFS 会在它前面中断；</li>
//  *   <li>真管道判定能否连过来用的是
//  *       {@code FluidPipeBlock.canConnectTo}：邻居不是管道时读这个行为并调用
//  *       {@code canHaveFlowToward(neighbour, direction.getOpposite())}，
//  *       返回 true 管道才会把朝向本 B11 的那一面设成"通"。</li>
//  * </ul>
//  * <b>只对 {@link #canHaveFlowToward} 里的 FACING 一面开放</b>：
//  * FACING 就是玩家贴真管道的那一面（设计定稿：B11 是坐标锚点，玩家必须在它的面上贴真管道）。
//  */
// public class WirelessFluidTransmitterBehaviour extends FluidTransportBehaviour {
// 
//     public WirelessFluidTransmitterBehaviour(SmartBlockEntity be) {
//         super(be);
//     }
// 
//     @Override
//     public boolean canHaveFlowToward(BlockState state, Direction direction) {
//         if (!(state.getBlock() instanceof WirelessFluidTransmitterBlock)) {
//             return false;
//         }
//         return direction == state.getValue(WirelessFluidTransmitterBlock.FACING);
//     }
// }
