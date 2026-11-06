package committee.nova.avaritia_singularity_stress.init.client.screen;

import java.util.function.BooleanSupplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组统一的<b>贴图复选框</b>：{@code textures/gui/check_box_off.png} / {@code check_box_on.png}（均 64×14）。
 * <p>
 * 源图结构（逐像素核对过）：{@code x=0..47} 文字区（可横向拉伸）、
 * {@code x=48..61} 右侧复选框本体（9px 方块 + 描边，必须保持原样不拉伸）。
 * 所以绘制是"两片"：左边拉伸、右边固定 14px。
 * <p>
 * 状态每帧从 {@link BooleanSupplier} 读，因此服务端把值同步回来时勾选状态会自动跟着变，
 * 不需要在界面里另存一份（避免"界面显示与实际不一致"）。
 */
public class SpriteCheckBox extends Button {

    public static final ResourceLocation OFF = ResourceLocation.fromNamespaceAndPath(
            "avaritia_singularity_stress", "textures/gui/check_box_off.png");
    public static final ResourceLocation ON = ResourceLocation.fromNamespaceAndPath(
            "avaritia_singularity_stress", "textures/gui/check_box_on.png");

    private static final int TEX_W = 64;
    private static final int TEX_H = 14;
    /** 文字区在源图里的宽度（x=0..47）。 */
    private static final int LABEL_W = 48;
    /** 复选框本体宽度（x=48..61），绘制时固定占右侧 14px。 */
    private static final int BOX_W = 14;

    private static final int TEXT_COLOR = 0x303030;
    private static final int TEXT_COLOR_DISABLED = 0x8A8A8A;
    private static final int TEXT_PAD = 5;

    private final Font font;
    private final BooleanSupplier state;
    private final Component label;

    public SpriteCheckBox(Font font, int x, int y, int width, int height,
                          BooleanSupplier state, Component label, OnPress onPress) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.font = font;
        this.state = state;
        this.label = label;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation texture = state.getAsBoolean() ? ON : OFF;
        int labelW = Math.max(0, getWidth() - BOX_W);
        if (labelW > 0) {
            graphics.blit(texture, getX(), getY(), labelW, getHeight(), 0, 0, LABEL_W, TEX_H, TEX_W, TEX_H);
        }
        graphics.blit(texture, getX() + labelW, getY(), BOX_W, getHeight(),
                LABEL_W, 0, BOX_W, TEX_H, TEX_W, TEX_H);

        if (isActive() && isHoveredOrFocused()) {
            graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1,
                    0x1CFFFFFF);
        }

        int color = isActive() ? TEXT_COLOR : TEXT_COLOR_DISABLED;
        int textY = getY() + (getHeight() - 8) / 2;
        int available = Math.max(0, labelW - TEXT_PAD * 2);
        Component shown = font.width(label) <= available
                ? label
                : Component.literal(font.plainSubstrByWidth(label.getString(), available));
        graphics.drawString(font, shown, getX() + TEXT_PAD, textY, color, false);
    }
}
