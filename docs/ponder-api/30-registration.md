# 30 · `net.createmod.ponder.api.registration` 逐条 API

> 签名来自 `javap -public`。语义证据写在每条下面；「推断」「未验证」标注同前。

## 这个包解决什么问题

Ponder 的注册是**两段式**：

1. 你实现 `PonderPlugin`（唯一必须写的方法是 `getModId()`）；
2. Ponder 在注册阶段遍历所有插件的回调，把你声明的东西填进三个静态注册表：
   场景（`PonderSceneRegistry`）、标签（`PonderTagRegistry`）、语言（`PonderLocalization`）。

回调只有三个（都有 default 空实现）：`registerScenes` / `registerTags` / `registerSharedText`，
外加两个可选钩子：`onPonderLevelRestore` / `indexExclusions`。

---

## PonderPlugin

#### `getModId()：String`
- **唯一抽象方法**。返回你的 modid（如 `"mymod"`）。
- 用途：决定剧情/标签语言键的命名空间，也是 `addStoryBoard(组件, "场景名", ...)` 自动补的命名空间。

#### `registerScenes(PonderSceneRegistrationHelper<ResourceLocation>)：void`（default）
- 在这里注册所有剧情。默认空实现。

#### `registerTags(PonderTagRegistrationHelper<ResourceLocation>)：void`（default）
- 在这里注册所有标签、以及标签与物品的关联。默认空实现。

#### `registerSharedText(SharedTextRegistrationHelper)：void`（default）
- 在这里注册跨场景复用的文本（`.sharedText("key")` 引用的那些）。默认空实现。

#### `onPonderLevelRestore(PonderLevel)：void`（default）
- 假世界被重建/恢复时回调，可用于把你的方块实体状态写回假世界。默认空实现。
- **未验证**：Ponder 内部在哪些时机触发它（字节码只能证明签名）。

#### `indexExclusions(IndexExclusionHelper)：void`（default）
- 声明「哪些物品不要出现在 Ponder 索引界面」。默认空实现。

---

## 注册时机

### 已核实（来自 `PonderIndex` / `PonderSceneRegistry` 字节码）

| 事实 | 证据 |
| --- | --- |
| `PonderIndex.addPlugin(PonderPlugin)` 只是把插件**按 comparator 排序后插入**静态 `List<PonderPlugin>`，不立即注册 | `addPlugin` 字节码：`Collections.binarySearch` + `List.add(index, plugin)` |
| `PonderIndex.reload()` = `clearShared()` → `SCENES.clearRegistry()` → `TAGS.clearRegistry()` → `registerAll()` → `gatherSharedText()` | `reload` 字节码 |
| `registerAll()` = 先对每个插件调 `registerScenes`（日志 “Registering Ponder Scenes took {}”），再调 `registerTags`（日志 “Registering Ponder Tags took {}”） | `registerAll` 字节码 |
| `gatherSharedText()` = 对每个插件调 `registerSharedText`（日志 “Collecting Shared Ponder Text took {}”） | `gatherSharedText` 字节码 |
| 注册阶段结束后再往场景表加条目会抛 `IllegalStateException`，消息 `"Registration Phase has already ended!"` | `PonderSceneRegistry` 常量池 |

### 未验证

- **`addPlugin` 应该在哪个 FML 事件里调用**：字节码里没有事件绑定信息。
- 插件之间的注册顺序：`binarySearch` + comparator 说明**有排序**，但 comparator 规则**未验证**。

---

## PonderSceneRegistrationHelper

`T` 是「组件」类型。Ponder 传给你的是 `PonderSceneRegistrationHelper<ResourceLocation>`。

#### `<S> withKeyFunction(Function<S, T>)：PonderSceneRegistrationHelper<S>`
- 换组件类型：把 `S` 映射成 `T`，让你直接传 `Block`/`Item` 之类。

#### `addStoryBoard(T, ResourceLocation, PonderStoryBoard, ResourceLocation...)：StoryBoardEntry`
- 第1参：组件 id。
- 第2参：schematic 的 `ResourceLocation`（**完整 id**，含命名空间）。
- 第3参：剧情函数。
- 第4参：标签（可变参数，可省略）。
- 返回：`StoryBoardEntry`，可继续 `.orderBefore(...)`/`.highlightTag(...)`。
- 坑：自己给了命名空间就**不会**被插件 modid 覆盖。

