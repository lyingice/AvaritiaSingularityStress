package committee.nova.avaritia_singularity_stress.init.client.screen;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel.WarpTunnelBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel.WarpTunnelConfigMenu;
import committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel.WarpTunnelConfigPacket;
import committee.nova.avaritia_singularity_stress.init.registry.ModPackets;

/**
 * B15 空间折跃隧道的配置界面：四面频率 + 出口过滤。
 * <p>
 * <b>与 B9/B10 的无线配置界面共用同一张面板贴图与排版语言</b>
 * （{@code textures/gui/machine/wireless_frequency_block.png}：256×256 画布、面板占左上 176×166、
 * 青色标题带在 y6..13、下方是深色斜纹空白区）。之前这里是自己 {@code graphics.fill} 画一块浅灰面板，
 * 和模组其它界面并排看明显不是一套东西，故本次统一。
 * <p>
 * 数据全部来自<b>客户端方块实体</b>（{@link WarpTunnelConfigMenu#holder}），
 * 所以开界面首帧就是真值；改值走 {@link WarpTunnelConfigPacket} 上行，服务端写回 BE 后再同步回来。
 * <p>
 * <b>没有软帘门的面（这一面没有传送带进出）一律灰显 + 按钮禁用</b>：
 * 那种面在游戏里本来就不该配对，允许它改频率只会让玩家困惑；
 * 服务端 {@code WarpTunnelConfigPacket} 里也有同一道 {@code hasBeltOn} 校验，两边不会分叉。
 */
public class WarpTunnelConfigScreen extends AbstractContainerScreen<WarpTunnelConfigMenu> {

    /** 与 B9/B10 配置界面同一张面板贴图（统一风格的关键）。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "avaritia_singularity_stress", "textures/gui/machine/wireless_frequency_block.png");

    private static final int GUI_W = 176;
    private static final int GUI_H = 166;

    /** 标题居中于青色标题带（x6..169 / y6..13），与 WirelessConfigScreen 同一组常量。 */
    private static final int TITLE_CX = 88;
    private static final int TITLE_Y = 6;

    private static final Direction[] FACES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * 四个面各占一行，行距 11px，从 y18 排到 y51。
     * 这段正好是贴图上青色带下方那块深色斜纹空白（B20 状态区用的也是这条带子的 y19 起算），
     * 四行文字 + 10px 高的小按钮刚好停在 y61，不会压到 y67 的频率凹槽。
     */
    private static final int ROW_Y = 18;
    private static final int ROW_PITCH = 11;
    private static final int LABEL_X = 8;
    private static final int MINUS_X = 116;
    private static final int PLUS_X = 140;
    private static final int STEP_BTN_W = 18;
    private static final int STEP_BTN_H = 10;

    /** 出口过滤开关：坐在频率凹槽下方的空白带，与 B9「套用频率卡」同一行位置。 */
    private static final int EXPORT_X = 8;
    private static final int EXPORT_Y = 86;
    private static final int EXPORT_W = 78;
    private static final int EXPORT_H = 14;

    /** 频率按钮的步长：一次 1 太慢，10 更顺手。 */
    private static final int STEP = 10;

    /** 每个面一对 -/+ 按钮，用于按 hasBeltOn 刷新可用状态。 */
    private final Button[] minusButtons = new Button[FACES.length];
    private final Button[] plusButtons = new Button[FACES.length];

    @Nullable
    private Button exportButton;

