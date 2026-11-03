# 20 · `net.createmod.ponder.api.element` 逐条 API

> 签名来自 `javap -public`。这个包分两类东西：
> **① 元素句柄/接口**（你在剧情里持有的东西，如 `ElementLink<WorldSectionElement>`）；
> **② 构建器**（`TextElementBuilder` / `InputElementBuilder`，链式配置文字气泡与操作提示）。

## 继承关系（javap 输出里能直接读到）

~~~
PonderElement
├── PonderSceneElement
│   ├── AnimatedSceneElement            (forceApplyFade/setFade/setFadeVec)
│   │   ├── WorldSectionElement
│   │   ├── MinecartElement
│   │   └── ParrotElement
│   └── TrackedElement<T>
│       └── EntityElement  = TrackedElement<Entity>
└── PonderOverlayElement
    └── AnimatedOverlayElement          (setFade/getFade + render x2)
~~~

---

## ElementLink

#### `getId()：UUID`
- 元素在场景里的唯一 id。

#### `cast(PonderElement)：T`
- 把随便一个元素转成 `T`（内部带类型校验，推断）。
- 用途：一般不用自己调，`PonderScene.resolve` 会用它。

> 怎么拿到 `ElementLink`？只能从 `world().showIndependentSection(...)`、`world().createEntity(...)`、
> `world().createItemEntity(...)`、`special().createBirb(...)`、`special().createCart(...)` 的返回值拿。

---

## PonderElement

所有元素的最小接口。**你自己实现它没有意义**（渲染管线不认识），只看前三个 default 方法即可知道生命周期。

#### `whileSkipping(PonderScene)：void`（default）
- 用户点「跳过」时调用。

#### `tick(PonderScene)：void`（default）
- 每 tick 调用。

#### `reset(PonderScene)：void`（default）
- 重播/重置时调用。

#### `isVisible()：boolean`（abstract）
#### `setVisible(boolean)：void`（abstract）
- 可见性开关。

---

## PonderSceneElement

参与**世界空间**渲染的元素。

#### `renderFirst(PonderLevel, MultiBufferSource, GuiGraphics, float)：void`
- 普通图层**之前**渲染。

#### `renderLayer(PonderLevel, MultiBufferSource, RenderType, GuiGraphics, float)：void`
- 按指定 `RenderType` 渲染（每个 render type 调一次）。

#### `renderLast(PonderLevel, MultiBufferSource, GuiGraphics, float)：void`
- 之后渲染。
- 坑：这三个方法的第 4 个 `float` 是 partialTick。

---

## PonderOverlayElement

参与 **HUD 叠加层**渲染的元素。

#### `render(PonderScene, PonderUI, GuiGraphics, float)：void`
- 在 Ponder 界面上画东西（文字气泡就是这一族）。

---

## AnimatedSceneElement

可淡入淡出的场景元素（`WorldSectionElement`/`MinecartElement`/`ParrotElement` 都继承它）。

#### `forceApplyFade(float)：void`
- 强制把淡入淡出进度设成指定值。

#### `setFade(float)：void`
- 设置淡入淡出进度（0~1）。

#### `setFadeVec(Vec3)：void`
- 设置淡入的移动方向向量（配合 direction 使用）。

---

## AnimatedOverlayElement

可淡入淡出的叠加元素。

#### `setFade(float)：void`
#### `getFade(float)：float`
- 读/写淡入进度（`getFade` 带 partialTick 参数）。

#### `render(PonderScene, PonderUI, GuiGraphics, float)：void`（default）
- 4 参版本，默认实现会自己调下面的 5 参版本（推断）。

#### `render(PonderScene, PonderUI, GuiGraphics, float, float)：void`（abstract）
- 5 参版本（多一个淡入进度）。

---

## TrackedElement<T>

跟踪一个真实对象的元素。

#### `ifPresent(Consumer<T>)：void`
- 如果被跟踪对象还在，就执行回调。
- 典型：`ent.ifPresent(e -> e.setPos(...))`。

