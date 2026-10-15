package committee.nova.avaritia_singularity_stress.init.registry;

import com.simibubi.create.AllBlocks;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.mods.avaritia_integration.integrations.create.registry.CreateIntegrationBlocks;
import committee.nova.mods.avaritia_integration.integrations.create.registry.CreateIntegrationItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AvaritiaSingularityStressMod.MODID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + AvaritiaSingularityStressMod.MODID))
                    .icon(() -> new ItemStack(ModItems.EXTREME_MECHANICAL_CRAFTER.get()))
                    .displayItems((parameters, output) -> {
                        // 本模组的终焉合成器家族
                        output.accept(ModItems.EXTREME_MECHANICAL_CRAFTER.get());
                        output.accept(ModItems.EXTREME_SMITHING_CRAFTER.get());
                        output.accept(ModItems.NEUTRON_EXTENDO_GRIP.get());
                        output.accept(ModItems.SINGULARITY_ROTATION_SPEED_CONTROLLER.get());
                        output.accept(ModItems.SINGULARITY_STABILIZER.get());

                        // B2 动能中子态素收集器(基础 / 致密 / 精英 / 极限)
                        output.accept(ModItems.KINETIC_NEUTRON_COLLECTOR.get());
                        output.accept(ModItems.KINETIC_NEUTRON_COLLECTOR_DENSE.get());
                        output.accept(ModItems.KINETIC_NEUTRON_COLLECTOR_ELITE.get());
                        output.accept(ModItems.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get());

                        // B4 动能中子态素压缩机(基础 / 致密 / 精英 / 极限)
                        output.accept(ModItems.KINETIC_NEUTRON_COMPRESSOR.get());
                        output.accept(ModItems.KINETIC_NEUTRON_COMPRESSOR_DENSE.get());
                        output.accept(ModItems.KINETIC_NEUTRON_COMPRESSOR_ELITE.get());
                        output.accept(ModItems.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get());
                        //无线系列
                        output.accept(ModItems.WIRELESS_FREQUENCY_CARD.get());
                        // B9 无线物品传输漏斗
                        output.accept(ModItems.WIRELESS_ITEM_FUNNEL.get());
                        // B10 无线应力传输器（输出端 / 接收端）
                        output.accept(ModItems.WIRELESS_STRESS_BRIDGE_SENDER.get());
                        output.accept(ModItems.WIRELESS_STRESS_BRIDGE_RECEIVER.get());
                        // [B11 雪藏] B11 无线流体传输器：整体雪藏，暂不进创造栏。
                        // // B11 无线流体传输器（输出端 / 接收端）
                        // output.accept(ModItems.WIRELESS_FLUID_TRANSMITTER_SENDER.get());
                        // output.accept(ModItems.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get());
                    })
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .build());

    public static void init() {
        // 仅用于触发类加载
    }
}