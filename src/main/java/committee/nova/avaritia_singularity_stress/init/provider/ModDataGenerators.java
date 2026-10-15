package committee.nova.avaritia_singularity_stress.init.provider;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import net.minecraft.data.tags.TagsProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(
        modid = AvaritiaSingularityStressMod.MODID
)
public final class ModDataGenerators {

    private ModDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        if (event.includeClient()) {
            event.createProvider(output ->
                    new ModLanguageProvider(output));
            event.createProvider(output ->
                    new ModLanguageProviderZhCn(output));
            // 方块模型必须先于物品模型生成:物品模型用 withExistingParent 引用方块模型,
            // 若物品模型先跑,ExistingFileHelper 会因找不到该模型而抛
            // "Model ... does not exist"。
            event.createProvider(output ->
                    new ModBlockStateProvider(output, event.getExistingFileHelper()));
            event.createProvider(output ->
                    new ModModelProvider(output, event.getExistingFileHelper()));
        }

        if (event.includeServer()) {
            ModTagProvider.ModBlockTagsProvider blockTags =
                    new ModTagProvider.ModBlockTagsProvider(
                            event.getGenerator().getPackOutput(),
                            event.getLookupProvider(),
                            event.getExistingFileHelper()
                    );

            event.getGenerator().addProvider(event.includeServer(), blockTags);
            event.getGenerator().addProvider(
                    event.includeServer(),
                    new ModTagProvider.ModItemTagsProvider(
                            event.getGenerator().getPackOutput(),
                            event.getLookupProvider(),
                            blockTags.contentsGetter(),
                            event.getExistingFileHelper()
                    )
            );
            event.getGenerator().addProvider(
                    event.includeServer(),
                    ModLootTableProvider.create(
                            event.getGenerator().getPackOutput(),
                            event.getLookupProvider()
                    )
            );
            event.getGenerator().addProvider(
                    event.includeServer(),
                    new ModRecipeProvider(
                            event.getGenerator().getPackOutput(),
                            event.getLookupProvider()
                    )
            );
        }
    }
}
