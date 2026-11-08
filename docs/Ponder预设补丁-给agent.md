# Ponder 预设补丁 v2（实战验证）

> 适用：NeoForge 1.21.1 / Ponder 1.0.82 / Create 6.0.10。
> 来源：一次完整的 Create 附属思索场景开发，所有规则都经实机或字节码验证。
> 用法：追加到你的 Ponder 预设里，覆盖与冲突项。

---

## 1. 三条铁律

1. **先抄官方，再写自己的。** 签名用 `javap`，场景分镜反编译 Create 的 scenes 类，文案读 jar 里的 lang，结构拆官方 nbt。任何"记忆里的写法"都不可信。
2. **一次只改一件事，改完立刻编译。** 坐标、文案、分镜不要一起改。
3. **坐标只有一个来源。** 结构坐标写在生成脚本里，Java 侧引用同一组常量。两边各写一份必然对不上。

---

## 2. 开工流程（照做，跳过任一步都会返工）

### 2.1 查签名

```bash
node dsh-ponder-mode/ponder-search.mjs --api <类名>          # 精确签名
node dsh-ponder-mode/ponder-search.mjs <关键词>              # 跨文档搜
```

### 2.2 找同类场景，反编译照着抄

```bash
# Create 的 kinetic 类场景都在这个类里
javap -c -p -cp <create.jar> com.simibubi.create.infrastructure.ponder.scenes.KineticsScenes
```

已确认可抄的关键场景：`shaftsCanBeEncased`（给传动杆装壳）、`cogwheelsCanBeEncased`（给齿轮装壳）。

### 2.3 抄结构

```bash
unzip -p <create.jar> assets/create/ponder/<场景>.nbt > /tmp/x.nbt
# 解 gzip + NBT，读 palette 和 blocks[].pos
```

**已确认事实**：Create 场景的地板不是石头，是 `minecraft:snow_block` 与 `minecraft:white_concrete` 的棋盘格（`(x+z)%2==0` 放雪块，否则白混凝土）。

### 2.4 抄文案

```bash
unzip -p <create.jar> assets/create/lang/zh_cn.json      # grep create.ponder.<场景>
```

---

## 3. 硬规则表

| 规则 | 写错的后果 |
| --- | --- |
| 结构文件放 `assets/<插件getModId()>/ponder/<剧本id>.nbt` | 场景全空，只打一行 `Ponder schematic missing`，**不崩** |
| `addStoryBoard(组件, "剧本id", ...)` 第二参是**纯 path**，命名空间取 `getModId()` | 找不到 nbt |
| lang 键：`<ns>.ponder.<剧本id>.header` / `.text_<N>`（N 从 1、按 `.text()` 调用顺序） | 界面显示原始 key |
| lang 改在 `LanguageProvider` 里，不要手改 `src/generated/**` | 下次 `runData` 被覆盖 |
| 中文改了英文必须一起改 | 切英文的玩家看到旧文案 |
| 场景 id 三处必须一致：`addStoryBoard` 第二参、nbt 文件名、`scene.title` 第一参 | 随机失效 |
| kinetic 方块必须连成线（≥3 格） | 孤立方块不转、包壳后什么都不画 |
| 每次 `setBlocks` 后紧跟一次 `setKineticSpeed` | 被替换的那格当场停转 |
| 两个替换/包壳点至少隔一格 | 视觉上糊成一个大方块 |

---

## 4. 坑 → 立刻这么修

### 4.1 Ponder 里包壳方块只剩外壳（最重要）

**症状**：游戏世界里包壳齿轮有齿盘，Ponder 场景里只有一个空壳。
**根因**：Flywheel visual 只挂 `ClientLevel`，Ponder 的 `PonderLevel` 拿不到 → visual 不参与；Ponder 只画 **baked model** + 调 **BER**。
**修法**：写 BER，**只在非 `ClientLevel` 时画**：

```java
if (be.getLevel() instanceof ClientLevel) return;   // 游戏内交给 visual，绝不双份
Direction.Axis axis = be.getBlockState().getValue(RotatedPillarKineticBlock.AXIS);
BlockState modelState = 普通方块.defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, axis);
SuperByteBuffer buffer = CachedBuffers.block(modelState);
buffer = KineticBlockEntityRenderer.standardKineticRotationTransform(buffer, be, packedLight);
buffer.renderInto(poseStack, bufferSource.getBuffer(RenderType.cutoutMipped()));
```

**关键取舍**：画**普通方块**的模型（`AllBlocks.SHAFT` / `COGWHEEL` / `LARGE_COGWHEEL`），不要用 partial model——普通齿轮的齿故意伸出方块边界（小齿轮 `[-1..17]`、大齿轮 `[-7..23]`），包壳外壳只有 `0..16`，齿才露得出来。另外重写 `getRenderBoundingBox` 放宽一格。

