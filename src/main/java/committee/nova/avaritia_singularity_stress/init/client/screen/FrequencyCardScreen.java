package committee.nova.avaritia_singularity_stress.init.client.screen;

import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.content.wireless.frequency_card.FrequencyCardMenu;
import committee.nova.avaritia_singularity_stress.content.wireless.frequency_card.FrequencyCardPacket;
import committee.nova.avaritia_singularity_stress.init.registry.ModPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class FrequencyCardScreen extends AbstractContainerScreen<FrequencyCardMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "avaritia_singularity_stress", "textures/gui/machine/wireless_frequency_card.png");

    // ===== 布局常量（直接取自 new_gui.agent.json）=====
    private static final int GUI_W = 176;
    private static final int GUI_H = 166; // 内容到 y=148 左右，留 12 像素边距

    private static final int TITLE_X = 67, TITLE_Y = 6;
    private static final int ICON_X = 80, ICON_Y = 32;
    private static final int INPUT_X = 49, INPUT_Y = 67, INPUT_W = 75, INPUT_H = 9;
    private static final int MODE_BTN_X = 130, MODE_BTN_Y = 65, MODE_BTN_W = 22, MODE_BTN_H = 13;

    // 6 组预设：标签 / 保存按钮 / 加载按钮 的左上角坐标
    private static final int[][] PRESET_LABEL_POS = {
            {51, 90}, {115, 90},
            {51, 112}, {115, 112},
            {51, 134}, {115, 134}
    };
    private static final int[][] PRESET_SAVE_POS = {
            {62, 86}, {126, 86},
            {62, 108}, {126, 108},
            {62, 130}, {126, 130}
    };
    private static final int[][] PRESET_LOAD_POS = {
            {62, 97}, {126, 97},
            {62, 119}, {126, 119},
            {62, 141}, {126, 141}
    };
    private static final int PRESET_COUNT = 6;

    private EditBox frequencyField;
    private Button modeButton;
    private Button[] saveButtons;
    private Button[] loadButtons;

    private int knownFrequency = Integer.MIN_VALUE;
    private int knownVisibility = -1;

    public FrequencyCardScreen(FrequencyCardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        // 不渲染物品栏标题
        this.inventoryLabelX = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        // ---- 频率输入框 ----
        frequencyField = new EditBox(font,
                leftPos + INPUT_X, topPos + INPUT_Y,
                INPUT_W, INPUT_H, Component.empty());
        frequencyField.setMaxLength(4);
        frequencyField.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        frequencyField.setBordered(false);
        frequencyField.setTextColor(0xFFFFFF);
        frequencyField.setResponder(this::onFrequencyTyped);
        addRenderableWidget(frequencyField);

        // ---- 私有 / 公开 切换按钮 ----
        modeButton = Button.builder(modeLabel(menu.getVisibility()), b -> {
                    IFrequencyBindable.ChannelVisibility next =
                            menu.getVisibility() == IFrequencyBindable.ChannelVisibility.PRIVATE
                                    ? IFrequencyBindable.ChannelVisibility.PUBLIC
                                    : IFrequencyBindable.ChannelVisibility.PRIVATE;
                    ModPackets.sendToServer(new FrequencyCardPacket(
                            menu.getHand().ordinal(), FrequencyCardPacket.Action.SET_VISIBILITY,
                            next.ordinal()));
                })
                .bounds(leftPos + MODE_BTN_X, topPos + MODE_BTN_Y, MODE_BTN_W, MODE_BTN_H)
                .build();
        addRenderableWidget(modeButton);

        // ---- 6 组预设按钮 ----
        saveButtons = new Button[PRESET_COUNT];
        loadButtons = new Button[PRESET_COUNT];
        for (int i = 0; i < PRESET_COUNT; i++) {
            final int idx = i;

            saveButtons[i] = Button.builder(Component.empty(), b ->
                            ModPackets.sendToServer(new FrequencyCardPacket(
                                    menu.getHand().ordinal(),
                                    FrequencyCardPacket.Action.SAVE_PRESET, idx)))
                    .bounds(leftPos + PRESET_SAVE_POS[i][0], topPos + PRESET_SAVE_POS[i][1], 7, 7)
                    .build();
            addRenderableWidget(saveButtons[i]);

            loadButtons[i] = Button.builder(Component.empty(), b ->
                            ModPackets.sendToServer(new FrequencyCardPacket(
                                    menu.getHand().ordinal(),
                                    FrequencyCardPacket.Action.LOAD_PRESET, idx)))
                    .bounds(leftPos + PRESET_LOAD_POS[i][0], topPos + PRESET_LOAD_POS[i][1], 7, 7)
                    .build();
            addRenderableWidget(loadButtons[i]);
        }

        syncFromServer();
    }

    private static Component modeLabel(IFrequencyBindable.ChannelVisibility visibility) {
        return Component.translatable(
                "gui.avaritia_singularity_stress.visibility."
                        + visibility.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void onFrequencyTyped(String text) {
        if (text.isEmpty()) return;
        int frequency;
        try {
            frequency = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return;
        }
        if (frequency < 0 || frequency > IFrequencyBindable.MAX_FREQUENCY) return;
        if (frequency == knownFrequency) return;
        knownFrequency = frequency;
        ModPackets.sendToServer(new FrequencyCardPacket(
                menu.getHand().ordinal(), FrequencyCardPacket.Action.SET_FREQUENCY, frequency));
    }

    private void syncFromServer() {
        int frequency = menu.getFrequency();
        int visibility = menu.getVisibility().ordinal();
        if (frequency == knownFrequency && visibility == knownVisibility) return;
        knownFrequency = frequency;
        knownVisibility = visibility;

        if (frequencyField != null) {
            String text = frequency == IFrequencyBindable.NO_FREQUENCY ? "" : String.valueOf(frequency);
            if (!text.equals(frequencyField.getValue())) {
                frequencyField.setValue(text);
            }
        }
        if (modeButton != null) {
            modeButton.setMessage(modeLabel(menu.getVisibility()));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncFromServer();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    /** 只画标题，绝不渲染玩家物品栏标签。 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, TITLE_X, TITLE_Y, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不要在这里再调 renderBackground：AbstractContainerScreen.render 内部已经调过一次
        // （renderBackground = 变暗/模糊底 + renderBg 贴图），重复调用会把整块面板又压暗一层，
        // 于是同一张无线面板在频率卡里比在方块配置界面里更黑。
        super.render(graphics, mouseX, mouseY, partialTick);

        // 卡片图标（只读展示手持物）
        ItemStack held = minecraft != null && minecraft.player != null
                ? minecraft.player.getItemInHand(menu.getHand())
                : ItemStack.EMPTY;
        if (!held.isEmpty()) {
            graphics.renderItem(held, leftPos + ICON_X, topPos + ICON_Y);
        }

        // 6 组预设数字标签
        for (int i = 0; i < PRESET_COUNT; i++) {
            int[] pos = PRESET_LABEL_POS[i];
            graphics.drawString(font, String.valueOf(i + 1),
                    leftPos + pos[0], topPos + pos[1], 0xFFFFFF, false);
        }

        // 预设按钮 tooltip：保存 / 读取
        for (int i = 0; i < PRESET_COUNT; i++) {
            // 保存按钮：覆盖保存提示
            Button saveBtn = saveButtons[i];
            if (saveBtn != null && saveBtn.isHovered()) {
                Component text = Component.translatable(
                        "gui.avaritia_singularity_stress.preset.save",
                        i + 1
                ).withStyle(ChatFormatting.YELLOW);
                graphics.renderComponentTooltip(font, List.of(text), mouseX, mouseY);
            }

            // 读取按钮：显示当前频率
            Button loadBtn = loadButtons[i];
            if (loadBtn != null && loadBtn.isHovered()) {
                int presetFreq = menu.getPresetFrequency(i);
                Component text = presetFreq == IFrequencyBindable.NO_FREQUENCY
                        ? Component.translatable("gui.avaritia_singularity_stress.preset.empty")
                        .withStyle(ChatFormatting.GRAY)
                        : Component.translatable(
                        "gui.avaritia_singularity_stress.preset.load",
                        presetFreq
                ).withStyle(ChatFormatting.GREEN);
                graphics.renderComponentTooltip(font, List.of(text), mouseX, mouseY);
            }
        }

        renderTooltip(graphics, mouseX, mouseY);
    }
}