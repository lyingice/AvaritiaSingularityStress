package committee.nova.avaritia_singularity_stress.content.wireless;

import java.util.UUID;

import javax.annotation.Nullable;

import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable.ChannelVisibility;
import net.minecraft.core.BlockPos;

/**
 * 无线频道钥匙。
 * <p>
 * 最终规格 A1：私有与公用在配对层就已隔离（见
 * {@link WirelessDevice#canPairWith}），此处不再展开额外钥匙。
 */
public record WirelessKey(int frequency, @Nullable UUID owner, ChannelVisibility visibility) {

    public static WirelessKey of(int frequency, @Nullable UUID owner, ChannelVisibility visibility) {
        if (visibility == ChannelVisibility.PUBLIC) {
            return new WirelessKey(frequency, null, ChannelVisibility.PUBLIC);
        }
        return new WirelessKey(frequency, owner, ChannelVisibility.PRIVATE);
    }

    /** A1：私有不能连公用，只查自身。 */
    public WirelessKey[] lookupKeys() {
        return new WirelessKey[] { this };
    }

    public String describe(BlockPos pos) {
        return "freq=" + frequency + " vis=" + visibility + " owner=" + owner + " @" + pos;
    }
}