package committee.nova.avaritia_singularity_stress.init.provider;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import committee.nova.avaritia_singularity_stress.init.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, AvaritiaSingularityStressMod.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + AvaritiaSingularityStressMod.MODID, "Avaritia Singularity Stress");
        add(ModBlocks.EXTREME_MECHANICAL_CRAFTER.get(), "Extreme Mechanical Crafter");
        add(ModBlocks.EXTREME_SMITHING_CRAFTER.get(), "Extreme Smithing Crafter");
        add(ModItems.NEUTRON_EXTENDO_GRIP.get(), "Neutron Extendo Grip");
        // I2 深空延拓之手：描述走 Create 的 ItemDescription 体系（挂载见 init/client/ModTooltips）。
        // 键固定为 <物品 descriptionId>.tooltip.*；conditionN / behaviourN 必须从 1 连续，
        // 中间断一个号后面整段都不会被读（ItemDescription.fillBuilder 遇到缺键就 break）。
        add("item." + AvaritiaSingularityStressMod.MODID + ".neutron_extendo_grip.tooltip.summary",
                "Greatly _increases reach distance_ of the wielder, and boosts _Attack_ and _Mining_ speed. "
                        + "Can be powered with _Air_ _Pressure_ from a _Backtank_");
        add("item." + AvaritiaSingularityStressMod.MODID + ".neutron_extendo_grip.tooltip.condition1",
                "When in Off-Hand");
        add("item." + AvaritiaSingularityStressMod.MODID + ".neutron_extendo_grip.tooltip.behaviour1",
                "Increases _reach distance_ of items used in the _Main-Hand_.");
        add("item." + AvaritiaSingularityStressMod.MODID + ".neutron_extendo_grip.tooltip.condition2",
                "With one in each hand");
        add("item." + AvaritiaSingularityStressMod.MODID + ".neutron_extendo_grip.tooltip.behaviour2",
                "_Reach distance_ increases even further.");
        add("item." + AvaritiaSingularityStressMod.MODID + ".neutron_extendo_grip.tooltip.condition3",
                "While wearing Backtank");
        add("item." + AvaritiaSingularityStressMod.MODID + ".neutron_extendo_grip.tooltip.behaviour3",
                "_No_ _Durability_ will be used. Instead, _Air_ _pressure_ is drained from the Tank");
        add("jei.extreme_crafter.tier.sculk", "Extreme Crafter (Sculk Tier)");
        add("jei.extreme_crafter.tier.nether", "Extreme Crafter (Nether Tier)");
        add("jei.extreme_crafter.tier.end", "Extreme Crafter (End Tier)");
        add("jei.extreme_crafter.tier.extreme", "Extreme Crafter (Eternity Tier)");
        add("jei.extreme_crafter.smithing", "Extreme Smithing Crafter");
        add("jei.extreme_crafter.kinetic_crafting", "Extreme Kinetic Crafting");
        add("jei.extreme_crafter.kinetic_smithing", "Extreme Kinetic Smithing");

        // 修订版 v1:B6 定名"奇点压缩器"(注册 id 不变,保持旧存档兼容)。
        add(ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(), "Singularity Compressor");
        add(ModBlocks.SINGULARITY_STABILIZER.get(), "Singularity Stabilizer");

        // ===== B2 收集器 =====
        add(ModBlocks.KINETIC_NEUTRON_COLLECTOR.get(), "Kinetic Neutronium Collector");
        add(ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get(), "Dense Kinetic Neutronium Collector");
        add(ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get(), "Elite Kinetic Neutronium Collector");
        add(ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get(), "Ultimate Kinetic Neutronium Collector");

        // ===== B4 压缩机 =====
        add(ModBlocks.KINETIC_NEUTRON_COMPRESSOR.get(), "Kinetic Neutronium Compressor");
        add(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get(), "Dense Kinetic Neutronium Compressor");
        add(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get(), "Elite Kinetic Neutronium Compressor");
        add(ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get(), "Ultimate Kinetic Neutronium Compressor");

        // ===== 无线系列 B9 / B10 / B11 =====
        add(ModBlocks.WIRELESS_ITEM_FUNNEL.get(), "Wireless Item Funnel");
        add(ModBlocks.WIRELESS_STRESS_BRIDGE_SENDER.get(), "Wireless Stress Transmitter (Sender)");
        add(ModBlocks.WIRELESS_STRESS_BRIDGE_RECEIVER.get(), "Wireless Stress Transmitter (Receiver)");
        // ===== B15 空间折跃隧道 =====
        add(ModBlocks.WARP_TUNNEL.get(), "Spatial Fold Tunnel");
        // ===== B18 水晶矩阵机壳封装（命名对齐 Create：Andesite/Brass Encased ...） =====
        add(ModBlocks.CRYSTAL_MATRIX_ENCASED_SHAFT.get(), "Crystal Matrix Encased Shaft");
        add(ModBlocks.BLAZE_CUBE_CASING.get(), "Blaze Cube Casing");
        add(ModBlocks.BLAZE_CUBE_ENCASED_SHAFT.get(), "Blaze Cube Encased Shaft");
        add(ModBlocks.BLAZE_CUBE_ENCASED_COGWHEEL.get(), "Blaze Cube Encased Cogwheel");
        add(ModBlocks.BLAZE_CUBE_ENCASED_LARGE_COGWHEEL.get(), "Blaze Cube Encased Large Cogwheel");
        add(ModBlocks.CRYSTAL_MATRIX_ENCASED_COGWHEEL.get(), "Crystal Matrix Encased Cogwheel");
        add(ModBlocks.CRYSTAL_MATRIX_ENCASED_LARGE_COGWHEEL.get(), "Crystal Matrix Encased Large Cogwheel");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.filter.any", "Filter: accepts any item");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.filter.only", "Filter: only %s");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.filter.no_link", "[%s] face has no link - wrench it onto another tunnel first");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.filter.cleared", "[%s] face filter cleared");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.none", "[%s] face: not bound");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.info", "[%s] face -> [%s] at %s");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.selected", "[%1$s] face %2$s selected (sender) - wrench another tunnel's face to bind");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.config", "Warp Tunnel");
        // B15 配置界面：与 B9/B10 共用同一套面板外观后，这两条文案改成 lang，界面里不再硬编码中文
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.config.export_filter", "Export filter");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.config.unset", "Not set");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.unmatched", "Frequency %2$s set, but no peer matched");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.cancel", "Selection cancelled");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.unbound", "[%1$s] face %2$s unbound");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.done", "Bound [%1$s %2$s] -> [%3$s %4$s] (arrow = item flow)");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.gone", "Selected tunnel is gone");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.north", "N");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.east", "E");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.south", "S");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.west", "W");
        // 上下两面不接传送带：单独一条提示，避免顶面被当成"未绑定"。
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.up", "Top");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.dir.down", "Bottom");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.not_bindable",
                "[%s] face cannot take a belt - only the four side faces can be bound");
        add("block." + AvaritiaSingularityStressMod.MODID + ".warp_tunnel.link.no_belt",
                "[%s] face has no belt attached - nothing to bind here");
        // [B11 雪藏] B11 无线流体传输器名称：整体雪藏。
        // add(ModBlocks.WIRELESS_FLUID_TRANSMITTER_SENDER.get(), "Wireless Fluid Transmitter (Sender)");
        // add(ModBlocks.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get(), "Wireless Fluid Transmitter (Receiver)");

        // ===== 护目镜 / Jade =====
        // 代码里这些键是按 <modid>.<key> 请求的(LangBuilder 用 modid 作命名空间,
        // Jade 用显式前缀),所以这里必须写出完整前缀才会命中,否则会显示原始键名。
        String prefix = AvaritiaSingularityStressMod.MODID + ".";
        // ssu 应力单位:接在换算后的数值后面,对应 Create 原生的 "su"。
        add(prefix + "generic.unit.ssu", "ssu");
        add(prefix + "kinetic_neutron_compressor.goggles.efficiency", "Efficiency: Material cost -%1$s");

        // ===== 奇点应力 ssu:护目镜(对标原版应力组)=====
        add(prefix + "singularity.goggles.stats", "Singularity Stats:");
        add(prefix + "singularity.goggles.impact", "Singularity Stress Impact:");
        add(prefix + "singularity.goggles.concentration", "Singularity Concentration: %1$s");
        add(prefix + "singularity.goggles.state", "State: ");
        add(prefix + "singularity.state.none", "None");
        add(prefix + "singularity.state.active", "Singularity");
        add(prefix + "singularity.state.overexcited", "Singularity (Overexcited)");
        add(prefix + "singularity.state.ultimate", "Singularity (Ultimate)");
        // B6 护目镜
        add(prefix + "singularity.goggles.compressing", "Compressing: %1$s ssu (idle stress %2$s su)");
        add(prefix + "singularity.goggles.below_gate", "Idle stress below threshold (%1$s su): no ssu produced");
        add(prefix + "singularity.goggles.qc_suppressed", "B14 Stabilizer: QC loss suppressed");
        add(prefix + "singularity.goggles.idle", "Idle: not running");

        // ===== B10 无线应力传输器:护目镜(调试用)=====
        add(prefix + "wireless.goggles.frequency", "Frequency: %1$s (%2$s)");
        add(prefix + "wireless.goggles.unbound", "No frequency (right-click with the Frequency Card)");
        add(prefix + "wireless.goggles.status", "Status: %1$s");
        add(prefix + "wireless.goggles.io_face", "Network port face: %1$s");
        add(prefix + "wireless.goggles.speed", "Speed: %1$s RPM");
        add(prefix + "wireless.goggles.facing", "Pipe port face: %1$s");
        add(prefix + "wireless.goggles.peer", "Peer: %1$s");
        add(prefix + "wireless.goggles.required_facing", "Port must face: %1$s");
        add(prefix + "wireless.goggles.transfer_out", "Bridge flow: %1$s su (drained from source network)");
        add(prefix + "wireless.goggles.transfer_in", "Bridge flow: %1$s su (injected into this network)");
        add(prefix + "wireless.goggles.net", "Network: capacity %1$s / stress %2$s / idle %3$s su");
        add(prefix + "wireless.goggles.source_speed", "Source network speed: %1$s RPM");
        add(prefix + "wireless.goggles.limit_send", "Send limit: %1$s su");
        add(prefix + "wireless.goggles.limit_receive", "Receive limit: %1$s su");
        add(prefix + "wireless.goggles.priority", "Priority: %1$s");
        add(prefix + "wireless.status.unbound", "No frequency bound");
        add(prefix + "wireless.status.no_network", "Not on a stress network (attach a shaft or gear to the single port face)");
        add(prefix + "wireless.status.no_headroom", "Source network has no idle stress");
        add(prefix + "wireless.status.no_peer", "No receiver with the same frequency in range");
        add(prefix + "wireless.status.sending", "Sending (output side)");
        add(prefix + "wireless.status.receiving", "Supplying (receiver)");
        add(prefix + "wireless.status.receive_idle", "Waiting for the sender");
        // [B11 雪藏] B11 流体桥接诊断文案：整体雪藏。
        // // B11 fluid bridge diagnosis (keys = lowercase BridgeStatus names)
        // add(prefix + "wireless.fluid.status.unbound", "No frequency bound");
        // add(prefix + "wireless.fluid.status.no_peer", "No peer with the same frequency in range (both ends need the same frequency)");
        // add(prefix + "wireless.fluid.status.facing_not_opposite", "The two port faces are not opposite each other");
        // add(prefix + "wireless.fluid.status.no_local_pump", "No mechanical pump on the pipes of this end's port face");
        // add(prefix + "wireless.fluid.status.no_peer_pump", "No mechanical pump on the pipes of the peer's port face");
        // add(prefix + "wireless.fluid.status.peer_out_of_range", "Peer is too far away (beyond the fluid transmitter range)");
        // add(prefix + "wireless.fluid.status.peer_ambiguous", "More than one peer on this frequency (one-to-one only, remove the extras)");
        // add(prefix + "wireless.fluid.status.pump_axis_mismatch", "The two pumps face different axes");
        // add(prefix + "wireless.fluid.status.ready", "Bridge ready (waiting for fluid propagation)");

        // ===== Jade =====
        add(prefix + "jade.progress", "Progress: %1$s");
        add(prefix + "jade.cycle", "Cycle: %1$s s");
        add(prefix + "jade.state", "State: %1$s");
        add(prefix + "jade.stabilizer", "QC loss suppressed");
        add("gui.avaritia_singularity_stress.empty","empty");
        // ===== I3 无线频率卡 =====
        add("item.avaritia_singularity_stress.wireless_frequency_card", "Wireless Frequency Card");
        add("item.avaritia_singularity_stress.wireless_frequency_card.tooltip.frequency", "Frequency: %s");
        add("item.avaritia_singularity_stress.wireless_frequency_card.tooltip.hint", "Right-click air to configure, right-click machine to write");
        add("item.avaritia_singularity_stress.wireless_frequency_card.unset", "Not set");
        add("item.avaritia_singularity_stress.wireless_frequency_card.read", "Channel %s (%s)");
        add("item.avaritia_singularity_stress.wireless_frequency_card.read_none", "Machine has no channel");
        add("item.avaritia_singularity_stress.wireless_frequency_card.write", "Wrote channel %s");
        add("item.avaritia_singularity_stress.wireless_frequency_card.visibility.private", "Private");
        add("item.avaritia_singularity_stress.wireless_frequency_card.visibility.public", "Public");
        add("gui.avaritia_singularity_stress.preset.save","Overwrite preset %s");
        add("gui.avaritia_singularity_stress.preset.load","Load preset frequency: %s");

