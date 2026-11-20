# Avaritia Singularity Stress · 无尽贪婪：奇点应力

A Minecraft **1.21.1 / NeoForge** mod and **Create** addon themed around *Avaritia* (Re-Avaritia).
It adds a singularity-stress (SSU) economy, extreme-tier kinetic machines, and a connector-networked
automated crafting system.

Minecraft **1.21.1 / NeoForge** 模组、**Create（机械动力）** 附属，主题来自 *无尽贪婪*（Re-Avaritia）。
新增奇点应力（SSU）体系、终焉级动能机器，以及基于连接器组网的自动合成系统。

## Features · 功能

- **Singularity stress (SSU)** — stress compression, neutron collectors/compressors in four tiers, stabilizers.
  **奇点应力（SSU）**——应力压缩、四档中子收集器/压缩机、稳定器。
- **Extreme crafter family** — 9×9 extreme mechanical crafter, extreme smithing crafter linkage;
  **Blaze Cube Crafter** (low-spec: Avaritia tier 1–2 tables + its exclusive `blaze_cube_crafting` recipe type,
  which no crafting table can craft).
  **终焉合成器家族**——9×9 终焉动力合成器、终焉锻造器联动；**炽骨立方合成器**（低配：仅无尽贪婪前两档工作台
  + 专属 `blaze_cube_crafting` 配方类型，任何工作台均无法合成）。
- **Extreme drill & water wheel** — drill: 256 SU/RPM, near-instant breaking, Fortune scales with RPM
  (configurable multiplier, default ×5); water wheel: constant 256 RPM, 5× vanilla base capacity, doubled with fluid.
  **终焉钻头与水车**——钻头 256 SU/RPM、近瞬挖、时运随转速线性增长（乘区可配置，默认 ×5）；
  水车恒 256 RPM、基础容量为原版水车 5 倍、有流体翻倍。
- **Extreme Crafting Terminal + Connectors** — place connectors behind crafters to form a network; the terminal
  auto-gathers materials from adjacent storage, unfiltered creative crates, or same-channel wireless item funnels
  anchored beside it; recipe list is filtered by network size/shape; pinned recipes auto-craft on redstone signal.
  **终焉动能合成终端 + 连接器**——合成器背面贴连接器组网；终端自动从相邻仓库、未过滤创造板条箱、
  或终端旁同频无线物品漏斗锚点调集材料；配方列表按网络数量/摆放过滤；钉住的配方响应红石信号自动合成。
- **Wireless devices** — frequency-channel item funnels, stress bridges, fluid transmitters, warp tunnels.
  **无线设备**——频道制物品漏斗、应力传输器、流体传输器、折跃隧道。
- JEI recipe categories, connected textures, Flywheel visuals, English & Simplified Chinese localization.
  JEI 配方分类、连接纹理、Flywheel 视觉、中英双语本地化。

## Requirements · 依赖

Minecraft 1.21.1 · NeoForge 21.1.x · Create 6.x · Re-Avaritia 1.4.x · AvaritiaIntegration (core + create) · JEI (optional).

## Build & verify · 构建与验证

```bash
./gradlew build          # compile + jar
./gradlew runData        # regenerate datagen resources
# isolated server self-tests (opt-in switches, dedicated game dirs):
./gradlew runServer --offline --init-script gradle/verification/run-crafting-terminal-verify.init.gradle
./gradlew runServer --offline --init-script gradle/verification/run-blaze-crafter-verify.init.gradle
./gradlew runServer --offline --init-script gradle/verification/run-extreme-kinetics-verify.init.gradle
```

Design notes and verification records live in [`docs/`](docs/) (Chinese).
设计稿与验证记录见 [`docs/`](docs/)（中文）。

> **Asset status · 资产状态**: bundled textures are placeholders copied from Create / vanilla pending original art;
> they are **not** covered by this project's asset license (see below).
> 内置贴图为复制自 Create / 原版的占位图，等待原创美术替换；**不**适用本项目资产许可（见下）。

## License · 许可

- **Code / 代码**: [MIT](LICENSE).
- **Original assets / 原创资产** (project-authored textures, models, sounds):
  [CC BY-NC-SA 4.0](LICENSE-ASSETS).
- **Third-party placeholders / 第三方占位资产** (Create, Minecraft vanilla, Re-Avaritia, AvaritiaIntegration
  textures/models copied as placeholders): remain under their original authors' licenses and are excluded
  from the CC grant. 仍归原作者许可约束，不在 CC 授权范围内。