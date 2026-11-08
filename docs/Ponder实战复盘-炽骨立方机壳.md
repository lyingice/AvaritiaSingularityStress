# Ponder 场景实战复盘：炽骨立方机壳

> 目标读者：要优化 Ponder 预设的 AI / 开发者。
> 本文所有结论都标了来源等级：**【字节码】**= javap 反编译核实 · **【实测】**= 游戏里跑过 · **【推断】**= 有证据但没逐行验证。
> 环境：NeoForge 21.1.232 / MC 1.21.1 / Java 21 / Ponder 1.0.82 / Create 6.0.10-280。

---

## 0. 最终成品

一个场景：`avaritia_singularity_stress:blaze_cube_casing`
- 标题「给齿轮装壳」
- 5×5 棋盘格地板（雪块 + 白色混凝土）
- 一条动力线：轴 · 小齿轮 · 轴 · 大齿轮 · 轴
- 两次右键包壳：小齿轮 → 齿轮箱，大齿轮 → 大齿轮箱
- 一句台词：「炽骨立方机壳，可以用来装饰齿轮」
- 包壳后的齿轮齿盘会转

文件清单（5 处）：

| 文件 | 职责 |
| --- | --- |
| `ponder/BlazeCubePonderScenes.java` | 剧本 + 注册 |
| `ponder/ASPonderPlugin.java` | 插件入口，`getModId()` 决定 lang 前缀和 nbt 命名空间 |
| `ponder/ASPonderClientSetup.java` | `FMLClientSetupEvent` 里 `PonderIndex.addPlugin` |
| `assets/<ns>/ponder/blaze_cube_casing.nbt` | 结构（脚本生成，别手改） |
| `init/provider/ModLanguageProvider*.java` | 中英文案 |

---

## 1. 推荐工作流（按这个顺序，能避开大部分坑）

1. **定剧本 id**（如 `blaze_cube_casing`）。它同时是：nbt 文件名、lang 键中段、`addStoryBoard` 第二参。
2. **先用脚本生成 `.nbt`**，坐标写在脚本里（本文用 `tools/gen_ponder_schematic.py`）。
3. **写 Java 剧本**，`util.grid().at(x,y,z)` 必须与脚本坐标逐一对齐。
4. **注册**：`helper.addStoryBoard(组件 id, "剧本id", 方法引用, 标签)`。
5. **文案写进 provider**（不是 generated json）。
6. `./gradlew compileJava processResources runData --offline`
7. 进游戏 `/ponder <ns>:<剧本id>` 验收。

---

## 2. 十一个坑

### 坑 1｜剧情 `.nbt` 不存在 → 场景全空，但**不报崩**

- 症状：`/ponder` 能打开，底板有、方块没有；日志一行 `Ponder schematic missing: <ns>:ponder/x.nbt`。
- 根因：Ponder 按 `"ponder/" + 剧本id + ".nbt"` 去 `assets/<ns>/` 找，**命名空间取插件的 `getModId()`**。
- 解法：路径 `src/main/resources/assets/<modid>/ponder/<剧本id>.nbt`，且 `addStoryBoard` 第二参传**纯 path**（不要带命名空间）。
- 【字节码】`PonderSceneRegistry.loadSchematic` 常量池 `"ponder/\u0001.nbt"`。

### 坑 2｜孤立的方块不转、也不画

- 症状：场景里放一根单独的 `create:shaft`，`setKineticSpeed(单格, 32)` 之后画面纹丝不动；包壳后更是什么都没有。
- 根因：Create 的 kinetic 方块要靠连通关系构成动力网络，孤立方块没有可渲染的转动状态。【推断，但实测有效】
- 解法：**至少连成 3 格以上的一条线**（本文用 轴·小齿轮·轴·大齿轮·轴）。

### 坑 3｜`setBlocks` 换方块后，被换掉的那格当场停转

- 症状：右键包壳成功，但新方块不转了。
- 根因：`setBlocks` 新建的方块实体 speed 为 0，不继承旧 BE 的状态。
- 解法：**每次 `setBlocks` 后紧跟一次 `setKineticSpeed(整条线, 32)`**。
- 【字节码】Create 原版 `KineticsScenes.shaftsCanBeEncased` 里每次 `setBlocks` 后都有一句 `setKineticSpeed(shaftLine, 32.0f)`。

### 坑 4｜两个包壳点相邻，糊成一个 2 格宽的方块

- 解法：包壳点**隔开一格**（原版就是 x=1 与 x=3）。

### 坑 5｜Ponder 里包壳方块只剩外壳（本次最大的坑）