// ===== I3 GUI =====
        add("gui.avaritia_singularity_stress.visibility.private", "Private");
        add("gui.avaritia_singularity_stress.visibility.public", "Public");
        add("gui.avaritia_singularity_stress.preset.empty", "Empty preset");
        // ===== 无线方块(B9/B10/B11)配置界面:B10 的两个"数量"字段标签 =====
        add("gui.avaritia_singularity_stress.wireless.send_limit", "Send limit");
        add("gui.avaritia_singularity_stress.wireless.receive_limit", "Receive limit");
        add("gui.avaritia_singularity_stress.wireless.priority", "Priority");

        // ===== B20 无线配置界面：运行状态区 + 套用频率卡 =====
        // 状态文案复用 wireless.status.*（与护目镜同一套，避免两处措辞漂移）
        add(prefix + "wireless.status.pending", "Detecting...");
        add("gui.avaritia_singularity_stress.wireless.status_line", "Status: %s");
        add("gui.avaritia_singularity_stress.wireless.bridge_flow", "Bridge flow: %s su");
        add("gui.avaritia_singularity_stress.wireless.net_line", "Net cap/use/idle: %s/%s/%s");
        add("gui.avaritia_singularity_stress.wireless.peers_ok", "Peers: 1 (range %s)");
        // B23：并流/分流合法化后，拓扑改为如实报数量
        add("gui.avaritia_singularity_stress.wireless.peers_targets", "Targets: %s receivers (range %s)");
        add("gui.avaritia_singularity_stress.wireless.peers_sources", "Sources: %s senders (range %s)");
        add("gui.avaritia_singularity_stress.wireless.peers_none", "No peer within %s blocks");
        add("gui.avaritia_singularity_stress.wireless.peers_ambiguous", "Peers: %s - ambiguous, halted");
        add("gui.avaritia_singularity_stress.wireless.peers_pending", "Peers: detecting...");
        add("gui.avaritia_singularity_stress.wireless.apply_card", "Use frequency card");
        add("gui.avaritia_singularity_stress.wireless.apply_card.no_card", "Hold a frequency card");
        add("gui.avaritia_singularity_stress.wireless.apply_card.unset", "Card has no channel");
        add("gui.avaritia_singularity_stress.wireless.apply_card.done", "Applied channel %s");
        // B21 档位转速滚轮（贴面，创造马达同款）
        add("gui.avaritia_singularity_stress.wireless.gear_speed", "Gear speed");
        // B22 输出端百分比滚轮
        add("gui.avaritia_singularity_stress.wireless.send_ratio", "Send ratio");
        // B9 外部输出：开启后接收端把物品吐到漏斗口外（便于落到传送带上），关闭则直接插入朝向容器
        add("gui.avaritia_singularity_stress.wireless.external_output", "External Output");
        add(prefix + "wireless.goggles.gear_speed", "Gear speed: %1$s RPM");

        // ===== Ponder scenes: all scenes of this mod live under the Avaritia tag =====
        // key 规则：标题 <modid>.ponder.<sceneId>.header；台词 <modid>.ponder.<sceneId>.text_<N>
        // 这里的英文与剧本里 .text("...") 的默认值保持一致，中文另见 ModLanguageProviderZhCn。
        add(AvaritiaSingularityStressMod.MODID + ".ponder.blaze_cube_casing.header", "Encasing Cogwheels");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.blaze_cube_casing.text_1",
                "Blaze Cube Casing can be used to decorate cogwheels");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.crystal_matrix_casing.header", "Encasing Cogwheels");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.crystal_matrix_casing.text_1",
                "Crystal Matrix Casing can be used to decorate cogwheels");

        // B15 空间折跃隧道：剧本见 ponder/WarpTunnelPonderScene.java（6 句，顺序即 text_1..6）
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel.header", "Warping Items Between Belts");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel.text_1",
                "Sits on a belt, like a tunnel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel.text_2",
                "One filter slot per side");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel.text_3",
                "Put a second one on another belt");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel.text_4",
                "Wrench one side, then a side on the other tunnel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel.text_5",
                "Comes out on the side you picked");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel.text_6",
                "Powered: nothing goes through");

        // B9 无线物品传输漏斗：剧本见 ponder/WirelessFunnelPonderScene.java（5 句）
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_item_funnel.header",
                "Wireless Item Funnel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_item_funnel.text_1",
                "The wireless funnel works like a normal funnel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_item_funnel.text_2",
                "It needs a channel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_item_funnel.text_3",
                "The Wireless Frequency Card sets it directly");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_item_funnel.text_4",
                "As long as a funnel on the same channel is collecting, this one picks items up even with no container behind");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_item_funnel.text_5",
                "Without External Output it pulls from its container but never spits anything out");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_item_funnel.text_6",
                "With External Output it drops items normally");

        add(AvaritiaSingularityStressMod.MODID + ".ponder.tag.avaritia", "Avaritia");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.tag.avaritia.description",
                "Blocks and components added by this mod");

        // ==== 以下对应思索者（Ponderer）2026-11-08 导出的剧本；导出文件里没有英文，这里按中文原意翻译 ====
        add(AvaritiaSingularityStressMod.MODID + ".ponder.extreme_mechanical_crafter.header",
                "Extreme Mechanical Crafter");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.extreme_mechanical_crafter.text_1",
                "An array of Extreme Mechanical Crafters can craft anything from Avaritia's crafting recipes");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.extreme_mechanical_crafter.text_2",
                "Mechanically it works just like the Mechanical Crafter");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.extreme_smithing_crafter.header",
                "Extreme Smithing Crafter");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.extreme_smithing_crafter.text_1",
                "Used to craft Extreme Smithing recipes");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.singularity_rotation_speed_controller.header",
                "Obtaining Singularity Stress");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.singularity_rotation_speed_controller.text_1",
                "The Singularity Compressor turns surplus idle stress from upstream into Singularity Stress");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.singularity_rotation_speed_controller.text_2",
                "The compressor adds extra loss, which the stabilizer can reduce");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.singularity_rotation_speed_controller.text_3",
                "Singularity kinetic machines are boosted by Singularity Stress");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_s_47ed8.header",
                "Sorting with the warp");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_s_47ed8.text_1",
                "Both faces of the two tunnels now share one channel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_s_47ed8.text_2",
                "When items enter through this face");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_s_47ed8.text_3",
                "They warp out of that face");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_s_47ed8.text_4",
                "With a filter on the warp face only matching items warp; everything else passes by");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_scene_1.header",
                "Space Warp Tunnel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_scene_1.text_1",
                "The Space Warp Tunnel is a special kind of tunnel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_scene_1.text_2",
                "Each of its four faces can hold its own channel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_scene_1.text_3",
                "Set two faces to the same channel to link them");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.warp_tunnel_scene_1.text_4",
                "The Wireless Frequency Card sets a channel instantly");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_stress_bridge_receiver_s_12fbb.header",
                "Gathering stress");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_stress_bridge_receiver_s_12fbb.text_1",
                "Matching channels gather stress together");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_stress_bridge_receiver_s_1b2c1.header",
                "Splitting stress");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_stress_bridge_receiver_s_1b2c1.text_1",
                "Matching channels can also split stress");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_stress_bridge_receiver_scene_1.header",
                "Wireless Stress Transmitter");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_stress_bridge_receiver_scene_1.text_1",
                "Transmit stress wirelessly");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_stress_bridge_receiver_scene_1.text_2",
                "The transmitter needs a channel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.wireless_stress_bridge_receiver_scene_1.text_3",
                "Two ways to set the channel");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.tag.ponderer_exported",
                "Ponderer");
        add(AvaritiaSingularityStressMod.MODID + ".ponder.tag.ponderer_exported.description",
                "These scenes were made and exported with the Ponderer mod. Learn more: https://modrinth.com/mod/the-ponderer");
    }
}