#### `isStillValid(T)：boolean`（default）
- 判断对象是否还有效（默认实现未验证，可能是「非 null 且 isAlive」）。

---

## EntityElement

`interface EntityElement extends TrackedElement<Entity>` —— **没有任何自己的方法**。
- 用途：`world().createEntity(...)` 返回它；`world().modifyEntity(link, consumer)` 消费它。

---

## WorldSectionElement

一块被独立控制的建筑。16 个方法（全部来自 `javap -public`）：

#### `mergeOnto(WorldSectionElement)：void`
- 把自己合并进另一个区块（自己消失）。

#### `set(Selection)：void`
- 重设区块包含的方块集合。

#### `add(Selection)：void`
- 追加方块。

#### `erase(Selection)：void`
- 移除方块。

#### `setCenterOfRotation(Vec3)：void`
- 设置旋转中心。对应 `world().configureCenterOfRotation`。

#### `stabilizeRotation(Vec3)：void`
- 对应 `world().configureStabilization`（语义**未验证**）。

#### `selectBlock(BlockPos)：void`
- 单独选中一个方块（用于「只高亮其中一格」的视觉效果，推断）。

#### `resetSelectedBlock()：void`
- 清掉上面的单独选中。

#### `queueRedraw()：void`
- 请求重新构建该区块的渲染缓冲（改完方块后调）。

#### `isEmpty()：boolean`
#### `setEmpty()：void`
- 判空 / 清空。

#### `setAnimatedRotation(Vec3, boolean)：void`
- 设置动画旋转（第2参含义**未验证**，推断为「是否立即生效」）。

#### `getAnimatedRotation()：Vec3`
#### `setAnimatedOffset(Vec3, boolean)：void`
- 设置动画位移。
#### `getAnimatedOffset()：Vec3`

#### `rayTrace(PonderLevel, Vec3, Vec3)：Pair<Vec3, BlockHitResult>`
- 在该区块的方块上做射线检测，返回命中点与命中结果。

---

## MinecartElement

#### `setPositionOffset(Vec3, boolean)：void`
#### `setRotation(float, boolean)：void`
- 第2参含义**未验证**（推断为「是否立即生效」）。

#### `getPositionOffset()：Vec3`
#### `getRotation()：Vec3`
- 注意：`setRotation` 收的是 `float`，`getRotation` 返回的是 `Vec3`（不是对称的一对）。
- 坑：这是本包最容易看错的一处签名。

---

## MinecartElement.MinecartConstructor

@FunctionalInterface：

#### `create(Level, double, double, double)：AbstractMinecart`
- 工厂。典型 lambda：`(w, x, y, z) -> new Minecart(w, x, y, z)`。

---

## ParrotElement

#### `setPositionOffset(Vec3, boolean)：void`
#### `setRotation(Vec3, boolean)：void`
- 鹦鹉的旋转是 **Vec3**（三轴），与矿车的 `float` 不同。

#### `getPositionOffset()：Vec3`
#### `getRotation()：Vec3`
#### `setPose(ParrotPose)：void`
- 直接换姿态对象（`special().changeBirbPose(...)` 走的是 Supplier 版本）。

---

## ParrotPose

抽象类（不是接口），有 5 个内置子类。

#### `ParrotPose()`（构造器）
- 无参，可自由继承。

#### `tick(PonderScene, Parrot, Vec3)：void`（abstract）
- 每 tick 调整鹦鹉。第3参是该鹦鹉的位置偏移。

#### `create(PonderLevel)：Parrot`
- 生成一只属于这个姿态的鹦鹉实体（非抽象，可被覆盖）。

### 内置子类（`javap -public` 全部列出）

