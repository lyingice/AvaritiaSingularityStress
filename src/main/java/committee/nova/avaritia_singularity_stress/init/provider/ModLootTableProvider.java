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
            // 全量 dropSelf：新增方块自动有掉落表，不再手抄清单。
            // 手抄清单踩过两次：漏了包壳方块 -> "Created block loot tables for non-blocks"；
            // 加回方块却没加进 generate -> "Missing loottable"。
            for (Block block : ModBlocks.BLOCKS.getEntries().stream()
                    .map(holder -> (Block) holder.get())
                    .toList()) {
                dropSelf(block);
            }
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            // 不再手抄方块清单：手抄的那份曾经漏掉全部包壳方块，导致 runData 直接抛
            // "Created block loot tables for non-blocks"（BlockLootSubProvider.generate 校验）。
            // 直接取 DeferredRegister 的全部条目，新加方块自动纳入。
            return ModBlocks.BLOCKS.getEntries().stream()
                    .map(holder -> (Block) holder.get())
                    .toList();
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