package committee.nova.avaritia_singularity_stress.compat.jade;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.kinetic_common.OutputChance;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector.KineticNeutronCollectorBlock;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector.KineticNeutronCollectorBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor.KineticNeutronCompressorBlock;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor.KineticNeutronCompressorBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity.SingularityStress;
import committee.nova.avaritia_singularity_stress.content.singularity_stabilizer.SingularityStabilizerBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;

/**
 * 动能机器的 Jade 集成。
 * <p>
 * 依次显示:产出进度百分比 / 运行周期(秒) / 奇点状态 / 可能产出列表(物品图标 + 数量,概率低于
 * 100% 时追加百分比)。进度与状态都是服务端数据,因此通过 {@link IServerDataProvider} 写入附加
 * 数据包,客户端再读取显示。为避免未安装 Jade 时类加载失败,本类不被模组其它代码引用。
 */
@WailaPlugin
public class JadePlugin implements IWailaPlugin {

    private static final ResourceLocation COLLECTOR_UID =
            ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "kinetic_neutron_collector");
    private static final ResourceLocation COMPRESSOR_UID =
            ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "kinetic_neutron_compressor");
    private static final ResourceLocation STABILIZER_UID =
            ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "singularity_stabilizer");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CollectorServerData.INSTANCE, KineticNeutronCollectorBlockEntity.class);
        registration.registerBlockDataProvider(CompressorServerData.INSTANCE, KineticNeutronCompressorBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CollectorComponent.INSTANCE, KineticNeutronCollectorBlock.class);
        registration.registerBlockComponent(CompressorComponent.INSTANCE, KineticNeutronCompressorBlock.class);
        registration.registerBlockComponent(StabilizerComponent.INSTANCE, SingularityStabilizerBlock.class);
    }

    // ------------------------------------------------------------------
    // 服务端数据:把进度 / 周期 / 状态 / 可能产出打包
    // ------------------------------------------------------------------

    public enum CollectorServerData implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof KineticNeutronCollectorBlockEntity collector)) {
                return;
            }
            tag.putInt("RemainingTicks", collector.remainingTicks());
            tag.putInt("PeriodTicks", collector.currentPeriodTicks());
            tag.putInt("SsuState", collector.getSingularityState().ordinal());
            putOutputs(tag, collector.outputChances(), accessor);
        }

        @Override
        public ResourceLocation getUid() {
            return COLLECTOR_UID;
        }
    }

    public enum CompressorServerData implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof KineticNeutronCompressorBlockEntity compressor)) {
                return;
            }
            tag.putInt("RemainingTicks", compressor.remainingTicks());
            tag.putInt("PeriodTicks", compressor.currentPeriodTicks());
            tag.putBoolean("HasRecipe", compressor.hasRecipe());
            tag.putInt("SsuState", compressor.getSingularityState().ordinal());
            putOutputs(tag, compressor.outputChances(), accessor);
        }

        @Override
        public ResourceLocation getUid() {
            return COMPRESSOR_UID;
        }
    }

    // ------------------------------------------------------------------
    // 客户端显示:进度 / 周期 / 状态 / 产量
    // ------------------------------------------------------------------
    public enum CollectorComponent implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("PeriodTicks")) {
                return;
            }
            addProgressLines(tooltip, data);
            addStateLine(tooltip, data);
            addOutputLines(tooltip, accessor, data);
        }

        @Override
        public ResourceLocation getUid() {
            return COLLECTOR_UID;
        }

        @Override
        public boolean isRequired() {
            return true;
        }
    }

    public enum CompressorComponent implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.getBoolean("HasRecipe")) {
                return;
            }
            addProgressLines(tooltip, data);
            addStateLine(tooltip, data);
            addOutputLines(tooltip, accessor, data);
        }

        @Override
        public ResourceLocation getUid() {
            return COMPRESSOR_UID;
        }

        @Override
        public boolean isRequired() {
            return true;
        }
    }

    /** B14 奇点稳定器:常显状态行(存在即全网生效,无需服务端数据)。 */
    public enum StabilizerComponent implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            tooltip.add(Component.translatable("avaritia_singularity_stress.jade.stabilizer"));
        }

        @Override
        public ResourceLocation getUid() {
            return STABILIZER_UID;
        }

        @Override
        public boolean isRequired() {
            return true;
        }
    }

    /**
     * 第一行:产出进度百分比(保留两位小数),第二行:当前运行周期(秒)。
     * <p>
     * 进度由服务端已同步的剩余 tick / 周期 tick 反推,无需额外数据包字段:
     * {@code 进度% = (周期 − 剩余) / 周期 × 100 }。
     */
    private static void addProgressLines(ITooltip tooltip, CompoundTag data) {
        int period = Math.max(1, data.getInt("PeriodTicks"));
        int remaining = Math.max(0, Math.min(data.getInt("RemainingTicks"), period));
        float percent = (period - remaining) * 100.0F / period;

        tooltip.add(Component.translatable(
                "avaritia_singularity_stress.jade.progress",
                String.format("%.2f%%", percent)));
        tooltip.add(Component.translatable(
                "avaritia_singularity_stress.jade.cycle",
                String.format("%.1f", period / 20.0F)));
    }

    /** 奇点状态行:无 / 奇点状态 / 奇点状态(超激发态) / 奇点状态(终极激发态)。 */
    private static void addStateLine(ITooltip tooltip, CompoundTag data) {
        if (!data.contains("SsuState")) {
            return;
        }
        SingularityStress.SingularityState[] states = SingularityStress.SingularityState.values();
        int ordinal = data.getInt("SsuState");
        SingularityStress.SingularityState state =
                ordinal >= 0 && ordinal < states.length ? states[ordinal] : SingularityStress.SingularityState.NONE;

        tooltip.add(Component.translatable("avaritia_singularity_stress.jade.state",
                Component.translatable("avaritia_singularity_stress." + state.key())));
    }

    /**
     * 可能产出列表:每项画 物品图标 + ×数量,概率低于 100% 时追加 (xx%)。
     * <p>
     * 例:极限压缩机满速奇点态 → {@code [奇点]×4 (27%)} 与 {@code [奇点]×2 (73%)}。
     */
    private static void addOutputLines(ITooltip tooltip, BlockAccessor accessor, CompoundTag data) {
        if (!data.contains("Outputs")) {
            return;
        }
        ListTag list = data.getList("Outputs", CompoundTag.TAG_COMPOUND);
        IElementHelper helper = IElementHelper.get();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack output = ItemStack.parseOptional(
                    accessor.getLevel().registryAccess(), entry.getCompound("Item"));
            if (output.isEmpty()) {
                continue;
            }
            float chance = entry.getFloat("Chance");

            tooltip.add(helper.smallItem(output));
            StringBuilder text = new StringBuilder(" ").append(output.getCount());
            if (chance < 0.999F) {
                text.append(" (").append(String.format("%.0f%%", chance * 100.0F)).append(")");
            }
            tooltip.append(helper.text(Component.literal(text.toString())));
        }
    }

    /** 把产出概率列表写入数据包(ItemStack + Chance)。 */
    private static void putOutputs(CompoundTag tag, List<OutputChance> outputs, BlockAccessor accessor) {
        if (outputs.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (OutputChance output : outputs) {
            if (output.stack().isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put("Item", output.stack().saveOptional(accessor.getLevel().registryAccess()));
            entry.putFloat("Chance", output.chance());
            list.add(entry);
        }
        if (!list.isEmpty()) {
            tag.put("Outputs", list);
        }
    }
}