#### `addStoryBoard(T, String, PonderStoryBoard, ResourceLocation...)：StoryBoardEntry`
- 第2参是**纯 path**（不带命名空间）。
- 字节码：`asLocation(该字符串)` → `ResourceLocation.fromNamespaceAndPath(插件命名空间, path)`。
- 这是**最常用**的重载。

#### `forComponents(T...)：MultiSceneBuilder`
- 一组组件共用同一批剧情。

#### `forComponents(Iterable<? extends T>)：MultiSceneBuilder`
- 集合版本。

#### `asLocation(String)：ResourceLocation`
- 把纯 path 变成 `<插件命名空间>:<path>`。

> **schematic 文件路径（已核实）**：`PonderSceneRegistry.loadSchematic` 用常量 `"ponder/" + path + ".nbt"` 拼路径，
> 所以上面 `"my_scene"` 对应文件是 `assets/<你的modid>/ponder/my_scene.nbt`。

---

## MultiSceneBuilder

由 `forComponents(...)` 返回，链式注册。

#### `addStoryBoard(ResourceLocation, PonderStoryBoard)：MultiSceneBuilder`
- 完整 id + 剧情。

#### `addStoryBoard(ResourceLocation, PonderStoryBoard, ResourceLocation...)：MultiSceneBuilder`
- 追加标签。

#### `addStoryBoard(ResourceLocation, PonderStoryBoard, Consumer<StoryBoardEntry>)：MultiSceneBuilder`
- 用回调配置返回值（排序/高亮标签）。

#### `addStoryBoard(String, PonderStoryBoard)：MultiSceneBuilder`
- 纯 path 版本。

#### `addStoryBoard(String, PonderStoryBoard, ResourceLocation...)：MultiSceneBuilder`
- 纯 path + 标签。

#### `addStoryBoard(String, PonderStoryBoard, Consumer<StoryBoardEntry>)：MultiSceneBuilder`
- 纯 path + 配置回调。

- 坑：返回值是 `MultiSceneBuilder` 本身，可一路链式写下去。

---

## StoryBoardEntry

#### `getBoard()：PonderStoryBoard`
#### `getNamespace()：String`
- 插件命名空间。
#### `getSchematicLocation()：ResourceLocation`
- schematic id（不含 `ponder/` 前缀，前缀在加载时拼）。
#### `getComponent()：ResourceLocation`
- 关联的组件 id。
#### `getTags()：List<ResourceLocation>`
#### `getOrderingEntries()：List<SceneOrderingEntry>`

#### `orderBefore(String)：StoryBoardEntry`（default）
- 第1参：另一个场景的 path（内部补本场景命名空间，推断）。

#### `orderBefore(String, String)：StoryBoardEntry`
- 第1参：命名空间；第2参：场景 path。本场景排它前面。

#### `orderAfter(String)：StoryBoardEntry`（default）
#### `orderAfter(String, String)：StoryBoardEntry`
- 同上，排在其后。

#### `highlightTag(ResourceLocation)：StoryBoardEntry`
- 让本场景在索引界面高亮某个标签。

#### `highlightTags(ResourceLocation...)：StoryBoardEntry`
- 多个标签。

#### `highlightAllTags()：StoryBoardEntry`
- 全部标签都高亮。

---

## StoryBoardEntry.SceneOrderingType

枚举，**只有 2 个常量**（`javap -public` 已全部列出）：

| 常量 | 含义（推断自 `before`/`after` 工厂方法名） |
| --- | --- |
| `BEFORE` | 排到目标场景之前 |
| `AFTER` | 排到目标场景之后 |

---

## StoryBoardEntry.SceneOrderingEntry

`java.lang.Record` 子类（1.21 record）。

#### `SceneOrderingEntry(SceneOrderingType, ResourceLocation)`（构造器）
#### `static after(String, String)：SceneOrderingEntry`
#### `static before(String, String)：SceneOrderingEntry`
- 两个 String = (命名空间, 场景 path)。
#### `type()：SceneOrderingType`
#### `sceneId()：ResourceLocation`
#### `toString() / `hashCode() / `equals(Object)`
- record 自动生成。

