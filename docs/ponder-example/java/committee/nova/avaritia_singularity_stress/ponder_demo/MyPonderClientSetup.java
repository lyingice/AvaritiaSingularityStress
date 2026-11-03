package committee.nova.avaritia_singularity_stress.ponder_demo;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 把 Ponder 插件挂进 Ponder 的插件表。
 * <p>必须发生在 <b>客户端</b>，而且要在第一次打开思索界面之前完成：
 * FMLClientSetupEvent 是最稳的时机（Create 自己也是在这个阶段调用）。
 * 注意：Ponder 的类全部是客户端类，<b>不要</b>放进 mod 构造器或 common setup，
 * 否则专有服务器会因为加载 net.minecraft.client.* 而崩。
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID, value = Dist.CLIENT)
public final class MyPonderClientSetup {

    private MyPonderClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // addPlugin 内部有 synchronized，可以安全地放到 work queue 里
        event.enqueueWork(() -> PonderIndex.addPlugin(MyPonderPlugin.INSTANCE));
    }
}
