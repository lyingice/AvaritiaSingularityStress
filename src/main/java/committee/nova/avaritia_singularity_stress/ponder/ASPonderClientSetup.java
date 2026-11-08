package committee.nova.avaritia_singularity_stress.ponder;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 把 Ponder 插件挂进 Ponder 的插件表。
 * <p>
 * 时机要点（这条是文档里的「未验证项」，按本仓库实证写法来）：
 * {@code PonderIndex.addPlugin(...)} 只是把插件塞进静态列表，<b>不会立刻注册</b>；
 * 真正的注册发生在 {@code PonderIndex.reload() -> registerAll()}。
 * 所以只要保证「第一次打开思索界面之前」调用到即可，
 * {@link FMLClientSetupEvent} 是 Create 自己用的阶段，也是这里选用的阶段。
 * <p>
 * 必须是 {@code Dist.CLIENT}：Ponder 全部是客户端类，放进 common 会让专用服务端崩。
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID, value = Dist.CLIENT)
public final class ASPonderClientSetup {

    private ASPonderClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // addPlugin 内部带 synchronized，可以安全放进 work queue
        event.enqueueWork(() -> PonderIndex.addPlugin(ASPonderPlugin.INSTANCE));
    }
}
