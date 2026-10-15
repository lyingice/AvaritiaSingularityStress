package committee.nova.avaritia_singularity_stress.content.wireless;

import java.util.UUID;

import javax.annotation.Nullable;

import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable.ChannelVisibility;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface WirelessDevice {

    Level getLevel();

    BlockPos getBlockPos();

    @Nullable
    WirelessKey getKey();

    boolean isAlive();

    default boolean wantsWirelessTick() {
        return false;
    }

    default void tickWireless() {
    }

    @Nullable
    UUID getOwner();

    ChannelVisibility getVisibility();

    /**
     * 最终规格 A1：公私不混。
     * <ul>
     *   <li>公用 ↔ 公用：允许；</li>
     *   <li>私有 ↔ 私有：owner 相同才允许；</li>
     *   <li>私有 ↔ 公用：禁止。</li>
     * </ul>
     */
    default boolean canPairWith(WirelessDevice other) {
        boolean selfPublic = getVisibility() == ChannelVisibility.PUBLIC;
        boolean otherPublic = other.getVisibility() == ChannelVisibility.PUBLIC;
        if (selfPublic != otherPublic) {
            return false;
        }
        if (selfPublic) {
            return true;
        }
        UUID owner = getOwner();
        return owner != null && owner.equals(other.getOwner());
    }
}