package committee.nova.avaritia_singularity_stress.init.client.screen;

import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.content.wireless.IWirelessConfigHolder;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigMenu;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigPacket;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessGuiKind;
import committee.nova.avaritia_singularity_stress.init.registry.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * B9 / B10 / B11 共用的无线方块配置界面。
 * <p>
 * 几何对齐专用面板贴图 wireless_frequency_block.png（256×256 画布，面板位于左上 176×166）：
 * 青色标题带 x6..169 / y6..13（标题居中其上）、频率输入凹槽灰底 x47..124 / y67..75
 * （正好 9 像素高）、私有/公用按钮 (139,65) 22×13 与凹槽垂直居中，
 * 且输入框与频率卡一样<b>无边框 + 白字</b>，直接坐在贴图自带的凹槽里。
 * <p>
 * 与频率卡的三点不同：
 * <ul>
 *   <li>数值来源是<b>客户端方块实体</b>（{@link WirelessConfigMenu#holder}），
 *       不是 ContainerData —— 开界面首帧就是真值，不会"闪一下"才显示频率；</li>
 *   <li>不渲染"物品栏"标签，也不显示任何槽位（频率卡的布局里没有槽位图案）；</li>
 *   <li>B10 额外显示"数量"字段，放在贴图底部预留的空白带（预设图案下方 y≥148）。
 *       输出端只填送出上限；接收端是接收上限 + 优先级。
 *       字段布局随设备种类变化，种类在 {@code init()} 时从客户端 BE 读一次。</li>
 * </ul>
 * 控件值变化 → 立即发 {@link WirelessConfigPacket}；服务端写回 BE 并 {@code sendData()}，
 * 客户端在 {@code containerTick} 里把 BE 的值回填到控件。
 */
public class WirelessConfigScreen extends AbstractContainerScreen<WirelessConfigMenu> {

    /** 无线方块专用面板贴图（256×256 画布，面板位于左上 176×166）。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "avaritia_singularity_stress", "textures/gui/machine/wireless_frequency_block.png");

    private static final int GUI_W = 176;
    private static final int GUI_H = 166;

    // ---- 标题：青色标题带（贴图 x6..169 / y6..13），水平居中于带子 ----
    private static final int TITLE_CX = 88;
    private static final int TITLE_Y = 6;

    // ---- 频率输入：贴图凹槽灰底 x47..124 / y67..75（正好 9 像素高），无边框白字坐进去 ----
    private static final int FREQ_X = 49;
    private static final int FREQ_Y = 67;
    private static final int FREQ_W = 75;
    private static final int FREQ_H = 9;

    // ---- 私有/公用按钮：与凹槽（y63..79）垂直居中，水平居中于凹槽右侧的空档（x128..172）----
    private static final int MODE_X = 139;
    private static final int MODE_Y = 65;
    private static final int MODE_W = 22;
    private static final int MODE_H = 13;

    // ---- B10 专用的"数量"字段：贴图底部空白带（y146..163）----
    // 输出端单组（标签+输入框）整体居中；接收端两组平铺，
    // 间距按中文标签字宽排开（"接收/送出上限"≈36px、"优先级"≈27px）。
    private static final int AMOUNT_Y = 150;
    private static final int AMOUNT_W = 44;
    private static final int AMOUNT_H = 12;
    private static final int AMOUNT_LABEL_Y = 152;
    private static final int LABEL2_X = 98;
    private static final int FIELD2_X = 128;

    /** 数量第一组的标签 x：输出端单组居中 (46)，接收端靠左 (8)。 */
    private int label1X() {
        return outputSide ? 46 : 8;
    }

    /** 数量第一组的输入框 x：与标签保持约 4 像素间距。 */
    private int field1X() {
        return outputSide ? 86 : 48;
    }

    /** 数量上限的最大输入长度：9 位（999,999,999，保证 parseInt 不溢出）。 */
    private static final int AMOUNT_MAX_LENGTH = 9;
    private static final int AMOUNT_MAX_VALUE = 999_999_999;

    @Nullable
    private EditBox frequencyField;
    @Nullable
    private EditBox amount1Field;
    @Nullable
    private EditBox amount2Field;
    @Nullable
    private Button visibilityButton;

    /** 布局依据：init() 时读一次（客户端 BE 就在场，之后不再变）。 */
    private WirelessGuiKind kind = WirelessGuiKind.ITEM_FUNNEL;
    private boolean outputSide;

    /** 当前频道可见性（按钮与提交都用它，替代原来的 menu.getVisibility()）。 */
    private IFrequencyBindable.ChannelVisibility visibility = IFrequencyBindable.ChannelVisibility.PRIVATE;

    /** 回填控件时置位，避免 setValue 触发 responder 又发一遍包。 */
    private boolean syncing;

    /**
     * 上次已知的值（= 服务端/客户端 BE 的值，或玩家刚敲进去的值）。
     * 用于两件事：① 回填时不覆盖玩家正在编辑的文本；② 隐藏字段（例如输出端的
     * 优先级框）提交时原样带回，不会被清成 0。
     */
    private int knownFrequency = Integer.MIN_VALUE;
    private int knownAmount1;
    private int knownAmount2;
    private int knownVisibility = -1;

    public WirelessConfigScreen(WirelessConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        // 不渲染物品栏标题（本界面也没有物品栏）
        this.inventoryLabelX = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        IWirelessConfigHolder holder = holder();
        if (holder != null) {
            kind = holder.guiKind();
            outputSide = holder.guiOutputSide();
        }

        frequencyField = numericField(FREQ_X, FREQ_Y, FREQ_W, FREQ_H, 4, false);
        if (kind.hasAmounts()) {
            amount1Field = numericField(field1X(), AMOUNT_Y, AMOUNT_W, AMOUNT_H, AMOUNT_MAX_LENGTH, true);
            if (!outputSide) {
                amount2Field = numericField(FIELD2_X, AMOUNT_Y, AMOUNT_W, AMOUNT_H, AMOUNT_MAX_LENGTH, true);
            }
        }

        visibilityButton = Button.builder(visibilityLabel(visibility), b ->
                        submit(submitFrequency(), nextVisibility().ordinal(), submitAmount1(), submitAmount2()))
                .bounds(leftPos + MODE_X, topPos + MODE_Y, MODE_W, MODE_H)
                .build();
        addRenderableWidget(visibilityButton);

        // BE 已在客户端：这里就能读到真值，界面第一帧即为正确内容
        syncFromHolder();
    }

    /**
     * 造一个只接受数字的输入框。
     *
     * @param bordered true = 保留原版边框与底色（字段下方没有贴图凹槽时用），
     *                 false = 无边框白字（坐在贴图自带的凹槽里，与频率卡一致）
     */
    private EditBox numericField(int x, int y, int w, int h, int maxLength, boolean bordered) {
        EditBox box = new EditBox(font, leftPos + x, topPos + y, w, h, Component.empty());
        box.setMaxLength(maxLength);
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        if (!bordered) {
            box.setBordered(false);
            box.setTextColor(0xFFFFFF);
        }
        box.setResponder(text -> onFieldChanged());
        addRenderableWidget(box);
        return box;
    }

    /** 任一输入框变化 → 汇总当前草稿值发给服务端。 */
    private void onFieldChanged() {
        if (syncing) {
            return;
        }
        submit(submitFrequency(), visibility.ordinal(), submitAmount1(), submitAmount2());
    }

    private int submitFrequency() {
        return readField(frequencyField, IFrequencyBindable.NO_FREQUENCY, IFrequencyBindable.MAX_FREQUENCY);
    }

    /** 隐藏时不显示但也不能丢：原样带回上次已知值。 */
    private int submitAmount1() {
        return amount1Field == null ? Math.max(0, knownAmount1)
                : readField(amount1Field, 0, AMOUNT_MAX_VALUE);
    }

    private int submitAmount2() {
        return amount2Field == null ? Math.max(0, knownAmount2)
                : readField(amount2Field, 0, AMOUNT_MAX_VALUE);
    }

    private IFrequencyBindable.ChannelVisibility nextVisibility() {
        return visibility == IFrequencyBindable.ChannelVisibility.PRIVATE
                ? IFrequencyBindable.ChannelVisibility.PUBLIC
                : IFrequencyBindable.ChannelVisibility.PRIVATE;
    }

    /** 解析输入框；空串按空值处理，越界按边界 clamp。 */
    private static int readField(@Nullable EditBox box, int emptyValue, int max) {
        if (box == null) {
            return emptyValue;
        }
        String text = box.getValue();
        if (text.isEmpty()) {
            return emptyValue;
        }
        try {
            return Math.min(Integer.parseInt(text), max);
        } catch (NumberFormatException e) {
            return emptyValue;
        }
    }

    /** 发给服务端。与上次已知值完全相同则不发，避免打字过程中刷包。 */
    private void submit(int frequency, int visibilityOrdinal, int amount1, int amount2) {
        if (frequency == knownFrequency && visibilityOrdinal == knownVisibility
                && amount1 == knownAmount1 && amount2 == knownAmount2) {
            return;
        }
        knownFrequency = frequency;
        knownVisibility = visibilityOrdinal;
        knownAmount1 = amount1;
        knownAmount2 = amount2;
        visibility = IFrequencyBindable.ChannelVisibility.byIndex(visibilityOrdinal);
        if (visibilityButton != null) {
            visibilityButton.setMessage(visibilityLabel(visibility));
        }
        ModPackets.sendToServer(new WirelessConfigPacket(menu.getTargetPos(), frequency, visibilityOrdinal,
                amount1, amount2));
    }

    /** 客户端 BE；取不到（区块卸载等）返回 null。 */
    @Nullable
    private IWirelessConfigHolder holder() {
        return menu.holder(minecraft == null ? null : minecraft.level);
    }

    /** 把客户端 BE 的最新值回填控件；BE 暂时读不到就保持现状。 */
    private void syncFromHolder() {
        IWirelessConfigHolder holder = holder();
        if (holder == null) {
            return;
        }
        int frequency = holder.getFrequency();
        int amount1 = holder.getPrimaryAmount();
        int amount2 = holder.getSecondaryAmount();
        IFrequencyBindable.ChannelVisibility channelVisibility = holder.getVisibility();

        syncing = true;
        try {
            if (frequency != knownFrequency) {
                knownFrequency = frequency;
                if (frequencyField != null) {
                    frequencyField.setValue(frequency == IFrequencyBindable.NO_FREQUENCY
                            ? "" : String.valueOf(frequency));
                }
            }
            if (amount1 != knownAmount1) {
                knownAmount1 = amount1;
                if (amount1Field != null) {
                    amount1Field.setValue(amount1 == 0 ? "" : String.valueOf(amount1));
                }
            }
            if (amount2 != knownAmount2) {
                knownAmount2 = amount2;
                if (amount2Field != null) {
                    amount2Field.setValue(amount2 == 0 ? "" : String.valueOf(amount2));
                }
            }
            if (channelVisibility.ordinal() != knownVisibility) {
                knownVisibility = channelVisibility.ordinal();
                visibility = channelVisibility;
                if (visibilityButton != null) {
                    visibilityButton.setMessage(visibilityLabel(channelVisibility));
                }
            }
        } finally {
            syncing = false;
        }
    }

    private static Component visibilityLabel(IFrequencyBindable.ChannelVisibility visibility) {
        return Component.translatable("gui.avaritia_singularity_stress.visibility."
                + visibility.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncFromHolder();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    /** 标题居中画在贴图的青色标题带上；B10 额外画数量字段的标签。 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, TITLE_CX - font.width(title) / 2, TITLE_Y, 0xFFFFFF, false);

        if (!kind.hasAmounts()) {
            return;
        }
        graphics.drawString(font, label(outputSide
                        ? "gui.avaritia_singularity_stress.wireless.send_limit"
                        : "gui.avaritia_singularity_stress.wireless.receive_limit"),
                label1X(), AMOUNT_LABEL_Y, 0xFFFFFF, false);
        if (!outputSide) {
            graphics.drawString(font, label("gui.avaritia_singularity_stress.wireless.priority"),
                    LABEL2_X, AMOUNT_LABEL_Y, 0xFFFFFF, false);
        }
    }

    private static Component label(String key) {
        return Component.translatable(key);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 注意：不要再手动调 renderBackground —— AbstractContainerScreen.render 内部已经调过一次
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
