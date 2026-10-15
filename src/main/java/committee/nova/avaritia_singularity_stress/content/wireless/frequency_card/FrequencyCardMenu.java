package committee.nova.avaritia_singularity_stress.content.wireless.frequency_card;

import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * I3 无线频率卡的设置 GUI。
 * <p>
 * ContainerData 布局（共 14 槽）：
 * <pre>
 *   0  = 当前频率（NO_FREQUENCY = -1）
 *   1  = 当前可见性序号
 *   2  = 预设0 频率
 *   3  = 预设0 可见性序号
 *   ...
 *   12 = 预设5 频率
 *   13 = 预设5 可见性序号
 * </pre>
 * 全部数值都在 SHORT 范围内（0~9999 或 -1），ContainerData 安全。
 */
public class FrequencyCardMenu extends AbstractContainerMenu {

    public static final int DATA_SIZE = 14;

    public static final int PRESET_COUNT = 6;

    // ---- 布局常量（与 GUI JSON 对齐） ----
    public static final int TITLE_X = 67;
    public static final int TITLE_Y = 6;

    // Menu.java
    public static final int CARD_ICON_X = 80;
    public static final int CARD_ICON_Y = 32;

    public static final int INPUT_X = 49;
    public static final int INPUT_Y = 67;
    public static final int INPUT_W = 75;
    public static final int INPUT_H = 9;

    public static final int MODE_BUTTON_X = 130;
    public static final int MODE_BUTTON_Y = 65;
    public static final int MODE_BUTTON_W = 22;
    public static final int MODE_BUTTON_H = 13;

    public static final int MODE_TEXT_X = 131;
    public static final int MODE_TEXT_Y = 65;

    /** 6 组预设的按钮坐标（保存 / 读取 各一个，7x7）。 */
    public static final int[][] PRESET_SAVE_BUTTONS = {
            {62, 86}, {62, 108}, {62, 130}, {126, 86}, {126, 108}, {126, 130}
    };
    public static final int[][] PRESET_LOAD_BUTTONS = {
            {62, 97}, {62, 119}, {62, 141}, {126, 97}, {126, 119}, {126, 141}
    };
    /** 每个预设槽的数字标签坐标（代码渲染数字）。 */
    public static final int[][] PRESET_LABEL_POS = {
            {51, 89}, {51, 111}, {51, 133}, {115, 89}, {115, 111}, {115, 133}
    };

    private final ContainerData data;
    private final InteractionHand hand;

    /** 客户端构造器：{@code IMenuTypeExtension.create(FrequencyCardMenu::new)} 调用。 */
    public FrequencyCardMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, readOpenData(buf));
    }

    private FrequencyCardMenu(int id, Inventory playerInventory, OpenData open) {
        this(id, playerInventory, open.data(), open.hand());
    }

    /**
     * 打开界面时随包下发的初始快照。
     * <p>
     * <b>为什么必须下发</b>：只靠 {@code ContainerData} 的话，客户端菜单构造时全是 0，
     * 而 {@code init()} 就在那一刻跑 —— 频率框会先画 0/空，等服务端那几个
     * {@code ClientboundContainerSetDataPacket} 到了、再等下一个 {@code containerTick()}
     * 才回填成真值，中间那一帧就是"打开 GUI 闪一下"。
     * 现在客户端第一帧拿到的就是真实值（手牌里的卡片在客户端本来就可读）。
     * <p>
     * 只有卡片是物品、没有方块实体才需要这样传；B9/B10/B11 那套是直接读客户端 BE。
     */
    public static void writeOpenData(FriendlyByteBuf buf, ItemStack card, InteractionHand hand) {
        buf.writeBoolean(hand == InteractionHand.OFF_HAND);
        buf.writeVarInt(WirelessFrequencyCardItem.getFrequency(card));
        buf.writeVarInt(WirelessFrequencyCardItem.getVisibility(card).ordinal());
        for (int i = 0; i < PRESET_COUNT; i++) {
            buf.writeVarInt(WirelessFrequencyCardItem.getPresetFrequency(card, i));
            buf.writeVarInt(WirelessFrequencyCardItem.getPresetVisibility(card, i).ordinal());
        }
    }

    /** 与 {@link #writeOpenData} 一一对应；读出的 14 个值直接灌进 ContainerData 的 0..13 槽。 */
    private static OpenData readOpenData(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
        for (int i = 0; i < DATA_SIZE; i++) {
            data.set(i, buf.readVarInt());
        }
        return new OpenData(data, hand);
    }

    private record OpenData(ContainerData data, InteractionHand hand) {
    }

    private FrequencyCardMenu(int id, Inventory playerInventory, ContainerData data, InteractionHand hand) {
        super(ModMenus.WIRELESS_FREQUENCY_CARD.get(), id);
        this.data = data;
        this.hand = hand;

        addDataSlots(data);
    }

    /** 服务端工厂：把卡片当前值 + 6 个预设全部装进 ContainerData。 */
    public static FrequencyCardMenu server(int id, Inventory playerInventory, ItemStack card,
                                           InteractionHand hand) {
        SimpleContainerData data = new SimpleContainerData(DATA_SIZE);
        data.set(0, WirelessFrequencyCardItem.getFrequency(card));
        data.set(1, WirelessFrequencyCardItem.getVisibility(card).ordinal());
        for (int i = 0; i < PRESET_COUNT; i++) {
            data.set(2 + i * 2, WirelessFrequencyCardItem.getPresetFrequency(card, i));
            data.set(3 + i * 2, WirelessFrequencyCardItem.getPresetVisibility(card, i).ordinal());
        }
        return new FrequencyCardMenu(id, playerInventory, data, hand);
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        this.data.set(id, value);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public int getFrequency() {
        return data.get(0);
    }

    public IFrequencyBindable.ChannelVisibility getVisibility() {
        return IFrequencyBindable.ChannelVisibility.byIndex(data.get(1));
    }

    public int getPresetFrequency(int index) {
        return data.get(2 + index * 2);
    }

    public IFrequencyBindable.ChannelVisibility getPresetVisibility(int index) {
        return IFrequencyBindable.ChannelVisibility.byIndex(data.get(3 + index * 2));
    }

    public InteractionHand getHand() {
        return hand;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof WirelessFrequencyCardItem;
    }
}