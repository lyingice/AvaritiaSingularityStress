package committee.nova.avaritia_singularity_stress.init.provider;

import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModLootTableProvider {

    // 在主类 gatherData 中通过 ModLootTableProvider.create(output, lookup) 注册
    public static LootTableProvider create(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        return new LootTableProvider(
                output,
                Set.of(),
                List.of(
                        new LootTableProvider.SubProviderEntry(
                                ModBlockLootTables::new,
                                LootContextParamSets.BLOCK
                        )
                ),
                lookupProvider
        );
    }

    // 方块战利品表生成器:继承 BlockLootSubProvider(NeoForge 1.21.1 标准写法)
    public static class ModBlockLootTables extends BlockLootSubProvider {

        protected ModBlockLootTables(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get());
            dropSelf(ModBlocks.EXTREME_SMITHING_CRAFTER.get());
            dropSelf(ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get());
            dropSelf(ModBlocks.SINGULARITY_STABILIZER.get());
            dropSelf(ModBlocks.KINETIC_NEUTRON_COLLECTOR.get());
            dropSelf(ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get());
            dropSelf(ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get());
            dropSelf(ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get());
            dropSelf(ModBlocks.KINETIC_NEUTRON_COMPRESSOR.get());
            dropSelf(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get());
            dropSelf(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get());
            dropSelf(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get());
            // B9 无线物品传输漏斗
            dropSelf(ModBlocks.WIRELESS_ITEM_FUNNEL.get());
            // B10 无线应力传输器（输出端 / 接收端）
            dropSelf(ModBlocks.WIRELESS_STRESS_BRIDGE_SENDER.get());
            dropSelf(ModBlocks.WIRELESS_STRESS_BRIDGE_RECEIVER.get());
            // [B11 雪藏] B11 无线流体传输器：整体雪藏。
            // // B11 无线流体传输器（输出端 / 接收端）
            // dropSelf(ModBlocks.WIRELESS_FLUID_TRANSMITTER_SENDER.get());
            // dropSelf(ModBlocks.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(
                    ModBlocks.EXTREME_MECHANICAL_CRAFTER.get(),
                    ModBlocks.EXTREME_SMITHING_CRAFTER.get(),
                    ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(),
                    ModBlocks.SINGULARITY_STABILIZER.get(),
                    ModBlocks.KINETIC_NEUTRON_COLLECTOR.get(),
                    ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get(),
                    ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get(),
                    ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get(),
                    ModBlocks.KINETIC_NEUTRON_COMPRESSOR.get(),
                    ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get(),
                    ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get(),
                    ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get(),
                    ModBlocks.WIRELESS_ITEM_FUNNEL.get(),
                    ModBlocks.WIRELESS_STRESS_BRIDGE_SENDER.get(),
                    ModBlocks.WIRELESS_STRESS_BRIDGE_RECEIVER.get()
                    // [B11 雪藏] ModBlocks.WIRELESS_FLUID_TRANSMITTER_SENDER.get(),
                    // [B11 雪藏] ModBlocks.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get()
            );
        }

        // ===== 以下为常用写法备忘(直接在 generate() 里调用)=====
        //
        // 1. 掉落自身(普通方块):
        //    dropSelf(ModBlocks.X.get());
        //
        // 2. 掉落其他物品(矿物类):
        //    dropOther(ModBlocks.X.get(), Items.Y);
        //
        // 3. 依赖精准采集(普通/精准两种表):
        //    this.add(ModBlocks.X.get(),
        //            createSilkTouchDispatchTable(ModBlocks.X.get(),
        //                    applyExplosionDecay(ModBlocks.X.get(), LootItem.lootTableItem(Items.Y))));
        //
        // 4. 自定义掉落数量区间:
        //    this.add(ModBlocks.X.get(),
        //            createSimpleTable(ModBlocks.X.get(), Items.Y)
        //                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 3.0F))));
    }
}