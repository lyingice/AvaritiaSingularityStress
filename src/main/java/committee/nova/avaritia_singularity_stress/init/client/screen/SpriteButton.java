package committee.nova.avaritia_singularity_stress.init.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组统一的<b>贴图按钮</b>：{@code textures/gui/button.png}（64×14）。
 * <p>
 * 源图结构（逐像素核对过）：{@code x=0,1} 左描边、{@code x=2..59} 可拉伸底、
 * {@code x=60,61} 右描边，{@code x=62,63} 透明。因此按"三片"绘制：
 * 左 2px 与右 2px 原样贴，中间横向拉伸，任何宽度都不会把圆角拉变形。
 * <p>
 * 为什么自绘而不用 {@code Button.builder}：原版按钮走 {@code widget/button} 九宫格，
 * 与本模组面板（深色 + 青色标题带）不是一套视觉；改用统一精灵图后所有界面一致。
 */
public class SpriteButton extends Button {

    /** 统一按钮底图。 */
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "avaritia_singularity_stress", "textures/gui/button.png");

    private static final int TEX_W = 64;
    private static final int TEX_H = 14;
    /** 左右描边各 2px。 */
    private static final int CAP = 2;
    /** 中间可拉伸部分在源图里的起点与宽度（x=2..59）。 */
    private static final int MID_U = 2;
    private static final int MID_W = 58;

    /** 文字色：贴图底是浅灰，必须用深色字，白字会糊掉。 */
    private static final int TEXT_COLOR = 0x303030;
    private static final int TEXT_COLOR_DISABLED = 0x8A8A8A;

    private final Font font;

    public SpriteButton(Font font, int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.font = font;
    }

    /**
     * 三片横向拉伸地画一条按钮底。
     * <p>
     * 宽度不足 4px 时按剩余宽度等比缩小两侧描边，避免 {@code blit} 出现负宽度。
     */
    public static void drawBar(GuiGraphics graphics, ResourceLocation texture,
                               int x, int y, int width, int height) {
        int cap = Math.min(CAP, Math.max(0, width / 2));
        int midW = width - cap * 2;
        if (cap > 0) {
            graphics.blit(texture, x, y, cap, height, 0, 0, cap, TEX_H, TEX_W, TEX_H);
            graphics.blit(texture, x + width - cap, y, cap, height,
                    TEX_W - CAP, 0, cap, TEX_H, TEX_W, TEX_H);
        }
        if (midW > 0) {
            graphics.blit(texture, x + cap, y, midW, height, MID_U, 0, MID_W, TEX_H, TEX_W, TEX_H);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBar(graphics, TEXTURE, getX(), getY(), getWidth(), getHeight());
        if (isActive() && isHoveredOrFocused()) {
            // 贴图没有 hover 帧，用一层很淡的白色提亮代替；比换图省一套资源
            graphics.fill(getX() + CAP, getY() + 1, getX() + getWidth() - CAP, getY() + getHeight() - 1,
                    0x26FFFFFF);
        }
        int color = isActive() ? TEXT_COLOR : TEXT_COLOR_DISABLED;
        Component message = getMessage();
        int textX = getX() + (getWidth() - font.width(message)) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        graphics.drawString(font, message, textX, textY, color, false);
    }
}