### 4.2 包壳轴看起来是实心

`encased_shaft/block.json` 的 `#opening` 槽位要填齿轮箱纹理：安山 `create:block/gearbox`、黄铜 `create:block/brass_gearbox`。填成外壳纹理等于把轴孔糊死。

### 4.3 编译报 `AccessDeniedException`（neoforge jar）

游戏客户端开着锁住了 `build/moddev/artifacts/neoforge-*.jar`。
修：关游戏，或 `./gradlew compileJava -x createMinecraftArtifacts`。

### 4.4 脚本送不进键盘事件

`SetForegroundWindow` 会失败、`keybd_event` 发的键进不了 GLFW 窗口。
**可用**：`PrintWindow` 抓图不需要焦点，截图可自动化；**不可用**：自动按键。
修：实机验证人工做，或给 mod 加临时文件触发钩子。

---

## 5. 文案规范（最容易被用户骂的地方）

**官方样本**（create jar 原文，照这个写）：

| 键 | 官方中文 |
| --- | --- |
| `create.ponder.shaft_casing.header` | 给传动杆装壳 |
| `create.ponder.shaft_casing.text_1` | 安山机壳或黄铜机壳，可以用来装饰传动杆 |
| `create.ponder.cogwheel_casing.header` | 给齿轮装壳 |
| `create.ponder.cogwheel_casing.text_1` | 安山机壳或黄铜机壳，可以用来装饰齿轮 |

**规则**：

1. **标题是动作短语**（「给齿轮装壳」），不是方块名（「炽骨立方机壳」）。
2. **台词 = 材料 + 可以用来 + 用途 + 目标**，零形容词、零评价。
3. **不替玩家下结论**：「纯粹是装饰」是冒犯，「可以用来装饰」才是陈述。
4. **禁强调词**：纯粹 / 都 / 也一样 / 仅仅 / 完全 / 非常 —— 全是 AI 味标记。
5. **一句只说一件事**。官方 `shaft_casing` 整场只有一句台词。
6. **说了必须演到**。台词提到大齿轮，画面就得有大齿轮；演不出来的（如"装壳后组件不与传动轴相连"）不要写。
7. **先演后讲**：整场演完才出总结句。
8. `attachKeyFrame()` 可加可不加，官方没加。

---

## 6. 交付前自查

- [ ] 剧本 id 三处一致
- [ ] `.nbt` 在 `assets/<getModId()>/ponder/`
- [ ] Java `grid().at()` 与脚本坐标逐格对齐
- [ ] kinetic 连成线
- [ ] 每次 `setBlocks` 后补 `setKineticSpeed`
- [ ] 替换点隔开一格
- [ ] `.header` + `.text_<N>` 齐全且编号对
- [ ] 中英文都改了、跑过 `runData`
- [ ] 台词没说画面演不到的事
- [ ] `./gradlew compileJava processResources runData` 全绿
- [ ] 进游戏 `/ponder <ns>:<id>` 实际看过

---

## 7. 证据标注规范

给用户/上层报告时，每条结论标等级：

- **【字节码】** javap / 解 jar 核实
- **【实测】** 游戏里跑过
- **【推断】** 有依据但未逐行验证

**不要把推断写成事实。** 本次遗留的推断：Create 官方 `cogwheel_casing` 场景里包壳齿轮同样没齿（由模型无齿 + 无 CogwheelRenderer 推出，未实机对比）。

---

## 8. 不要做的事

1. 不要手改 `src/generated/**`（datagen 产物）。
2. 不要用孤立方块演 kinetic。
3. 不要在 Ponder 场景里指望 Flywheel visual 生效。
4. 不要在没有 `.nbt` 的情况下以为场景能显示方块。
5. 不要替用户写"总结陈词"式文案。
6. 不要在游戏运行时编译（会锁 jar）。

---

## 9. 包壳/复杂方块在 Ponder 里的渲染（进阶）

### 9.1 铁律

**Ponder 只画 baked model + 调 BER，不跑 Flywheel visual。**

证据（全部核实过）：

- ponder jar 里 **0 处 `VisualizationManager`** → visual 系统整个不参与
- 同 jar 里 `WorldSectionElementImpl` 引用了 `BlockEntityRenderDispatcher` → 方块实体走 BER
- 方块本体走 `VirtualRenderHelper.blockModel(BlockState)` + catnip `BakedModelBuffererImpl`

### 9.2 兜底 BER 模板

