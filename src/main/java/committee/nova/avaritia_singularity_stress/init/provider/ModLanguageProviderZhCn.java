package committee.nova.avaritia_singularity_stress.init.provider;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import committee.nova.avaritia_singularity_stress.init.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** 简体中文本地化(与 en_us 同键)。 */
public class ModLanguageProviderZhCn extends LanguageProvider {
    public ModLanguageProviderZhCn(PackOutput output) {
        super(output, AvaritiaSingularityStressMod.MODID, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + AvaritiaSingularityStressMod.MODID, "奇点应力");
        add(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get(), "终焉动力合成器");
        add(ModBlocks.EXTREME_SMITHING_CRAFTER.get(), "终焉锻造器");
        add(ModItems.NEUTRON_EXTENDO_GRIP.get(), "深空延拓之手");
        add("jei.extreme_crafter.tier.sculk", "终焉合成器(幽匿工作台)");
        add("jei.extreme_crafter.tier.nether", "终焉合成器(下界工作台)");
        add("jei.extreme_crafter.tier.end", "终焉合成器(末地工作台)");
        add("jei.extreme_crafter.tier.extreme", "终焉合成器(终焉工作台)");
        add("jei.extreme_crafter.smithing", "终焉锻造器");
        add("jei.extreme_crafter.kinetic_crafting", "终焉动能合成");
        add("jei.extreme_crafter.kinetic_smithing", "终焉动能锻造");

        // 修订版 v1:B6 定名"奇点压缩器"(注册 id 不变,保持旧存档兼容)。
        add(ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(), "奇点压缩器");
        add(ModBlocks.SINGULARITY_STABILIZER.get(), "奇点稳定器");

        // ===== B2 收集器 =====
        add(ModBlocks.KINETIC_NEUTRON_COLLECTOR.get(), "基础动能中子态素收集器");
        add(ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get(), "致密动能中子态素收集器");
        add(ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get(), "精英动能中子态素收集器");
        add(ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get(), "极限动能中子态素收集器");

        // ===== B4 压缩机 =====
        add(ModBlocks.KINETIC_NEUTRON_COMPRESSOR.get(), "基础动能中子态素压缩机");
        add(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get(), "致密动能中子态素压缩机");
        add(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get(), "精英动能中子态素压缩机");
        add(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get(), "极限动能中子态素压缩机");

        // ===== 无线系列 B9 / B10 / B11 =====
        add(ModBlocks.WIRELESS_ITEM_FUNNEL.get(), "无线物品传输漏斗");
        add(ModBlocks.WIRELESS_STRESS_BRIDGE_SENDER.get(), "无线应力传输器(输出端)");
        add(ModBlocks.WIRELESS_STRESS_BRIDGE_RECEIVER.get(), "无线应力传输器(接收端)");
        // ===== B15 空间折跃隧道 =====
        add(ModBlocks.WARP_TUNNEL.get(), "空间折跃隧道");
        // ===== B18 水晶矩阵机壳封装（命名对齐 Create：安山/黄铜传动杆箱、齿轮箱、大齿轮箱） =====
        add(ModBlocks.CRYSTAL_MATRIX_ENCASED_SHAFT.get(), "水晶矩阵传动杆箱");
        add(ModBlocks.BLAZE_CUBE_CASING.get(), "炽骨立方机壳");
        add(ModBlocks.BLAZE_CUBE_ENCASED_SHAFT.get(), "炽骨立方传动杆箱");
        add(ModBlocks.BLAZE_CUBE_ENCASED_COGWHEEL.get(), "炽骨立方齿轮箱");
        add(ModBlocks.BLAZE_CUBE_ENCASED_LARGE_COGWHEEL.get(), "炽骨立方大齿轮箱");
        add(ModBlocks.CRYSTAL_MATRIX_ENCASED_COGWHEEL.get(), "水晶矩阵齿轮箱");
        add(ModBlocks.CRYSTAL_MATRIX_ENCASED_LARGE_COGWHEEL.get(), "水晶矩阵大齿轮箱");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.filter.any", "过滤：接收任何物品");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.filter.only", "过滤：仅 %s");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.filter.no_link", "[%s]面还没有连接，先用扳手连到另一台隧道");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.filter.cleared", "[%s]面的过滤已清空");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.none", "[%s]面：未绑定");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.info", "[%s]面 → [%s]面 @ %s");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.selected", "已选中[%1$s]面 %2$s（发送方），扳手右键另一台隧道的面完成绑定");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.config", "空间折跃隧道");
        // B15 配置界面：与 B9/B10 共用同一套面板外观后，这两条文案改成 lang，界面里不再硬编码中文
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.config.export_filter", "出口过滤");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.config.unset", "未设置");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.unmatched", "已设频率 %2$s，但对端未配对");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.cancel", "已取消选择");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.unbound", "[%1$s]面 %2$s 已解绑");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.done", "已绑定 [%1$s %2$s] → [%3$s %4$s]（箭头=物品流向）");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.gone", "选中的隧道已不存在");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.north", "北");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.east", "东");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.south", "南");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.west", "西");
        // 上下两面不接传送带：单独一条提示，避免顶面被当成"未绑定"。
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.up", "上");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.down", "下");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.not_bindable",
                "[%s]面接不了传送带，只有四个侧面能绑定");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.no_belt",
                "[%s]面没有传送带，这里没得绑");
        // [B11 雪藏] B11 无线流体传输器名称：整体雪藏。
        // add(ModBlocks.WIRELESS_FLUID_TRANSMITTER_SENDER.get(), "无线流体传输器(输出端)");
        // add(ModBlocks.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get(), "无线流体传输器(接收端)");

        // ===== 护目镜 / Jade =====
        // 必须带完整 modid 前缀,与代码里 LangBuilder(modid)/显式前缀的请求一致。
        String prefix = AvaritiaSingularityStressMod.MODID + ".";
        // ssu 应力单位,对应 Create 原生的 "su"。
        add(prefix + "generic.unit.ssu", "ssu");
        add(prefix + "kinetic_neutron_compressor.goggles.efficiency", "效能:原料消耗 -%1$s");

        // ===== 奇点应力 ssu:护目镜(对标原版应力组)=====
        add(prefix + "singularity.goggles.stats", "奇点动力学状态:");
        add(prefix + "singularity.goggles.impact", "奇点应力影响:");
        add(prefix + "singularity.goggles.concentration", "奇点浓度:%1$s");
        add(prefix + "singularity.goggles.state", "状态:");
        add(prefix + "singularity.state.none", "无");
        add(prefix + "singularity.state.active", "奇点状态");
        add(prefix + "singularity.state.overexcited", "奇点状态(超激发态)");
        add(prefix + "singularity.state.ultimate", "奇点状态(终极激发态)");
        // B6 护目镜
        add(prefix + "singularity.goggles.compressing", "正在压缩:%1$s ssu(空闲应力 %2$s su)");
        add(prefix + "singularity.goggles.below_gate", "空闲应力未达门槛(%1$s su):暂不产出 ssu");
        add(prefix + "singularity.goggles.qc_suppressed", "B14 稳定器:品控损耗已抑制");
        add(prefix + "singularity.goggles.idle", "待机:未运转");

        // ===== B10 无线应力传输器:护目镜(调试用)=====
        // 原版动力学组在应力为 0 时整组不显示,而接收端 calculateStressApplied() 恒为 0,
        // 所以这里补一套自己的状态行(见 WirelessStressBridgeBlockEntity.addToGoggleTooltip)。
        add(prefix + "wireless.goggles.frequency", "频率:%1$s(%2$s)");
        add(prefix + "wireless.goggles.unbound", "未绑定频率(用频率卡右键写入)");
        add(prefix + "wireless.goggles.status", "状态:%1$s");
        add(prefix + "wireless.goggles.io_face", "接网面:%1$s");
        add(prefix + "wireless.goggles.speed", "转速:%1$s RPM");
        add(prefix + "wireless.goggles.facing", "管道接入面:%1$s");
        add(prefix + "wireless.goggles.peer", "对端:%1$s");
        add(prefix + "wireless.goggles.required_facing", "端口需要朝向:%1$s");
        add(prefix + "wireless.goggles.transfer_out", "桥流量:%1$s su(已从源网扣除)");
        add(prefix + "wireless.goggles.transfer_in", "桥流量:%1$s su(已注入本网)");
        add(prefix + "wireless.goggles.net", "本网:产能 %1$s / 消耗 %2$s / 空闲 %3$s su");
        add(prefix + "wireless.goggles.source_speed", "源网转速:%1$s RPM");
        add(prefix + "wireless.goggles.limit_send", "送出上限:%1$s su");
        add(prefix + "wireless.goggles.limit_receive", "接收上限:%1$s su");
        add(prefix + "wireless.goggles.priority", "优先级:%1$s");
        // 状态文案(键名 = WirelessStressBridgeBlockEntity.BridgeStatus 的小写名)
        add(prefix + "wireless.status.unbound", "未绑定频率");
        add(prefix + "wireless.status.no_network", "未接入应力网络(要在唯一的接网面上接轴或齿轮)");
        add(prefix + "wireless.status.no_headroom", "源网没有空闲应力");
        add(prefix + "wireless.status.no_peer", "范围内没有同频率的接收端");
        add(prefix + "wireless.status.sending", "传输中(输出端)");
        add(prefix + "wireless.status.receiving", "供电中(接收端)");
        add(prefix + "wireless.status.receive_idle", "等待输出端推送");
        // B20：刚放置/刚改频率、还没跑过第一次无线 tick 时的过渡态
        add(prefix + "wireless.status.pending", "检测中…");
        // [B11 雪藏] B11 流体桥接诊断文案：整体雪藏。
        // // B11 流体桥接诊断(键名 = WirelessFluidBridge.BridgeStatus 的小写名)
        // add(prefix + "wireless.fluid.status.unbound", "未绑定频率");
        // add(prefix + "wireless.fluid.status.no_peer", "找不到同频率的对端(两端都要绑同一频率且在范围内)");
        // add(prefix + "wireless.fluid.status.facing_not_opposite", "两端接入面不对置(两端的接入面必须相对)");
        // add(prefix + "wireless.fluid.status.no_local_pump", "本端接入面这一侧的管道上找不到动力泵");
        // add(prefix + "wireless.fluid.status.no_peer_pump", "对端接入面这一侧的管道上找不到动力泵");
        // add(prefix + "wireless.fluid.status.peer_out_of_range", "同频率对端离得太远(超出无线流体传输范围)");
        // add(prefix + "wireless.fluid.status.peer_ambiguous", "同频率对端不止一台:本方案一对一,请拆掉多余的");
        // add(prefix + "wireless.fluid.status.pump_axis_mismatch", "两端动力泵的朝向轴不一致");
        // add(prefix + "wireless.fluid.status.ready", "桥已成立,等流体传播触发");

        // ===== Jade =====
        add(prefix + "jade.progress", "进度:%1$s");
        add(prefix + "jade.cycle", "运行周期:%1$s 秒");
        add(prefix + "jade.state", "状态:%1$s");
        add(prefix + "jade.stabilizer", "额外损耗抑制中");
        add("gui.avaritia_singularity_stress.empty","空");
        // ===== I3 无线频率卡 =====
        add("item.avaritia_singularity_stress.wireless_frequency_card", "无线频率卡");
        add("item.avaritia_singularity_stress.wireless_frequency_card.tooltip.frequency", "频率：%s");
        add("item.avaritia_singularity_stress.wireless_frequency_card.tooltip.hint", "右键空气配置，右键机器写入");
        add("item.avaritia_singularity_stress.wireless_frequency_card.unset", "未设置");
        add("item.avaritia_singularity_stress.wireless_frequency_card.read", "频道 %s（%s）");
        add("item.avaritia_singularity_stress.wireless_frequency_card.read_none", "机器无频道");
        add("item.avaritia_singularity_stress.wireless_frequency_card.write", "已写入频道 %s");
        add("item.avaritia_singularity_stress.wireless_frequency_card.visibility.private", "私有");
        add("item.avaritia_singularity_stress.wireless_frequency_card.visibility.public", "公用");
        add("gui.avaritia_singularity_stress.preset.save","覆盖保存到预设 %s");
        add("gui.avaritia_singularity_stress.preset.load","读取预设频率：%s");