- 症状：游戏世界里齿轮箱能看到齿盘，Ponder 场景里只剩机壳外壳。
- 根因链（全部核实过）：
  1. 【字节码】ponder jar 里 **0 处 `VisualizationManager`** → Flywheel visual 只挂 `ClientLevel`，Ponder 的假世界 `PonderLevel` 拿不到，visual 整个不参与。
  2. 【字节码】ponder jar 里 `WorldSectionElementImpl` 引用了 `BlockEntityRenderDispatcher` → Ponder 渲染方块实体走 **BER**。
  3. 【字节码】Ponder 画方块本体用 `VirtualRenderHelper.blockModel(BlockState)` + catnip `BakedModelBuffererImpl` → 只画 **baked model**。
  4. 【字节码】`create:block/encased_cogwheel/block.json` 拆开只有 4 个 element（下半/中间层/内缩副/上半），**没有齿**；create jar 里也不存在 `CogwheelRenderer`（只有 `KineticBlockEntityRenderer` 和 `ShaftRenderer`）。
  5. 【推断】所以 Create 官方场景里包壳齿轮同样看不到齿。
- 解法：写一个 BER，**只在非 `ClientLevel` 时画**：

```java
public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                   int packedLight, int packedOverlay) {
    if (be.getLevel() instanceof ClientLevel) return;   // 游戏内交给 visual，绝不双份
    Direction.Axis axis = be.getBlockState().getValue(RotatedPillarKineticBlock.AXIS);
    BlockState modelState = 普通方块.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, axis);
    SuperByteBuffer buffer = CachedBuffers.block(modelState);
    buffer = KineticBlockEntityRenderer.standardKineticRotationTransform(buffer, be, packedLight);
    buffer.renderInto(poseStack, bufferSource.getBuffer(RenderType.cutoutMipped()));
}
```

- 关键取舍：**渲染"普通方块"的模型**（`AllBlocks.SHAFT` / `COGWHEEL` / `LARGE_COGWHEEL`），不要用 partial model。因为普通齿轮模型的齿**故意伸出方块边界**——小齿轮 `[-1,6.5,6.5]→[17,9.5,9.5]`，大齿轮 `[-7…23]`，而包壳外壳只有 0..16，齿才能露出来。
- 另外要重写 `getRenderBoundingBox` 放宽一格，否则伸出部分的齿可能被裁剪。

### 坑 6｜包壳轴的 `opening` 纹理填错，看起来像实心方块

- 症状：包壳轴没有"轴孔"，比原版更闷。
- 根因：`encased_shaft/block.json` 里有个 `Core` element 专吃 `#opening` 纹理；本仓库原先把它填成了外壳纹理，等于把孔糊死。
- 解法：照官方——安山填 `create:block/gearbox`，黄铜填 `create:block/brass_gearbox`。
- 【字节码】两条都是直接从 create jar 的模型 json 里读出来的。

### 坑 7｜lang 走 datagen，手改 generated json 会被覆盖

- 症状：改了 `src/generated/resources/.../zh_cn.json`，下次 `runData` 后改动消失。
- 解法：改 `init/provider/ModLanguageProviderZhCn.java` / `ModLanguageProvider.java`，再 `./gradlew runData`。
- 附带：**中文改了英文也要改**，否则切英文的玩家看到旧文案。

### 坑 8｜文案 AI 味（被用户当面骂了两次）

这是本次最不技术、但最影响观感的部分。

**Create 官方台词样本**（从 create jar 的 `zh_cn.json` 扒的，原文）：

| 键 | 官方中文 |
| --- | --- |
| `create.ponder.shaft_casing.header` | 给传动杆装壳 |
| `create.ponder.shaft_casing.text_1` | 安山机壳或黄铜机壳，可以用来装饰传动杆 |
| `create.ponder.cogwheel_casing.header` | 给齿轮装壳 |
| `create.ponder.cogwheel_casing.text_1` | 安山机壳或黄铜机壳，可以用来装饰齿轮 |

**提炼出的规则**：

1. **标题是动作短语，不是名词**。「给齿轮装壳」而不是「炽骨立方机壳」——标题回答"这玩意儿拿来干嘛"。
2. **台词 = 主语（什么材料）+ 可以用来 + 用途 + 目标**，零形容词、零评价。
3. **不替玩家下结论**。"纯粹是装饰"是冒犯（等于说他看不出来），"可以用来装饰"才是陈述。
4. **不堆强调词**：纯粹 / 都 / 也一样 / 仅仅 / 完全 —— 全是 AI 味标记。
5. **一句只说一件事**，官方 `shaft_casing` 整场只有一句台词。
6. **说了的必须演到**。想在台词里提大齿轮，画面里就得真有大齿轮；演不出来（比如"装壳后组件不与传动轴相连"）就不要写。
7. **先演后讲**：官方是整场演完才出一句总结，`title` 在最开始。
8. 官方**不用** `attachKeyFrame()`（可以自己加，但别以为必须）。

