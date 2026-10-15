package committee.nova.avaritia_singularity_stress.content.wireless.frequency_card;

import java.util.List;
import java.util.UUID;

import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class WirelessFrequencyCardItem extends Item {

    private static final String KEY_FREQUENCY = "Frequency";
    private static final String KEY_VISIBILITY = "ChannelVisibility";

    private static String presetKey(int index, String suffix) {
        return "Preset" + index + "_" + suffix;
    }

    public WirelessFrequencyCardItem(Properties properties) {
        super(properties);
    }

    // ===================== 当前频率读写 =====================

    public static int getFrequency(ItemStack stack) {
        CustomDataAccess data = CustomDataAccess.of(stack);
        return data.has(KEY_FREQUENCY) ? data.getInt(KEY_FREQUENCY) : IFrequencyBindable.NO_FREQUENCY;
    }

    public static void setFrequency(ItemStack stack, int frequency) {
        int clamped = IFrequencyBindable.clampFrequency(frequency);
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(KEY_FREQUENCY, clamped));
    }

    public static IFrequencyBindable.ChannelVisibility getVisibility(ItemStack stack) {
        CustomDataAccess data = CustomDataAccess.of(stack);
        return IFrequencyBindable.ChannelVisibility.byIndex(
                data.has(KEY_VISIBILITY) ? data.getInt(KEY_VISIBILITY) : 0);
    }

    public static void setVisibility(ItemStack stack, IFrequencyBindable.ChannelVisibility visibility) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(KEY_VISIBILITY, visibility.ordinal()));
    }

    // ===================== 预设读写 =====================

    public static int getPresetFrequency(ItemStack stack, int index) {
        if (index < 0 || index >= FrequencyCardMenu.PRESET_COUNT) {
            return IFrequencyBindable.NO_FREQUENCY;
        }
        CustomDataAccess data = CustomDataAccess.of(stack);
        String key = presetKey(index, "Frequency");
        return data.has(key) ? data.getInt(key) : IFrequencyBindable.NO_FREQUENCY;
    }

    public static IFrequencyBindable.ChannelVisibility getPresetVisibility(ItemStack stack, int index) {
        if (index < 0 || index >= FrequencyCardMenu.PRESET_COUNT) {
            return IFrequencyBindable.ChannelVisibility.PRIVATE;
        }
        CustomDataAccess data = CustomDataAccess.of(stack);
        String key = presetKey(index, "Visibility");
        return IFrequencyBindable.ChannelVisibility.byIndex(data.has(key) ? data.getInt(key) : 0);
    }

    /** 把当前的频率 + 可见性存进预设槽 {@code index}。 */
    public static void savePreset(ItemStack stack, int index) {
        if (index < 0 || index >= FrequencyCardMenu.PRESET_COUNT) {
            return;
        }
        int freq = getFrequency(stack);
        int vis = getVisibility(stack).ordinal();
        String freqKey = presetKey(index, "Frequency");
        String visKey = presetKey(index, "Visibility");
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(freqKey, freq);
            tag.putInt(visKey, vis);
        });
    }

    /** 把预设槽 {@code index} 的频率 + 可见性读进当前。 */
    public static void loadPreset(ItemStack stack, int index) {
        if (index < 0 || index >= FrequencyCardMenu.PRESET_COUNT) {
            return;
        }
        int freq = getPresetFrequency(stack, index);
        int vis = getPresetVisibility(stack, index).ordinal();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(KEY_FREQUENCY, freq);
            tag.putInt(KEY_VISIBILITY, vis);
        });
    }

    // ===================== 三态右键 =====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            final InteractionHand h = hand;
            player.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> FrequencyCardMenu.server(id, inventory, stack, h),
                            Component.translatable(
                                    "item.avaritia_singularity_stress.wireless_frequency_card")),
                    // 频率/可见性/6 组预设随开界面包一起下发：客户端第一帧就是真值，
                    // 不会再出现"先 0/空、闪一下才显示频率"（见 FrequencyCardMenu.writeOpenData）
                    buf -> FrequencyCardMenu.writeOpenData(buf, stack, h));
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        if (!(blockEntity instanceof IFrequencyBindable bindable)) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player == null) {
            return InteractionResult.PASS;
        }

        ItemStack card = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(readout(bindable), true);
            return InteractionResult.SUCCESS;
        }

        if (!bindable.hasFrequency()) {
            player.displayClientMessage(
                    Component.translatable(
                                    "item.avaritia_singularity_stress.wireless_frequency_card.unset")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.SUCCESS;
        }
        applyTo(bindable, card);
        player.displayClientMessage(
                Component.translatable(
                        "item.avaritia_singularity_stress.wireless_frequency_card.write",
                        bindable.getFrequency()),
                true);
        return InteractionResult.SUCCESS;
    }

    private static void applyTo(IFrequencyBindable bindable, ItemStack card) {
        bindable.setFrequency(getFrequency(card));
        bindable.setVisibility(getVisibility(card));
        if (bindable instanceof BlockEntity be) {
            be.setChanged();
            if (be instanceof SyncedBlockEntity synced) {
                synced.sendData();
            }
        }
    }

    private static Component readout(IFrequencyBindable bindable) {
        if (!bindable.hasFrequency()) {
            return Component.translatable(
                            "item.avaritia_singularity_stress.wireless_frequency_card.read_none")
                    .withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable(
                "item.avaritia_singularity_stress.wireless_frequency_card.read",
                bindable.getFrequency(),
                Component.translatable(visibilityKey(bindable.getVisibility())));
    }

    private static String visibilityKey(IFrequencyBindable.ChannelVisibility visibility) {
        return "item.avaritia_singularity_stress.wireless_frequency_card.visibility."
                + visibility.name().toLowerCase(java.util.Locale.ROOT);
    }

    // ===================== Tooltip =====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        int frequency = getFrequency(stack);
        tooltip.add(Component.translatable(
                        "item.avaritia_singularity_stress.wireless_frequency_card.tooltip.frequency",
                        frequency == IFrequencyBindable.NO_FREQUENCY
                                ? Component.translatable(
                                "item.avaritia_singularity_stress.wireless_frequency_card.unset")
                                : Component.literal(String.valueOf(frequency)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(visibilityKey(getVisibility(stack)))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
                        "item.avaritia_singularity_stress.wireless_frequency_card.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private record CustomDataAccess(CompoundTag tag) {

        static CustomDataAccess of(ItemStack stack) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            return new CustomDataAccess(data == null ? new CompoundTag() : data.copyTag());
        }

        boolean has(String key) {
            return tag.contains(key);
        }

        int getInt(String key) {
            return tag.getInt(key);
        }
    }
}