    public WarpTunnelConfigScreen(WarpTunnelConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        // 本界面没有物品栏区：把标签挪出可视范围（与 WirelessConfigScreen 一致）
        this.inventoryLabelX = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < FACES.length; i++) {
            Direction face = FACES[i];
            int rowY = topPos + ROW_Y + i * ROW_PITCH;
            // ±也换成统一贴图按钮：四行只有 11px 行距，精灵图按 10px 高压缩绘制
            minusButtons[i] = addRenderableWidget(
                    new SpriteButton(font, leftPos + MINUS_X, rowY, STEP_BTN_W, STEP_BTN_H,
                            Component.literal("-"), b -> adjust(face, -STEP)));
            plusButtons[i] = addRenderableWidget(
                    new SpriteButton(font, leftPos + PLUS_X, rowY, STEP_BTN_W, STEP_BTN_H,
                            Component.literal("+"), b -> adjust(face, STEP)));
        }
        exportButton = addRenderableWidget(
                new SpriteCheckBox(font, leftPos + EXPORT_X, topPos + EXPORT_Y, EXPORT_W, EXPORT_H,
                        this::isExportFilterOn,
                        Component.translatable("block.avaritia_singularity_stress.warp_tunnel.config.export_filter"),
                        b -> toggleExport()));
        refreshWidgetState();
    }

    /** 出口过滤当前状态：每帧由复选框读取，服务端同步回来会自动变。 */
    private boolean isExportFilterOn() {
        WarpTunnelBlockEntity be = tunnel();
        return be != null && be.isExportFilter();
    }

    @Nullable
    private WarpTunnelBlockEntity tunnel() {
        return minecraft == null || minecraft.level == null ? null : menu.holder(minecraft.level);
    }

    private void adjust(Direction face, int delta) {
        WarpTunnelBlockEntity be = tunnel();
        if (be == null || !be.hasBeltOn(face)) {
            return;
        }
        int current = be.getFaceFrequency(face);
        int base = current < 0 ? 0 : current;
        int next = Math.max(0, Math.min(WarpTunnelBlockEntity.MAX_FACE_FREQUENCY, base + delta));
        ModPackets.sendToServer(new WarpTunnelConfigPacket(
                menu.targetPos(), face.get3DDataValue(), WarpTunnelConfigPacket.KIND_FREQUENCY, next));
    }

    private void toggleExport() {
        WarpTunnelBlockEntity be = tunnel();
        if (be == null) {
            return;
        }
        ModPackets.sendToServer(new WarpTunnelConfigPacket(
                menu.targetPos(), 0, WarpTunnelConfigPacket.KIND_EXPORT_FILTER, be.isExportFilter() ? 0 : 1));
    }

    /**
     * 按当前 BE 状态刷新按钮可用性。
     * <p>
     * 不能只在 {@link #init()} 里做一次：隧道形状（STRAIGHT/T/CROSS）由邻居带子决定，
     * 玩家在界面开着时改旁边的带子，hasBeltOn 会变，按钮必须跟着灰/亮。
     */
    private void refreshWidgetState() {
        WarpTunnelBlockEntity be = tunnel();
        for (int i = 0; i < FACES.length; i++) {
            boolean hasBelt = be != null && be.hasBeltOn(FACES[i]);
            if (minusButtons[i] != null) {
                minusButtons[i].active = hasBelt;
            }
            if (plusButtons[i] != null) {
                plusButtons[i].active = hasBelt;
            }
        }
        // 出口过滤是 SpriteCheckBox，状态由 isExportFilterOn() 每帧读取，这里不用改文案
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshWidgetState();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 统一风格：整块面板来自贴图，不再自绘灰底
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        WarpTunnelBlockEntity be = tunnel();

        // 标题居中画在青色标题带上，白字（与 B9/B10 界面一致）
        graphics.drawString(font, title, TITLE_CX - font.width(title) / 2, TITLE_Y, 0xFFFFFF, false);

        for (int i = 0; i < FACES.length; i++) {
            Direction face = FACES[i];
            int y = ROW_Y + i * ROW_PITCH;
            boolean hasBelt = be != null && be.hasBeltOn(face);
            int freq = be == null ? WarpTunnelBlockEntity.NO_FACE_FREQUENCY : be.getFaceFrequency(face);
            String freqText = freq == WarpTunnelBlockEntity.NO_FACE_FREQUENCY
                    ? Component.translatable("block.avaritia_singularity_stress.warp_tunnel.config.unset").getString()
                    : String.valueOf(freq);
            // 有带子：白字；没软帘门：灰字，一眼能看出这一面不能配
            graphics.drawString(font, faceName(face).getString(), LABEL_X, y, hasBelt ? 0xFFFFFF : 0x7F7F7F, false);
            graphics.drawString(font, freqText, LABEL_X + 30, y, hasBelt ? 0xFFFFFF : 0x7F7F7F, false);
        }
    }

    /** 方位名走 lang（本模组已有 warp_tunnel.dir.*），避免界面里再留一份硬编码中文。 */
    private static Component faceName(Direction face) {
        return switch (face) {
            case NORTH -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.north");
            case EAST -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.east");
            case SOUTH -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.south");
            case WEST -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.west");
            case UP -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.up");
            case DOWN -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.down");
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不要再手动调 renderBackground —— AbstractContainerScreen.render 内部已经调过一次
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
