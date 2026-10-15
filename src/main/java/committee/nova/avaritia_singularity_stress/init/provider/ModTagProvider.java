package committee.nova.avaritia_singularity_stress.init.provider;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModTagProvider {
    // 方块标签
    public static class ModBlockTagsProvider extends BlockTagsProvider {
        public ModBlockTagsProvider(PackOutput output,
                                    CompletableFuture<HolderLookup.Provider> lookupProvider,
                                    @Nullable ExistingFileHelper existingFileHelper) {
            super(output, lookupProvider, AvaritiaSingularityStressMod.MODID, existingFileHelper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            // 机器方块统一可被镐开采(否则战利品表不会判定为可挖掘)。
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
                    .add(committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.EXTREME_MECHANICAL_CRAFTER.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.EXTREME_SMITHING_CRAFTER.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.SINGULARITY_STABILIZER.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.KINETIC_NEUTRON_COLLECTOR.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.KINETIC_NEUTRON_COMPRESSOR.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get(),
                            committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get());
        }
    }

    // 物品标签
    public static class ModItemTagsProvider extends ItemTagsProvider {
        public ModItemTagsProvider(PackOutput output,
                                   CompletableFuture<HolderLookup.Provider> lookupProvider,
                                   CompletableFuture<TagLookup<Block>> blockTags,
                                   @Nullable ExistingFileHelper existingFileHelper) {
            super(output, lookupProvider, blockTags, AvaritiaSingularityStressMod.MODID, existingFileHelper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            tag(ItemTags.DURABILITY_ENCHANTABLE)
                    .add(committee.nova.avaritia_singularity_stress.init.registry.ModItems.NEUTRON_EXTENDO_GRIP.get());
        }
    }
}