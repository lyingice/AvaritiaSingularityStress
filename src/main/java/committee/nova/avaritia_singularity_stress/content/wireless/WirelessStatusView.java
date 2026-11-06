package committee.nova.avaritia_singularity_stress.content.wireless;

/**
 * B20：无线配置界面顶部「运行状态区」的数据快照。
 * <p>
 * <b>为什么要有这个类型</b>：B10 的方块实体每 5 tick 就把
 * 状态 / 本网产能·消耗·空闲 写进客户端同步包（{@code WirelessStressBridgeBlockEntity.write}
 * 的 {@code clientPacket} 分支），但 {@code WirelessConfigScreen} 原先一个字段都没读，
 * 玩家开界面只能改数值、看不到「为什么没在传」。这个 record 把那些已有字段
 * 拼成一次读取，界面不必知道 BE 内部有多少个 {@code debug*} 字段、也不必按类型强转。
 * <p>
 * <b>为什么候选数 {@code peerCandidates} 必须由服务端算</b>：
 * {@code WirelessService.candidates} 第一行就对客户端返回空列表
 * （{@code WirelessService.java:77}），而 {@code WirelessDevice.isAlive()} 又要求
 * {@code !level.isClientSide}（{@code WirelessStressBridgeBlockEntity.java:280}）。
 * 频道表本身也只存在于服务端。所以这个数字只能服务端算完随同步包下发。
 *
 * @param statusKey     状态在 lang 里的后缀（如 {@code sending} / {@code no_peer} /
 *                      {@code pending}），拼成 {@code avaritia_singularity_stress.wireless.status.<key>}
 * @param bridgeSu      当前跨桥流动的 SU（输出端 = 已从源网扣除；接收端 = 已注入本网）
 * @param capacity      本机所在网络的产能
 * @param stress        本机所在网络的消耗
 * @param idle          本机所在网络的空闲（capacity - stress，不小于 0）
 * @param peerCandidates 同频道设备台数；{@code -1} 表示本设备不适用（尚未检测）
 * @param range         配对距离上限（格），来自 COMMON config，客户端可直接读
 */
public record WirelessStatusView(
        String statusKey,
        float bridgeSu,
        float capacity,
        float stress,
        float idle,
        int peerCandidates,
        double range) {

    /** 尚未完成第一次无线 tick（刚放置 / 刚改频率）时的状态后缀。 */
    public static final String PENDING = "pending";
}