### 坑 9｜游戏开着 → `AccessDeniedException`，编译失败

- 症状：`./gradlew compileJava` 在 `:createMinecraftArtifacts` 挂掉：

```
java.nio.file.AccessDeniedException: build\moddev\artifacts\neoforge-21.1.232.jar.tmp -> ...jar
```

- 根因：运行中的客户端锁住了这个 jar。
- 解法：关游戏；或用 `./gradlew compileJava -x createMinecraftArtifacts` 绕过（jar 已存在时可行）。

### 坑 10｜自动化脚本送不进键盘事件

- 现象：`SetForegroundWindow` 返回 true，但前台随即被别的窗口抢回；`keybd_event` / SendKeys 发的 `T`、`F3` 全无反应（连 F3 调试屏都不出）。
- 可用：`PrintWindow` 抓图**不需要焦点**，所以截图可以自动化。
- 结论：**Ponder 的实机验证只能人工操作，或给 mod 加临时触发钩子**（读一个文件、进世界后自动执行命令）。别指望脚本按键。

### 坑 11｜Java 坐标与 nbt 坐标是两份数据

- 症状：方块没出现、或者出现在别处。
- 根因：`.nbt` 里是局部坐标，剧本里是 `util.grid().at(x,y,z)`，两者必须逐格对齐。
- 解法：坐标只在**一个地方**定义（脚本里），Java 侧用常量引用同一组数字；改布局两边一起改。

---

## 3. 可复用的检查清单

写/改一个 Ponder 场景后，按这个顺序自查：

- [ ] 剧本 id 三处一致：`addStoryBoard` 第二参、`.nbt` 文件名、`scene.title` 第一参
- [ ] `.nbt` 在 `assets/<getModId()>/ponder/` 下
- [ ] Java 的 `grid().at()` 与脚本坐标逐格对齐
- [ ] kinetic 方块连成线，不是孤立单格
- [ ] 每次 `setBlocks` 后补 `setKineticSpeed`
- [ ] 包壳/替换点之间至少隔一格
- [ ] lang 键齐全：`.header`、`.text_<N>`（按 `.text()` 顺序从 1 起）
- [ ] 中文英文都改了，且跑过 `runData`
- [ ] 台词没有说画面演不到的事
- [ ] `./gradlew compileJava processResources runData` 全绿
- [ ] 进游戏 `/ponder <ns>:<id>` 实际看过

---

## 4. 证据怎么查（这套方法比答案更有用）

| 想知道 | 查法 |
| --- | --- |
| Ponder 的 API 签名 | `node dsh-ponder-mode/ponder-search.mjs --api <类名>`（javap 转储） |
| Create 的某个场景怎么写的 | `javap -c -p -cp <create.jar> com.simibubi.create.infrastructure.ponder.scenes.KineticsScenes`，按方法名 grep |
| 原版台词怎么写 | `unzip -p <create.jar> assets/create/lang/zh_cn.json`，grep `create.ponder.<场景>` |
| 场景的 nbt 里有什么方块 | 解 gzip NBT 读 `palette` 字段（本仓库 `tools/gen_ponder_schematic.py` 里有完整的读写实现） |
| 某个方块模型细节 | `unzip -p <jar> assets/create/models/block/xxx.json` |
| Ponder 渲染走哪条路 | 把 ponder jar 解压成目录，`grep -rl "VisualizationManager" .` / `grep -rl "BlockEntityRenderDispatcher" .` |
| 中文说明 | `docs/Ponder-保姆级使用手册.md`、`docs/ponder-research/03-调试与排错.md`（本次已更新 8.5 节） |

---

## 5. 仍未验证 / 边界

1. **Create 官方 `create:cogwheel_casing` 场景里包壳齿轮到底有没有齿** —— 结论是从模型和 jar 内容推断的，没有实机对比截图。
2. **BER 兜底方案**编译通过，但实机画面由用户验收，本文撰写时未亲眼确认。
3. **包壳轴的 BER 兜底**（渲染 `AllBlocks.SHAFT`）未单独验证——场景里没演轴箱。
4. Ponder 里齿轮的"转"是 `getAngleForBe` 按 tick 算出来的静态轮询，不是真模拟；速度只受 `setKineticSpeed` 影响。
5. 大齿轮的 `TOP_SHAFT` / `BOTTOM_SHAFT` 状态没设置（默认 false），如果需要带轴头的造型要自己 `.setValue()`。