| 子类 | 自身方法 | 用途（推断自类名） |
| --- | --- | --- |
| `ParrotPose.DancePose` | `DancePose()`、`create(PonderLevel)`、`tick(...)` | 跳舞 |
| `ParrotPose.FaceVecPose`（abstract） | `FaceVecPose()`、`tick(...)` | 「朝向某个向量」的基类 |
| `ParrotPose.FaceCursorPose` | `FaceCursorPose()` | 朝向玩家光标 |
| `ParrotPose.FacePointOfInterestPose` | `FacePointOfInterestPose()` | 朝向 `movePointOfInterest` 设定的点 |
| `ParrotPose.FlappyPose` | `FlappyPose()`、`tick(...)` | 扇翅膀 |

- 坑：`FaceCursorPose`/`FacePointOfInterestPose` **没有**重写 `tick` 的 public 签名，说明它们只继承 `FaceVecPose.tick`。

---

## TextElementBuilder

从 `overlay().showText(int)` / `showOutlineWithText(Selection, int)` 拿到。全部返回 `TextElementBuilder`（链式）。

#### `colored(PonderPalette)：TextElementBuilder`
- 文字颜色（用 `PonderPalette` 的 10 个常量）。

#### `pointAt(Vec3)：TextElementBuilder`
- 让气泡指着一个世界坐标（带小尾巴）。

#### `independent(int)：TextElementBuilder`
- 独立显示 N tick（不受场景进度影响），第1参是 tick 数。

#### `independent()：TextElementBuilder`（default）
- 无参版本（内部用某个默认时长，**未验证**具体值）。

#### `text(String)：TextElementBuilder`
- 直接给定文本。**会自动分配 `text_N` 语言键**（`PonderScene.registerText` 路径）。

#### `text(String, Object...)：TextElementBuilder`
- 带 `%s` 参数填充的文本。

#### `sharedText(ResourceLocation)：TextElementBuilder`
- 引用共享文本（key 为 `ResourceLocation`）。

#### `sharedText(ResourceLocation, Object...)：TextElementBuilder`
- 带参数填充。

#### `sharedText(String)：TextElementBuilder`
- 纯字符串 key 版本（内部拼成 `<ns>.ponder.shared.<key>`，推断自 `LangRegistryAccess.getShared` 的用法）。

#### `sharedText(String, Object...)：TextElementBuilder`
- 带参数填充。

#### `placeNearTarget()：TextElementBuilder`
- 气泡贴着目标放（而不是在屏幕中部）。

#### `attachKeyFrame()：TextElementBuilder`
- 在这条文字上打一个关键帧（进度条节点）。

- 坑：`.text()` 与 `.sharedText()` **互斥**：前者占 `text_N` 编号，后者不占（已核实 `sharedText` 不走 `registerText`）。

---

## InputElementBuilder

从 `overlay().showControls(Vec3, Pointing, int)` 拿到。全部返回 `InputElementBuilder`（链式）。

#### `withItem(ItemStack)：InputElementBuilder`
- 在提示里画一个物品图标。

#### `leftClick()：InputElementBuilder`
- 左键图标。

#### `rightClick()：InputElementBuilder`
- 右键图标。

#### `scroll()：InputElementBuilder`
- 滚轮图标。

#### `showing(ScreenElement)：InputElementBuilder`
- 自定义图标（`catnip.gui.element.ScreenElement`，例如 `PonderGuiTextures` 的常量）。
- 这是**唯一**能放非标准图标（按键/自定义贴图）的入口。

#### `whileSneaking()：InputElementBuilder`
- 附一个「潜行时」的前置图标。

#### `whileCTRL()：InputElementBuilder`
- 附一个「按住 Ctrl」的前置图标。

- 坑：`leftClick`/`rightClick`/`scroll`/`showing` 通常是**选一个**；再叠加 `whileSneaking`/`whileCTRL` 表示组合键。

---

## 交叉引用

- 这些元素在剧情里怎么被创建/使用 → [10-scene.md](10-scene.md)
- `PonderPalette` 颜色值 → [40-foundation-others.md](40-foundation-others.md#ponderpalette)
