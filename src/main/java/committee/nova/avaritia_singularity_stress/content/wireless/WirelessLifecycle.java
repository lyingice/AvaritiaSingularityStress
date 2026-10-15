package committee.nova.avaritia_singularity_stress.content.wireless;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 无线系统的维度生命周期 + 每 tick 驱动。
 * <p>
 * <b>为什么不用 {@code @EventBusSubscriber}</b>：{@link LevelEvent} 明确声明在
 * 「main Forge event bus」上（NeoForge 21.1.232 源码 {@code LevelEvent:28}），
 * 与主类已有的 {@code NeoForge.EVENT_BUS.register(this)} 是同一个总线。
 * 这里由主类显式注册本类的实例，风格与主类的 {@code tick(ServerTickEvent.Post)} 完全一致，
 * 避免 {@code @EventBusSubscriber} 对双总线的隐式选择。
 */
public final class WirelessLifecycle {

    /**
     * 节流间隔（tick）。B10 每 5 tick 才把应力推给网络，
     * 比每 tick 推便宜得多，且 Create 自身的应力同步也不是每 tick 的。
     */
    private static final int TICK_INTERVAL = 5;

    @SubscribeEvent
    public void onLoadWorld(LevelEvent.Load event) {
        WirelessNetwork.onLoadWorld(event.getLevel());
    }

    @SubscribeEvent
    public void onUnloadWorld(LevelEvent.Unload event) {
        WirelessNetwork.onUnloadWorld(event.getLevel());
    }

    /**
     * 驱动所有声明了 {@link WirelessDevice#wantsWirelessTick()} 的设备。
     * <p>
     * 做法：从当前登记的频道表收集设备（收集时顺带剔除死引用），
     * 再去重后统一回调。用 {@link HashSet} 去重是因为一台设备理论上可能被
     * 登记在多个频道下（频率刚被改写、旧条目尚未清理的瞬间）。
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TICK_INTERVAL != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level);
        }
    }

    private static void tickLevel(Level level) {
        Set<WirelessDevice> unique = new HashSet<>();
        for (WirelessDevice device : WirelessNetwork.devicesIn(level)) {
            if (device.wantsWirelessTick()) {
                unique.add(device);
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        for (WirelessDevice device : unique) {
            if (!device.isAlive()) {
                continue;
            }
            device.tickWireless();
        }
    }

    /** 调试用：某维度当前频道数。 */
    public static int debugChannelCount(LevelAccessor level) {
        return WirelessNetwork.channelCount(level);
    }
}
