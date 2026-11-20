package committee.nova.avaritia_singularity_stress.init.client;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import committee.nova.avaritia_singularity_stress.content.extreme_connector.ExtremeConnectorBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlock;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * B30 终焉连接器连接纹理：连接器与相邻连接器（或终端）相连时平移到 connected 半区。
 * 与逻辑组网（CraftingTerminalNetwork 的相邻 BFS）判据一致。
 * Flywheel 烘焙会用 minecraft:air 占位调用 getShift，故先做方块类型守卫。
 */
public class ExtremeConnectorCTBehaviour extends ConnectedTextureBehaviour.Base {

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                              BlockPos pos, BlockPos otherPos, Direction face) {
        if (!(state.getBlock() instanceof ExtremeConnectorBlock)) {
            return false;
        }
        return other.getBlock() instanceof ExtremeConnectorBlock
                || other.getBlock() instanceof ExtremeCraftingTerminalBlock;
    }

    @Override
    public CTSpriteShiftEntry getShift(BlockState state, Direction direction,
                                       @Nullable TextureAtlasSprite sprite) {
        if (!(state.getBlock() instanceof ExtremeConnectorBlock)) {
            return null;
        }
        return ModSpriteShifts.EXTREME_CONNECTOR;
    }
}