---

## PonderTagRegistrationHelper

#### `<S> withKeyFunction(Function<S, T>)：PonderTagRegistrationHelper<S>`
- 换组件类型。

#### `registerTag(ResourceLocation)：TagBuilder`
- 第1参：标签完整 id（如 `ResourceLocation.fromNamespaceAndPath("mymod","machines")`）。
- 返回 `TagBuilder`；**必须再调 `.register()`** 才真正生效。

#### `registerTag(String)：TagBuilder`
- 纯 path 版本（自动补插件命名空间）。

#### `addTagToComponent(T, ResourceLocation)：void`
- 第1参：组件 id。第2参：已有标签 id。直接把一个标签挂到一个组件上。

#### `addToTag(ResourceLocation)：MultiTagBuilder.Tag<T>`
- 往这个标签里批量加组件：`helper.addToTag(tagId).add(a).add(b)`。

#### `addToTag(ResourceLocation...)：MultiTagBuilder.Tag<T>`
- 一次指定多个标签，后续 `.add(x)` 会加到**所有**这些标签。

#### `addToComponent(T)：MultiTagBuilder.Component`
- 给这个组件批量加标签：`helper.addToComponent(id).add(tagA).add(tagB)`。

#### `addToComponent(T...)：MultiTagBuilder.Component`
- 多个组件共用同一批标签。

---

## TagBuilder

链式构建一个标签，**最后必须 `register()`**。

#### `title(String)：TagBuilder`
- 显示名（同时会写进语言表 `<ns>.ponder.tag.<path>` 的默认值）。

#### `description(String)：TagBuilder`
- 描述（写入 `<ns>.ponder.tag.<path>.description`）。

#### `addToIndex()：TagBuilder`
- 把标签加入索引界面（不调则不显示）。

#### `icon(ResourceLocation)：TagBuilder`
- 用某个物品作为标签图标（完整 id）。

#### `icon(String)：TagBuilder`
- 纯 path 版本。

#### `idAsIcon()：TagBuilder`
- 用标签 id 自身推导图标（推断：把 tag id 当物品 id 用）。

#### `item(ItemLike, boolean, boolean)：TagBuilder`
- 第1参：物品/方块。第2、3参：两个 boolean，**未验证**（推断与「是否加入索引」「是否显示变体」有关）。

#### `item(ItemLike)：TagBuilder`（default）
- 单参数便捷重载。

#### `register()：void`
- **提交**。不调这个方法，前面所有链式设置全部作废。
- 坑：返回 `void`，所以必须放在链尾单独一句。

---

## MultiTagBuilder

**空接口**（`javap -public` 输出里没有任何成员）。它只作为 `Tag<T>` / `Component` 的命名空间容器。

---

## MultiTagBuilder.Tag<T>

#### `add(T)：MultiTagBuilder.Tag<T>`
- 把下一个组件加进「当前标签集合」。

---

## MultiTagBuilder.Component

#### `add(ResourceLocation)：MultiTagBuilder.Component`
- 把下一个标签加进「当前组件集合」。

---

## IndexExclusionHelper

#### `exclude(ItemLike)：IndexExclusionHelper`
- 把这个物品排除出索引界面。

#### `excludeItemVariants(Class<? extends Item>, Item)：IndexExclusionHelper`
- 第1参：物品类；第2参：基准物品。把该类下与该基准同族的变体一起排除。
- **未验证**：判定「同族」的具体规则。

#### `excludeBlockVariants(Class<? extends Block>, Block)：IndexExclusionHelper`
- 方块版本，同上。

#### `exclude(Predicate<ItemLike>)：IndexExclusionHelper`
- 自定义谓词批量排除。
- 这是**最灵活**的一个，不确定时用它。

---

## SharedTextRegistrationHelper

#### `registerSharedText(String, String)：void`
- 第1参：key（纯 path）。第2参：文本内容。
- 语言键：`<你的modid>.ponder.shared.<key>`（已核实，见「语言键速查」）。
- 之后在剧情里写 `.sharedText("key")` 就能引用。
- 坑：`sharedText` **不占用** `text_N` 编号（`PonderScene.textIndex` 只在 `registerText` 里自增）。

