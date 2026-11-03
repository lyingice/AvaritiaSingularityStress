package committee.nova.avaritia_singularity_stress.ponder_demo;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder 插件的唯一入口。
 * <p>SINGLETON 只 new 一次；Ponder 会在 /ponder reload 时重复调用下面三个 register 方法，
 * 因此方法体里<b>不要</b>做有副作用的事（不要注册方块、不要读世界、不要发包）。
 */
public class MyPonderPlugin implements PonderPlugin {

    public static final MyPonderPlugin INSTANCE = new MyPonderPlugin();

    /** 必须是本模组的 modid，Ponder 用它给 lang key 加前缀，也用它给 schematic 找命名空间。 */
    @Override
    public String getModId() {
        return AvaritiaSingularityStressMod.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ExampleScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ExampleScenes.registerTags(helper);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        ExampleScenes.registerSharedText(helper);
    }
}