```java
public class PonderKineticFallbackRenderer<T extends KineticBlockEntity> implements BlockEntityRenderer<T> {
    private final Supplier<PartialModel> model;   // 必须用 Supplier，partial model 要等资源加载

    @Override
    public void render(T be, float pt, PoseStack poseStack, MultiBufferSource bufferSource,
                       int light, int overlay) {
        if (be.getLevel() instanceof ClientLevel) return;   // 游戏内交给 visual，绝不双份
        // ... 见 9.5
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(8);       // 见 9.6
    }
}
```

注册在 `EntityRenderersEvent.RegisterRenderers` 里。

### 9.3 方块模型 vs partial model（本次最大的坑）

| 用哪个 | 朝向谁处理 | 材质 |
| --- | --- | --- |
| `CachedBuffers.block(BlockState)` | blockstate 的 variants 旋转**烘在模型里**，自动对 | 方块自己那套 |
| `CachedBuffers.partial(PartialModel, state)` | **裸模型**，`state` 只当缓存 key，**必须自己摆正** | partial model 那套 |

想要自定义材质 → 只能用 partial model → 必须自己处理朝向。

### 9.4 怎么判断 partial model 的默认轴

**看模型 json 的 element**：几个长条绕哪个轴旋转排开，轴就是哪个。

例：`blaze_cube/cogwheel_shaftless.json` 里 4 个齿条分别绕 **Y** 转 0°/45°/−45°/90° → 模型轴朝 **Y**。
而方块是 `axis=x` → 差 90°，必须转。

### 9.5 自转 + 摆正的正确顺序（踩了两次才顺）

```java
Direction.Axis axis = state.getValue(RotatedPillarKineticBlock.AXIS);

// 1) 自转：绕**模型自己的轴**（Y），不是方块的轴
float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), axis);
SuperByteBuffer buffer = CachedBuffers.partial(model.get(), state);
buffer = KineticBlockEntityRenderer.kineticRotationTransform(
        buffer, be, Direction.Axis.Y, angle, light);

// 2) 摆正：外层 PoseStack 绕方块中心转，中心用 translate(±0.5) 写死
poseStack.pushPose();
poseStack.translate(0.5, 0.5, 0.5);
if (axis == Direction.Axis.X) {
    poseStack.mulPose(Axis.ZP.rotationDegrees(90));
} else if (axis == Direction.Axis.Z) {
    poseStack.mulPose(Axis.XP.rotationDegrees(90));
}
poseStack.translate(-0.5, -0.5, -0.5);
buffer.renderInto(poseStack, bufferSource.getBuffer(RenderType.cutoutMipped()));
poseStack.popPose();
```

三条硬规则：

1. **自转轴用模型自己的轴**。用方块轴转（`standardKineticRotationTransform`）再摆正，转的就不是模型自己的轴，视觉上是歪的。
2. **摆正用 `PoseStack.translate(±0.5)`**，不要用 `buffer.rotateCentered(...)` —— 后者绕的中心是它自己假设的（0..1 归一化），partial model 的顶点空间不一定一致，会整体漂。
3. **顺序不能反**：先在模型空间自转，再整体摆正。

### 9.6 包围盒按模型实际伸出范围

`getRenderBoundingBox` 默认只有方块那 1 格。模型的齿伸出去多远，就得放宽多少：

- 小齿轮齿 `x: -1..17` → `inflate(1)` 勉强够
- 大齿轮齿 `x: -7..23` → 必须 `inflate(8)`，否则大半被裁，看起来像"缩在方块一边"

### 9.7 常见编译错误

- **`Direction.Z` 不存在**。`Direction` 只有 `NORTH/SOUTH/EAST/WEST/UP/DOWN`；轴是 `Direction.Axis.X/Y/Z`。
  转轴方向要写：`Direction.get(Direction.AxisDirection.POSITIVE, Direction.Axis.Z)`，或者 `com.mojang.math.Axis.ZP`。
- 用 `Direction.Axis` 和 `Direction` 时注意别混：`getAngleForBe` 要 `Axis`，`rotateCentered` 要 `Direction`。

### 9.8 一个场景挂多个组件（顺带）

```java
helper.forComponents(itemA, itemB, itemC)
      .addStoryBoard(sceneId, board, TAG);
```

- 每个组件各生成一份场景条目，**共用同一个 `.nbt` 和同一套 lang**
- 玩家在物品栏悬浮某物品按 W 能不能开思索，取决于**那个物品 id 有没有注册过场景**；只注册了齿轮箱，悬浮机壳当然没反应
- 机壳可能来自别的模组：用 `((EncasedBlock) 包壳方块).getCasing().asItem()` 反查，别硬编码命名空间
