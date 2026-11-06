package committee.nova.avaritia_singularity_stress.init.client.screen;

import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.content.wireless.IWirelessConfigHolder;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigMenu;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigPacket;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessGuiKind;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessStatusView;
import committee.nova.avaritia_singularity_stress.content.wireless.frequency_card.WirelessFrequencyCardItem;
import committee.nova.avaritia_singularity_stress.init.registry.ModItems;
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
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * B9 / B10 / B11 / B15 共用的无线方块配置界面。
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
 *   <li>B10 额外显示"数量"字段，放在贴图底部预留的空白带（y≥148）。</li>
 * </ul>
 * 控件值变化 → 立即发 {@link WirelessConfigPacket}；服务端写回 BE 并 {@code sendData()}，
 * 客户端在 {@code containerTick} 里把 BE 的值回填到控件。
 * <p>
 * <b>B20（本次）新增两件事</b>：
 * <ol>
 *   <li><b>运行状态区</b>（y19..57，贴图这块本来就是空白斜纹）：把 BE 早就同步到客户端、
 *       却一直没人读的状态 / 桥流量 / 本网产能·消耗·空闲 / 同频道台数画出来。
 *       玩家"改了频率还是不传"时，终于能在界面里直接看到原因，
 *       不必再靠护目镜去猜（状态文案与护目镜共用同一套 lang 键）。</li>
 *   <li><b>「套用频率卡」按钮</b>（y86）：输出端/接收端成对配置时，
 *       不必把同一串频率手敲两遍 —— 手里拿着频率卡点一下即可。</li>
 * </ol>
 * 另外按 R2 把「优先级」输入框整体雪藏（该字段当前不参与任何计算，
 * 见 {@code WirelessStressBridgeBlockEntity.secondaryAmount} 的注释）。
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

    // ---- B20 运行状态区：标题带下沿到频率凹槽之间（贴图此处为空白的深色斜纹）----
    // 4 行 × 10px 行距，从 y19 到 y57（最后一行文字底 57+8=65），
    // 正好停在频率凹槽 y67 上方，不会压到输入框。
    private static final int STATUS_X = 8;
    private static final int STATUS_Y = 19;
    private static final int STATUS_LINE = 10;

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

    // ---- B20 「套用频率卡」按钮：频率凹槽与数量字段之间（y85..101 是空白）----
    private static final int APPLY_X = 8;
    private static final int APPLY_Y = 86;
    private static final int APPLY_W = 78;
    private static final int APPLY_H = 14;

    // ---- B9「外部输出」复选框：与「套用频率卡」同一行（y85..101），靠右摆放 ----
    private static final int EXT_X = 90;
    private static final int EXT_Y = 86;
    private static final int EXT_W = 78;
    private static final int EXT_H = 14;

    // ---- B10 专用的"数量"字段：贴图底部空白带（y146..163）----
    // [B20 雪藏] 原来接收端是"接收上限 + 优先级"两组，优先级雪藏后只剩一组，
    // 于是两端的单组统一按同一套 x 摆放（居中偏左），不再分叉。
    private static final int AMOUNT_Y = 150;
    private static final int AMOUNT_W = 44;
    private static final int AMOUNT_H = 12;
    private static final int AMOUNT_LABEL_Y = 152;
    private static final int LABEL1_X = 46;
    private static final int FIELD1_X = 86;

    /** 数量上限的最大输入长度：9 位（999,999,999，保证 parseInt 不溢出）。 */
    private static final int AMOUNT_MAX_LENGTH = 9;
    private static final int AMOUNT_MAX_VALUE = 999_999_999;

    @Nullable
    private EditBox frequencyField;
    @Nullable
    private EditBox amount1Field;
    @Nullable
    private Button visibilityButton;
    @Nullable
    private Button externalOutputButton;

    /** 布局依据：init() 时读一次（客户端 BE 就在场，之后不再变）。 */
    private WirelessGuiKind kind = WirelessGuiKind.ITEM_FUNNEL;
    private boolean outputSide;

    /** 当前频道可见性（按钮与提交都用它，替代原来的 menu.getVisibility()）。 */
    private IFrequencyBindable.ChannelVisibility visibility = IFrequencyBindable.ChannelVisibility.PRIVATE;

    /** B20：服务端算好、随方块实体同步下来的运行状态；null = 该设备不画状态区。 */
    @Nullable
    private WirelessStatusView statusView;

    /** 回填控件时置位，避免 setValue 触发 responder 又发一遍包。 */
    private boolean syncing;

    /**
     * 上次已知的值（= 服务端/客户端 BE 的值，或玩家刚敲进去的值）。
     * 用于两件事：① 回填时不覆盖玩家正在编辑的文本；② 隐藏字段（例如输出端
     * 不显示的字段）提交时原样带回，不会被清成 0。
     */
    private int knownFrequency = Integer.MIN_VALUE;
    private int knownAmount1;
    private int knownAmount2;
    private int knownVisibility = -1;
    /** B9「外部输出」当前值（复选框与提交共用；默认关闭）。 */
    private boolean knownExternalOutput;

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
            amount1Field = numericField(FIELD1_X, AMOUNT_Y, AMOUNT_W, AMOUNT_H, AMOUNT_MAX_LENGTH, true);
            // [B20 雪藏] 接收端的第二格（优先级）：该字段不参与任何计算，
            // 留着只会让玩家以为设了有用。恢复时把下面三行原样放回。
            // if (!outputSide) {
            //     amount2Field = numericField(FIELD2_X, AMOUNT_Y, AMOUNT_W, AMOUNT_H, AMOUNT_MAX_LENGTH, true);
            // }
        }

        visibilityButton = new SpriteButton(font, leftPos + MODE_X, topPos + MODE_Y, MODE_W, MODE_H,
                visibilityLabel(visibility), b ->
                        submit(submitFrequency(), nextVisibility().ordinal(), submitAmount1(), submitAmount2(),
                                knownExternalOutput));
        addRenderableWidget(visibilityButton);

        addRenderableWidget(new SpriteButton(font, leftPos + APPLY_X, topPos + APPLY_Y, APPLY_W, APPLY_H,
                Component.translatable("gui.avaritia_singularity_stress.wireless.apply_card"),
                b -> applyFromCard()));

        // B9 专用：外部输出开关（默认关闭）。只有无线物品漏斗有这个语义，
        // 所以按 guiKind 判断，B10/B15 的界面上不出现这个按钮。
        // 勾选框用本模组统一精灵图（check_box_off/on）；状态每帧从 knownExternalOutput 读，
        // 服务端同步回来后界面自动跟着变。
        if (kind == WirelessGuiKind.ITEM_FUNNEL) {
            externalOutputButton = new SpriteCheckBox(font, leftPos + EXT_X, topPos + EXT_Y, EXT_W, EXT_H,
                    () -> knownExternalOutput,
                    Component.translatable("gui.avaritia_singularity_stress.wireless.external_output"),
                    b -> toggleExternalOutput());
            addRenderableWidget(externalOutputButton);
        }

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
        submit(submitFrequency(), visibility.ordinal(), submitAmount1(), submitAmount2(), knownExternalOutput);
    }

    private int submitFrequency() {
        return readField(frequencyField, IFrequencyBindable.NO_FREQUENCY, IFrequencyBindable.MAX_FREQUENCY);
    }

    /** 隐藏时不显示但也不能丢：原样带回上次已知值。 */
    private int submitAmount1() {
        return amount1Field == null ? Math.max(0, knownAmount1)
                : readField(amount1Field, 0, AMOUNT_MAX_VALUE);
    }

    /**
     * [B20 雪藏] 第二格（优先级）。
     * <p>
     * 界面不再提供该输入框，但提交时仍要把**上次已知值**带回去 ——
     * 否则玩家一改频率，服务端就会把它清成 0（隐藏字段被"顺手重置"是经典坑）。
     */
    private int submitAmount2() {
        return Math.max(0, knownAmount2);
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
    private void submit(int frequency, int visibilityOrdinal, int amount1, int amount2, boolean externalOutput) {
        if (frequency == knownFrequency && visibilityOrdinal == knownVisibility
                && amount1 == knownAmount1 && amount2 == knownAmount2
                && externalOutput == knownExternalOutput) {
            return;
        }
        knownFrequency = frequency;
        knownVisibility = visibilityOrdinal;
        knownAmount1 = amount1;
        knownAmount2 = amount2;
        knownExternalOutput = externalOutput;
        visibility = IFrequencyBindable.ChannelVisibility.byIndex(visibilityOrdinal);
        if (visibilityButton != null) {
            visibilityButton.setMessage(visibilityLabel(visibility));
        }
        // 复选框自己按 knownExternalOutput 画 check_box_on/off，这里不必再改文案
        ModPackets.sendToServer(new WirelessConfigPacket(menu.getTargetPos(), frequency, visibilityOrdinal,
                amount1, amount2, externalOutput));
    }

    /** B9「外部输出」：翻转并立即提交（其它字段保持不变）。 */
    private void toggleExternalOutput() {
        submit(submitFrequency(), visibility.ordinal(), submitAmount1(), submitAmount2(),
                !knownExternalOutput);
    }

    /**
     * B20：把主手频率卡上的频道一键写进本方块。
     * <p>
     * <b>为什么在客户端算、不新开包</b>：频率卡的 NBT 客户端读得到
     * （{@code WirelessFrequencyCardItem.getFrequency} 读的是 {@code CUSTOM_DATA}），
     * 而写方块本来就有现成的 {@link WirelessConfigPacket} 上行通道。
     * 多一条包只会多一处要维护的校验。
     * <p>
     * 全部失败分支都给 action bar 提示 —— 静默无反应是最差的手感。
     */
    private void applyFromCard() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!held.is(ModItems.WIRELESS_FREQUENCY_CARD.get())) {
            minecraft.player.displayClientMessage(Component
                    .translatable("gui.avaritia_singularity_stress.wireless.apply_card.no_card")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        int frequency = WirelessFrequencyCardItem.getFrequency(held);
        if (frequency == IFrequencyBindable.NO_FREQUENCY) {
            minecraft.player.displayClientMessage(Component
                    .translatable("gui.avaritia_singularity_stress.wireless.apply_card.unset")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        IFrequencyBindable.ChannelVisibility cardVisibility = WirelessFrequencyCardItem.getVisibility(held);
        // 先把输入框刷成新值（置 syncing 免得 setValue 触发 responder 抢先发包），再统一提交
        syncing = true;
        try {
            if (frequencyField != null) {
                frequencyField.setValue(String.valueOf(frequency));
            }
        } finally {
            syncing = false;
        }
        submit(frequency, cardVisibility.ordinal(), submitAmount1(), submitAmount2(), knownExternalOutput);
        minecraft.player.displayClientMessage(Component
                .translatable("gui.avaritia_singularity_stress.wireless.apply_card.done", frequency)
                .withStyle(ChatFormatting.GREEN), true);
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
        boolean externalOutput = holder.isExternalOutput();
        // B20：状态区同样每 tick 从客户端 BE 取一次（值由服务端 sendData 推下来）
        statusView = holder.statusView();

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
                // [B20 雪藏] 第二格输入框已雪藏，值只记进已知值，供提交时原样带回
            }
            if (channelVisibility.ordinal() != knownVisibility) {
                knownVisibility = channelVisibility.ordinal();
                visibility = channelVisibility;
                if (visibilityButton != null) {
                    visibilityButton.setMessage(visibilityLabel(channelVisibility));
                }
            }
            if (externalOutput != knownExternalOutput) {
                // 只更新状态位；勾选框在 renderWidget 里按它选 check_box_on/off
                knownExternalOutput = externalOutput;
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

        renderStatus(graphics);

        if (!kind.hasAmounts()) {
            return;
        }
        graphics.drawString(font, label(outputSide
                        ? "gui.avaritia_singularity_stress.wireless.send_limit"
                        : "gui.avaritia_singularity_stress.wireless.receive_limit"),
                LABEL1_X, AMOUNT_LABEL_Y, 0xFFFFFF, false);
        // [B20 雪藏] 接收端的"优先级"标签（与上面雪藏的输入框成对）：
        // if (!outputSide) {
        //     graphics.drawString(font, label("gui.avaritia_singularity_stress.wireless.priority"),
        //             LABEL2_X, AMOUNT_LABEL_Y, 0xFFFFFF, false);
        // }
    }

    /**
     * B20：运行状态区（4 行）。
     * <p>
     * 只在设备提供了 {@link WirelessStatusView} 时画（当前只有 B10）。
     * B9 漏斗 / B15 隧道拿到的是 null，界面与改动前完全一致。
     */
    private void renderStatus(GuiGraphics graphics) {
        WirelessStatusView view = statusView;
        if (view == null) {
            return;
        }
        int y = STATUS_Y;

        graphics.drawString(font, Component.translatable(
                        "gui.avaritia_singularity_stress.wireless.status_line",
                        // 【实机修正 2026-11-05】原来这里少写了 MODID 前缀，界面直接把
                        // "wireless.status.unbound" 当原文画出来；lang 里的真键是
                        // avaritia_singularity_stress.wireless.status.<key>
                        Component.translatable(
                                committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod.MODID
                                        + ".wireless.status." + view.statusKey())),
                STATUS_X, y, statusColor(view), false);
        y += STATUS_LINE;

        graphics.drawString(font, Component.translatable(
                        "gui.avaritia_singularity_stress.wireless.bridge_flow",
                        su(view.bridgeSu())),
                STATUS_X, y, 0x55FFFF, false);
        y += STATUS_LINE;

        graphics.drawString(font, Component.translatable(
                        "gui.avaritia_singularity_stress.wireless.net_line",
                        su(view.capacity()), su(view.stress()), su(view.idle())),
                STATUS_X, y, 0xAAAAAA, false);
        y += STATUS_LINE;

        renderPeerLine(graphics, view, y);
    }

    /**
     * 第 4 行：拓扑。
     * <p>
     * <b>B23 改了什么</b>：B20 时这里是「候选 ≥2 = 歧义、已停机」的红字（一对一语义）。
     * 现在并流/分流都合法了，所以改为如实报拓扑：输出端看「对端几个接收端」，
     * 接收端看「同频道几个输出端」。
     * <p>
     * 仍然是 0 台时最要紧 —— 那才是「频率改了却不传」的常见原因（频率对不上 / 可见性公私不混 /
     * 超出距离 / 私有频道 owner 不同）。
     */
    private void renderPeerLine(GuiGraphics graphics, WirelessStatusView view, int y) {
        int peers = view.peerCandidates();
        String range = String.valueOf(Math.round(view.range()));
        Component text;
        int color;
        if (peers < 0) {
            text = Component.translatable("gui.avaritia_singularity_stress.wireless.peers_pending");
            color = 0xAAAAAA;
        } else if (peers == 0) {
            text = Component.translatable("gui.avaritia_singularity_stress.wireless.peers_none", range);
            color = 0xFFAA00;
        } else {
            text = Component.translatable(outputSide
                            ? "gui.avaritia_singularity_stress.wireless.peers_targets"
                            : "gui.avaritia_singularity_stress.wireless.peers_sources",
                    String.valueOf(peers), range);
            color = 0x55FF55;
        }
        graphics.drawString(font, text, STATUS_X, y, color, false);
    }

    /** 与护目镜的 statusColor（{@code WirelessStressBridgeBlockEntity.java}）保持一致。 */
    private static int statusColor(WirelessStatusView view) {
        return switch (view.statusKey()) {
            case "sending", "receiving" -> 0x55FF55;
            case "unbound" -> 0xFF5555;
            case WirelessStatusView.PENDING -> 0xAAAAAA;
            default -> 0xFFAA00;
        };
    }

    /**
     * 把 SU 缩成 4~6 个字符，免得四个数字把 176px 宽的面板撑爆
     * （护目镜用的是 Create 的 {@code LangNumberFormat}，它带逗号分隔，会超宽）。
     */
    private static String su(float value) {
        float abs = Math.abs(value);
        if (abs >= 1_000_000F) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000F);
        }
        if (abs >= 1_000F) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000F);
        }
        if (abs >= 10F) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
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
