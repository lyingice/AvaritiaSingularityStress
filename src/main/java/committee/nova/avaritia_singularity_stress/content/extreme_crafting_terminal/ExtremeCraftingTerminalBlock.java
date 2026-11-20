package committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * B29 终焉动能合成终端：右键打开 GUI，选择配方后自动从相邻仓库调集材料投入连接器网络的合成器。
 * 非动能方块，自身不耗应力；应力需求显示的是所管理合成器网络的占用。
 */
public class ExtremeCraftingTerminalBlock extends Block implements IBE<ExtremeCraftingTerminalBlockEntity> {

    public ExtremeCraftingTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ExtremeCraftingTerminalBlock> codec() {
        return simpleCodec(ExtremeCraftingTerminalBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // 原版 useItemOn 已经做过一次 pick 距离/mayInteract 校验，这里再查一遍是服务端纵深防御：
        // 右键入口不能因为调用链变化而变成"只要坐标对就打开"。
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!player.canInteractWithBlock(pos, 1.0D) || !level.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof ExtremeCraftingTerminalBlock)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof ExtremeCraftingTerminalBlockEntity terminal)) {
            return InteractionResult.PASS;
        }

        // 菜单体只带坐标；初始选择作为额外数据随 openMenu 一起发给客户端。
        // 客户端用同一份数据初始化菜单会话，首帧就能高亮默认选择（钉住优先），不依赖客户端 BE 同步时序。
        ResourceLocation selected = parseSelection(terminal.getDefaultRecipeId());
        player.openMenu(terminal, buf -> {
            buf.writeBlockPos(pos);
            buf.writeBoolean(selected != null);
            if (selected != null) {
                buf.writeResourceLocation(selected);
            }
        });
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static ResourceLocation parseSelection(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(raw);
    }

    @Override
    public Class<ExtremeCraftingTerminalBlockEntity> getBlockEntityClass() {
        return ExtremeCraftingTerminalBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ExtremeCraftingTerminalBlockEntity> getBlockEntityType() {
        return ModBlockEntities.EXTREME_CRAFTING_TERMINAL.get();
    }
}