---

## SceneRegistryAccess

获取：`PonderIndex.getSceneAccess()`。

#### `doScenesExistForId(ResourceLocation)：boolean`
- 该组件是否已有剧情（用于「有才显示提示」的判断）。

#### `getRegisteredEntries()：Collection<Map.Entry<ResourceLocation, StoryBoardEntry>>`
- 全部注册项（key = 组件 id）。

#### `compile(ResourceLocation)：List<PonderScene>`
- 编译该组件下的所有场景（返回 `foundation.PonderScene` 列表）。
- 坑：每次调用都会**重新编译**，不要在 tick 里调。

#### `compile(Collection<StoryBoardEntry>)：List<PonderScene>`
- 编译指定的一批注册项。

---

## TagRegistryAccess

获取：`PonderIndex.getTagAccess()`。

#### `getRegisteredTag(ResourceLocation)：PonderTag`
- 按 id 取标签（取不到时返回值**未验证**，可能是 null）。

#### `getListedTags()：List<PonderTag>`
- 索引界面里列出的标签（即调过 `addToIndex()` 的）。

#### `getTags(ResourceLocation)：Set<PonderTag>`
- 某个组件身上的所有标签。

#### `getItems(ResourceLocation)：Set<ResourceLocation>`
- 某个标签下的所有组件 id。

#### `getItems(PonderTag)：Set<ResourceLocation>`
- 同上，参数换成 `PonderTag` 对象。

---

## LangRegistryAccess

获取：`PonderIndex.getLangAccess()`。

#### `provideLang(String, BiConsumer<String, String>)：void`
- 第1参：命名空间；第2参：接收 (key, value) 的回调。
- 用途：把 Ponder 收集到的语言条目导出成你自己的 lang 文件。
- 推断：对应 `PonderLocalization.provideLang`（该方法存在且签名一致）。

#### `getShared(ResourceLocation)：String`
- 取共享文本。

#### `getShared(ResourceLocation, Object...)：String`
- 带 `%s` 参数填充的版本。

#### `getTagName(ResourceLocation)：String`
#### `getTagDescription(ResourceLocation)：String`
#### `getSpecific(ResourceLocation, String)：String`
- 第1参：场景 id；第2参：具体 key（如 `header` / `text_1`）。

#### `getSpecific(ResourceLocation, String, Object...)：String`
- 带参数填充版本。

---

## 语言键速查（已核实）

| 用途 | 键格式 | 例 |
| --- | --- | --- |
| 场景标题 | `<ns>.ponder.<scene>.header` | `mymod.ponder.my_scene.header` |
| 场景内第 N 条自动编号文本 | `<ns>.ponder.<scene>.text_N`，N **从 1 起** | `mymod.ponder.my_scene.text_1` |
| 共享文本 | `<ns>.ponder.shared.<key>` | `mymod.ponder.shared.tip1` |
| 标签名 | `<ns>.ponder.tag.<path>` | `mymod.ponder.tag.machines` |
| 标签描述 | `<ns>.ponder.tag.<path>.description` | `mymod.ponder.tag.machines.description` |

证据：
- `PonderLocalization` 常量池：`\u0001.ponder.shared.\u0001`、`\u0001.ponder.tag.\u0001`、`\u0001.ponder.tag.\u0001.description`、`\u0001.ponder.\u0001.\u0001`；`LANG_PREFIX = "ponder."`、`UI_PREFIX = "ui."`。
- `PonderScene.TITLE_KEY` ConstantValue = `header`；`PonderScene` 常量池另有 `text_\u0001`（`makeConcatWithConstants(int)`）。
- `PonderScene` 构造器里 `textIndex = 1`（字节码 `iconst_1 / putfield textIndex`），`registerText` 用当前值拼键后再自增 → 首条是 `text_1`。
- `.sharedText(...)` 走的是 `PonderLocalization.getShared`，**不经过** `registerText`，因此不占 `text_N` 编号。

---

## 交叉引用

- `PonderScene` / `PonderTag` / `PonderIndex` → [40-foundation-others.md](40-foundation-others.md)
- 剧情写法 → [10-scene.md](10-scene.md)