// ===== I3 GUI =====
        add("gui.avaritia_singularity_stress.visibility.private", "私有");
        add("gui.avaritia_singularity_stress.visibility.public", "公用");
        add("gui.avaritia_singularity_stress.preset.empty", "预设为空");
        // ===== 无线方块(B9/B10/B11)配置界面:B10 的两个"数量"字段标签 =====
        add("gui.avaritia_singularity_stress.wireless.send_limit", "送出上限");
        add("gui.avaritia_singularity_stress.wireless.receive_limit", "接收上限");
        add("gui.avaritia_singularity_stress.wireless.priority", "优先级");

        // ===== B20 无线配置界面：运行状态区 + 套用频率卡 =====
        // 状态文案复用 wireless.status.*（与护目镜同一套，避免两处措辞漂移）
        add("gui.avaritia_singularity_stress.wireless.status_line", "状态：%s");
        add("gui.avaritia_singularity_stress.wireless.bridge_flow", "桥流量：%s su");
        add("gui.avaritia_singularity_stress.wireless.net_line", "网络 产/耗/闲：%s/%s/%s");
        add("gui.avaritia_singularity_stress.wireless.peers_ok", "同频道 1 台 · 范围 %s 格");
        // B23：并流/分流合法化后，拓扑改为如实报数量
        add("gui.avaritia_singularity_stress.wireless.peers_targets", "对端 %s 个接收端 · 范围 %s 格");
        add("gui.avaritia_singularity_stress.wireless.peers_sources", "同频道 %s 个输出端 · 范围 %s 格");
        add("gui.avaritia_singularity_stress.wireless.peers_none", "范围内没有同频道设备（%s 格）");
        add("gui.avaritia_singularity_stress.wireless.peers_ambiguous", "同频道 %s 台：歧义，已停机");
        add("gui.avaritia_singularity_stress.wireless.peers_pending", "同频道设备：检测中…");
        add("gui.avaritia_singularity_stress.wireless.apply_card", "套用频率卡");
        add("gui.avaritia_singularity_stress.wireless.apply_card.no_card", "主手没有频率卡");
        add("gui.avaritia_singularity_stress.wireless.apply_card.unset", "频率卡没有设置频道");
        add("gui.avaritia_singularity_stress.wireless.apply_card.done", "已套用频道 %s");
        // B21 档位转速滚轮（贴面，创造马达同款）
        add("gui.avaritia_singularity_stress.wireless.gear_speed", "档位转速");
        // B22 输出端百分比滚轮
        add("gui.avaritia_singularity_stress.wireless.send_ratio", "送出占比");
        // B9 外部输出：开启后接收端把物品吐到漏斗口外（便于落到传送带上），关闭则直接插入朝向容器
        add("gui.avaritia_singularity_stress.wireless.external_output", "外部输出");
        add(prefix + "wireless.goggles.gear_speed", "档位转速:%1$s RPM");


    }
}

