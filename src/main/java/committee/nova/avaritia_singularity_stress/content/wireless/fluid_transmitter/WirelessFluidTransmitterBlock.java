// ============================================================================
// [已雪藏 · SHELVED] B11 方块 WirelessFluidTransmitterBlock
// 无线流体传输器(B11)实现难度过高，整体注释保留待后续重启。
// 恢复时需要同步取消注释:
//   ModBlocks / ModItems / ModBlockEntities / ModCreativeTabs /
//   ModLootTableProvider / ModModelProvider / ModLanguageProvider(En/ZhCn) /
//   WirelessGuiKind / WirelessConfig / WirelessDebugCommand / mixins.json
// ============================================================================
// package committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter;
// 
// import java.util.UUID;
// 
// import com.simibubi.create.content.equipment.wrench.IWrenchable;
// import com.simibubi.create.content.fluids.FluidPropagator;
// import com.simibubi.create.foundation.block.IBE;
// 
// import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessBlockInteractions;
// import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigMenu;
// import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
// import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
// import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;
// 
// import net.minecraft.core.BlockPos;
// import net.minecraft.core.Direction;
// import net.minecraft.nbt.CompoundTag;
// import net.minecraft.network.chat.Component;
// import net.minecraft.server.level.ServerLevel;
// import net.minecraft.util.RandomSource;
// import net.minecraft.world.InteractionResult;
// import net.minecraft.world.SimpleMenuProvider;
// import net.minecraft.world.entity.LivingEntity;
// import net.minecraft.world.entity.player.Player;
// import net.minecraft.world.item.ItemStack;
// import net.minecraft.world.item.context.BlockPlaceContext;
// import net.minecraft.world.item.context.UseOnContext;
// import net.minecraft.world.level.Level;
// import net.minecraft.world.level.block.Block;
// import net.minecraft.world.level.block.Mirror;
// import net.minecraft.world.level.block.Rotation;
// import net.minecraft.world.level.block.entity.BlockEntity;
// import net.minecraft.world.level.block.entity.BlockEntityType;
// import net.minecraft.world.level.block.state.BlockState;
// import net.minecraft.world.level.block.state.StateDefinition.Builder;
// import net.minecraft.world.level.block.state.properties.BlockStateProperties;
// import net.minecraft.world.level.block.state.properties.BooleanProperty;
// import net.minecraft.world.level.block.state.properties.DirectionProperty;
// import net.minecraft.world.ticks.TickPriority;
// import net.minecraft.world.phys.BlockHitResult;
// 
// /**
//  * B11 无线流体传输器的方块（方案 A：两个注册名、一个方块类）。
//  * <p>
//  * 它<b>不是管道、也不提供压强</b>，只是坐标锚点：玩家必须在 {@link #FACING} 那一面贴上真管道。
//  * 真正让流体"穿过"它的是 {@code WirelessFluidBridge} + 两个 Mixin，见该类注释。
//  * <p>
//  * <b>{@link #POWERED} 说明</b>：已定稿的 blockstate JSON 里变体键含 {@code powered=false}，
//  * 所以必须声明这个属性否则状态匹配不上；JSON 里只有 false 的变体，故默认 false 且永不置 true。
//  * <p>
//  * 压强传播是"变化驱动"的：{@code propagateChangedPipe} 内部会 wipePressure()，
//  * 绝不能每 tick 调，所以只在放置 / 邻居变化 / 移除时调度一次 tick。
//  */
// public class WirelessFluidTransmitterBlock extends Block
//         implements IBE<WirelessFluidTransmitterBlockEntity>, IWrenchable {
// 
//     /** 输出端（Sender）标记。 */
//     public static final BooleanProperty OUTPUT = BooleanProperty.create("output");
// 
//     /** 仅为匹配 blockstate JSON 而存在的红石属性，恒为 false（见类注释）。 */
//     public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
// 
//     /** 玩家贴真管道的那一面，也是 B11 唯一开放流动的方向。 */
//     public static final DirectionProperty FACING = BlockStateProperties.FACING;
// 
//     private final boolean outputSide;
// 
//     public WirelessFluidTransmitterBlock(Properties properties, boolean outputSide) {
//         super(properties);
//         this.outputSide = outputSide;
//         registerDefaultState(defaultBlockState()
//                 .setValue(FACING, Direction.NORTH)
//                 .setValue(OUTPUT, outputSide)
//                 .setValue(POWERED, false));
//     }
// 
//     @Override
//     protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
//         builder.add(FACING, OUTPUT, POWERED);
//     }
// 
//     @Override
//     public BlockState rotate(BlockState state, Rotation rotation) {
//         return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
//     }
// 
//     @Override
//     @SuppressWarnings("deprecation")
//     public BlockState mirror(BlockState state, Mirror mirror) {
//         return state.rotate(mirror.getRotation(state.getValue(FACING)));
//     }
// 
//     @Override
//     public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
//         return WirelessFluidTransmitterBlockEntity.create(pos, state);
//     }
// 
//     @Override
//     public Class<WirelessFluidTransmitterBlockEntity> getBlockEntityClass() {
//         return WirelessFluidTransmitterBlockEntity.class;
//     }
// 
//     @Override
//     public BlockEntityType<? extends WirelessFluidTransmitterBlockEntity> getBlockEntityType() {
//         return outputSide
//                 ? ModBlockEntities.WIRELESS_FLUID_TRANSMITTER_SENDER.get()
//                 : ModBlockEntities.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get();
//     }
// 
//     /**
//      * 放置时决定 {@link #FACING}（= 管道接入面）。
//      * <p>
//      * <b>为什么必须有这个方法</b>：本类只继承 {@code Block}，没有 {@code DirectionalKineticBlock}
//      * 那套自动朝向逻辑，之前也没有覆写它 —— 于是 {@code registerDefaultState} 里的
//      * {@code FACING = NORTH} 就是唯一结果：<b>方块永远朝北</b>。
//      * 而桥接判定要求"管道必须贴在 FACING 那一面"，所以只要管道贴的不是北面，桥永远不成立
//      * （护目镜里会显示 {@code 状态:本端接入面这一侧的管道上找不到动力泵}）。
//      * <p>
//      * 现在的规则：
//      * <ol>
//      *   <li>先看"你点的那一面的外侧"是不是真管道 —— 贴着管道放就正好朝它（最常用的操作）；</li>
//      *   <li>否则扫一圈邻居，第一根真管道就是接入面；</li>
//      *   <li>都没有就朝向你（端口朝外对着你），之后再贴管道到那一面即可。</li>
//      * </ol>
//      */
//     @Override
//     public BlockState getStateForPlacement(BlockPlaceContext context) {
//         Level level = context.getLevel();
//         BlockPos pos = context.getClickedPos();
// 
//         Direction towardClicked = context.getClickedFace().getOpposite();
//         if (isPipeAt(level, pos.relative(towardClicked))) {
//             return withFacing(towardClicked);
//         }
//         for (Direction side : Direction.values()) {
//             if (side != towardClicked && isPipeAt(level, pos.relative(side))) {
//                 return withFacing(side);
//             }
//         }
//         return withFacing(towardClicked);
//     }
// 
//     private BlockState withFacing(Direction facing) {
//         return defaultBlockState()
//                 .setValue(FACING, facing)
//                 .setValue(OUTPUT, outputSide)
//                 .setValue(POWERED, false);
//     }
// 
//     private static boolean isPipeAt(Level level, BlockPos pos) {
//         return FluidPropagator.getPipe(level, pos) != null;
//     }
// 
//     /** 放置时把放置者记为频道所有者（私有频道靠它判定配对）。 */
//     @Override
//     public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
//         if (!level.isClientSide
//                 && placer instanceof Player player
//                 && level.getBlockEntity(pos) instanceof WirelessFluidTransmitterBlockEntity transmitter) {
//             transmitter.setOwner(player.getUUID());
//         }
//     }
// 
//     // ===================== 流体传播的触发（变化驱动，绝不用 ticker 每 tick 调） =====================
// 
//     @Override
//     public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
//         if (level.isClientSide || state.getBlock() == oldState.getBlock()) {
//             return;
//         }
//         level.scheduleTick(pos, this, 1);
//     }
// 
//     @Override
//     public void neighborChanged(BlockState state, Level level, BlockPos pos, Block otherBlock,
//                                 BlockPos neighborPos, boolean isMoving) {
//         if (level.isClientSide) {
//             return;
//         }
//         level.scheduleTick(pos, this, 1, TickPriority.HIGH);
//     }
// 
//     @Override
//     public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
//         FluidPropagator.propagateChangedPipe(level, pos, state);
//     }
// 
//     @Override
//     public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
//         if (state.getBlock() != newState.getBlock()) {
//             if (!level.isClientSide) {
//                 FluidPropagator.propagateChangedPipe(level, pos, state);
//             }
//             IBE.onRemove(state, level, pos, newState);
//         }
//     }
// 
//     // ===================== 交互 =====================
// 
//     /** 空手右键 → 打开无线配置界面；手持扳手/频率卡时让位给物品（见 {@link WirelessBlockInteractions}）。 */
//     @Override
//     protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
//                                                Player player, BlockHitResult hitResult) {
//         if (WirelessBlockInteractions.deferToHeldItem(player)) {
//             return InteractionResult.PASS;
//         }
//         if (level.isClientSide) {
//             return InteractionResult.SUCCESS;
//         }
//         if (level.getBlockEntity(pos) instanceof WirelessFluidTransmitterBlockEntity transmitter) {
//             player.openMenu(
//                     new SimpleMenuProvider(
//                             (id, inventory, p) -> new WirelessConfigMenu(
//                                     ModMenus.WIRELESS_CONFIG.get(), id, inventory, transmitter),
//                             Component.translatable(getDescriptionId())),
//                     buffer -> buffer.writeBlockPos(pos));
//         }
//         return InteractionResult.SUCCESS;
//     }
// 
//     /**
//      * 扳手：在输出端 / 接收端之间切换（默认实现会旋转方块，不满足要求）。
//      * <p>
//      * 两个角色是两个注册名，切换等于换方块；换掉时 BE 会重建，所以要把 NBT 搬过去，
//      * 同时避免把旧的运动学残留带进来。
//      */
//     @Override
//     public InteractionResult onWrenched(BlockState state, UseOnContext context) {
//         Level level = context.getLevel();
//         BlockPos pos = context.getClickedPos();
//         if (level.isClientSide) {
//             return InteractionResult.SUCCESS;
//         }
// 
//         boolean nowOutput = !state.getValue(OUTPUT);
//         BlockState swapped = (nowOutput
//                 ? ModBlocks.WIRELESS_FLUID_TRANSMITTER_SENDER.get()
//                 : ModBlocks.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get())
//                 .defaultBlockState()
//                 .setValue(FACING, state.getValue(FACING))
//                 .setValue(OUTPUT, nowOutput)
//                 .setValue(POWERED, false);
// 
//         BlockEntity previous = level.getBlockEntity(pos);
//         CompoundTag saved = previous == null ? null : previous.saveWithFullMetadata(level.registryAccess());
// 
//         // 配置(频率/可见性/所有者)显式抄一份,换完方块后最后写回 ——
//         // 换角色不该弄丢玩家设好的频道。
//         int frequency = IFrequencyBindable.NO_FREQUENCY;
//         IFrequencyBindable.ChannelVisibility visibility = IFrequencyBindable.ChannelVisibility.PRIVATE;
//         UUID owner = null;
//         if (previous instanceof WirelessFluidTransmitterBlockEntity old) {
//             frequency = old.getFrequency();
//             visibility = old.getVisibility();
//             owner = old.getOwner();
//         }
// 
//         BlockState before = level.getBlockState(pos);
//         level.setBlock(pos, swapped, Block.UPDATE_ALL);
// 
//         if (level.getBlockEntity(pos) instanceof WirelessFluidTransmitterBlockEntity fresh) {
//             if (saved != null) {
//                 fresh.loadWithComponents(saved, level.registryAccess());
//             }
//             fresh.setFrequency(frequency);
//             fresh.setVisibility(visibility);
//             fresh.setOwner(owner);
//         }
//         if (level.getBlockState(pos) != before) {
//             IWrenchable.playRotateSound(level, pos);
//             // 换角色等于换了一条桥，压强必须重算
//             FluidPropagator.propagateChangedPipe(level, pos, swapped);
//         }
//         return InteractionResult.SUCCESS;
//     }
// }
