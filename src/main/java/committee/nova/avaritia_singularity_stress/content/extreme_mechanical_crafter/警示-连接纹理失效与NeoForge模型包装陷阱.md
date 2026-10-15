# 警示：连接纹理（CTModel）为什么一直不显示 —— NeoForge 模型包装陷阱

> **一句话教训**
> 在 `ModelEvent.ModifyBakingResult` 里替换方块模型时，
> **烘焙结果表的 key 是「方块注册名 + 状态属性串」，不是模型文件路径。**
> 按模型路径去匹配，会**永远不命中**，且**没有任何报错**——编译通过、游戏能启动、模型正常显示，
> 只有那一个功能（连接纹理）静默失效。
>
> 相关文件：`src/main/java/committee/nova/avaritia_singularity_stress/init/client/ModClientEvents.java`

---

## 1. 现象

- 终焉动力合成器的**连接纹理完全不显示**。
- 方块本身正常，贴图正常，扳手能建立/拆分连接（功能正常）。
- 只有「连接处边框融合」这一视觉反馈缺失。
- **无任何日志报错**，编译与启动都正常。

关键干扰项：重写方块实体之后现象**一模一样**，容易误判为「重写引入了问题」或「扳手连接没触发」。

---

## 2. 根因

`CTModel` **从来没有被安装到模型上**。

```java
// ❌ 错误写法（旧代码）
var bodyModel = ResourceLocation.fromNamespaceAndPath(MODID, "block/extreme_mechanical_crafter/block");
event.getModels().replaceAll((rl, model) -> {
    if (rl instanceof ModelResourceLocation mrl && mrl.id().equals(bodyModel)) {  // 恒为 false
        return new CTModel(model, behaviour);
    }
    return model;
});
```

`bodyModel` 是 **模型文件路径** `avaritia_singularity_stress:block/extreme_mechanical_crafter/block`；
而 `event.getModels()` 的 key 是 **方块注册名 + 状态属性串**：

```text
ModelResourceLocation(avaritia_singularity_stress:extreme_mechanical_crafter, "facing=north,pointing=down")
```

两者**永远不相等** → `replaceAll` 一个都没替换 → `CTModel` 未安装
→ `CTModel.getQuads` 里 `extraData.has(CT_PROPERTY)` 为 false → UV 从不改写。

`block/extreme_mechanical_crafter/block` 只是被 blockstate **引用**的依赖模型，
它存在于 `unbakedCache`，**从不作为 top-level key 出现在烘焙结果表里**。

---

## 3. 证据链（一手源码，勿凭记忆）

### 3.1 key 是怎么构造的

`net/minecraft/client/resources/model/BlockStateModelLoader.java`（约 92-104 行）：

```java
// blockStateId 来自方块注册名
private void loadBlockStateDefinitions(ResourceLocation blockStateId, StateDefinition<Block, BlockState> stateDefinition) {
    ...
    map.put(BlockModelShaper.stateToModelLocation(blockStateId, state), ...);
}
```

其中 `blockStateId = block.builtInRegistryHolder().key().location()`。

`net/minecraft/client/renderer/block/BlockModelShaper.java`（约 57-59 行）：

```java
public static ModelResourceLocation stateToModelLocation(ResourceLocation location, BlockState state) {
    return new ModelResourceLocation(location, statePropertiesToString(state.getValues()));
}
```

### 3.2 反查确认（state 缓存用同一个 key）

`net/minecraft/client/resources/model/ModelManager.java`（约 225-236 行）：
`ModifyBakingResult` 在此触发，随后构建 state 缓存时：

```java
ResourceLocation resourcelocation = p_250633_.getBlock().builtInRegistryHolder().key().location();
BakedModel bakedmodel1 = map.getOrDefault(BlockModelShaper.stateToModelLocation(resourcelocation, p_250633_), bakedmodel);
```

**再次确认 key = 方块注册名 + 状态串。**

### 3.3 `ModelResourceLocation` 的类型

Minecraft 1.21.1 起它是 **record**，且**不继承 `ResourceLocation`**：

```java
public record ModelResourceLocation(ResourceLocation id, String variant) {
    public static final String INVENTORY_VARIANT = "inventory";
    public static final String STANDALONE_VARIANT = "standalone";
    public String getVariant() { return this.variant; }
}
```

- `.id()` → 方块注册名
- `.getVariant()` → 状态属性串，或 `"inventory"`（物品）/ `"standalone"`（额外注册的模型）

### 3.4 Create 官方是怎么做的

`allapis/.../foundation/data/CreateRegistrate.java`（约 287-317 行）：
`connectedTextures(...)` 把行为登记进 `MODEL_SWAPPER`，key 是**方块 id**：

```java
CreateClient.MODEL_SWAPPER.getCustomBlockModels()
    .register(RegisteredObjectsHelper.getKeyOrThrow(entry), model -> new CTModel(model, behavior));
```

`allapis/.../foundation/model/ModelSwapper.java`（约 37-69 行）：在 `ModifyBakingResult` 里，
**枚举该方块的全部状态**并逐个替换：

```java
public void onModelBake(ModelEvent.ModifyBakingResult event) {
    Map<ModelResourceLocation, BakedModel> modelRegistry = event.getModels();
    customBlockModels.forEach((block, modelFunc) ->
        swapModels(modelRegistry, getAllBlockStateModelLocations(block), modelFunc));
    ...
}

public static List<ModelResourceLocation> getAllBlockStateModelLocations(Block block) {
    ResourceLocation blockRl = RegisteredObjectsHelper.getKeyOrThrow(block);
    return block.getStateDefinition().getPossibleStates().stream()
        .map(state -> BlockModelShaper.stateToModelLocation(blockRl, state))
        .toList();
}
```

**它从不按模型文件路径去找。** 这是判断「写法对不对」最权威的参照。

---

## 4. 为什么这个坑难发现

1. **完全静默**。匹配失败 ≠ 报错，只是「什么都没做」。没有异常、没有警告、没有日志。
2. **两个概念长得极像**。`ns:block/foo/bar`（模型路径）与 `ns:block_name`（方块注册名）都是 `ResourceLocation`，
   编译器不会拦，肉眼也容易滑过去。
3. **症状被错误归因**。CT 不显示 → 先去查图集、查 CT 判据、查扳手连接，
   而真正的断点在更上游的「模型是否被包装」，**这条链在最前面就断了，后面查得再对也没用**。
4. **重写会掩盖归因**。若在修 bug 的同时重构了其它部分，现象不变会让人误以为是新代码的问题。

---

## 5. 正确写法

```java
@SubscribeEvent
public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
    ExtremeCTBehaviour behaviour = new ExtremeCTBehaviour();
    ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath(
            MODID, "extreme_mechanical_crafter");   // ★ 方块注册名，不是模型路径

    event.getModels().replaceAll((location, model) -> {
        if (location.id().equals(blockId)
                && !ModelResourceLocation.INVENTORY_VARIANT.equals(location.getVariant())) {
            return new CTModel(model, behaviour);
        }
        return model;
    });
}
```

要点：

- **按方块注册名匹配**。16 个 `facing × pointing` 变体共用同一个 `id()`，一条判断覆盖全部。
- **排除 `inventory` 变体**。BlockItem 与方块同名，其 key 是 `(ns:block_name, "inventory")`；
  不排除会连带包装物品模型（本次无害，但不干净）。
- **不要按模型路径匹配**。它永远不命中。
- **事件必须是 `ModifyBakingResult`**，不能用 `BakingCompleted`：后者的 `getModels()` 是
  **不可修改视图**，且发生在 state 缓存构建**之后**，即便改了也不会生效。
- 更贴近官方的写法：把自己的行为注册进 Create 的 `CustomBlockModels`，交给它的 `MODEL_SWAPPER` 处理。

---

## 6. 通用规则（适用于所有「改烘焙模型」的场景）

> **只要有代码在遍历 `ModifyBakingResult.getModels()`，先问一句：
> 「我拿什么当 key 去比？这个 map 的 key 到底是什么？」**

适用于：连接纹理、额外模型数据、模型替换、自定义渲染包装、动态模型等一切「包装 BakedModel」的需求。

- 要改**方块状态模型** → key 用 `ModelResourceLocation(方块注册名, 状态属性串)`，
  用 `BlockModelShaper.stateToModelLocation(blockRl, state)` 枚举全部状态。
- 要改**物品模型** → key 用 `ModelResourceLocation(物品注册名, "inventory")`。
- 要改**额外注册的模型** → 必须先 `RegisterAdditional`，key 用 `ModelResourceLocation(模型路径, "standalone")`。
- 模型文件路径（`ns:block/xxx`）**只在 `RegisterAdditional` 那条路径里**才作为 key 使用。

---

## 7. 排查清单：CT 不显示时按顺序查

按「数据流从上游到下游」的顺序查，**不要跳步**：

| # | 检查项 | 怎么验证 |
|---|---|---|
| 1 | **CTModel 是否真的装上了**（最高优先级） | 在包装处打日志/断点，计数必须 > 0。为 0 则**到此为止**，后面都不用查 |
| 2 | 匹配用的 key 是否正确 | 对照第 5、6 节；打印几个 `location.id()` 与 `location.getVariant()` 看实际值 |
| 3 | 事件时机是否正确 | 必须是 `ModifyBakingResult`，不能是 `BakingCompleted` |
| 4 | `getShift` 的 original 是否与模型该面纹理**同源** | `CTModel` 要求 `quad.getSprite() == spriteShift.getOriginal()`，身份比较，必须完全一致 |
| 5 | CT 类型与图集尺寸是否匹配 | `VERTICAL`/`HORIZONTAL` → 2×2（如 32×32）；`OMNIDIRECTIONAL` → 8×8（如 128×128） |
| 6 | `getShift` 是否对非目标状态返回 `null` | 否则烘焙阶段拿到 `minecraft:air` 占位状态会因缺属性抛异常 |
| 7 | `connectsTo` 判据是否成立 | 原版语义：**必须已建立连接组**才显示连接纹理，相邻不够 |
| 8 | 连接建立后是否触发重烘焙 | 对照 Create 的 `connectivityChanged → sendData → Redraw → sendBlockUpdated(...,16)` |

**第 1 步是最高杠杆的一步。** 本次三个独立调查全部收敛到它，
而在此之前对图集、判据、扳手链的排查（虽然结论都是"正确"）对解决问题毫无帮助。

---

## 8. 本次已排除的项（避免重复走弯路）

以下都**不是**原因，且都已被一手证据确认无误。以后再遇到 CT 问题**不必优先怀疑**：

| 排查项 | 结论 | 依据 |
|---|---|---|
| 侧壁连接图集 `crafter_side_connected.png` | ✅ 正确 | 32×32，四象限互异（无连接/开左/开右/双向），象限 0 与原图字节相同，**alpha 通道与 Create 图集逐字节一致**（仅重着色） |
| 正面连接图集 `crystal_matrix_casing_connected.png` | ✅ 正确 | 128×128，8×8 图集，48 个互异 tile、17 个透明填充，边框/角/无边框状态齐全 |
| CT 的 original 与模型面是否同源 | ✅ 同源 | 正面 22 面用 `#5 = crystal_matrix_casing`；侧壁/上下 16 面用 `#4 = crafter_side` |
| 纹理文件是否存在 | ✅ 全部存在 | 本地资源、外部集成源码、release jar 内均有 |
| 扳手连接能否建立 | ✅ 正常 | behaviour 挂载、`Tags.Items.TOOLS_WRENCH` 与 Create 一致、非正面返回 `PASS` 三者齐备 |
| 连接后是否触发刷新 | ✅ 正常 | `connectivityChanged → sendData → Redraw → sendBlockUpdated(...,16)` 与 Create 逐行对齐 |
| 是否需要 BE 调 `requestModelDataUpdate` | ✅ 不需要 | CT 数据由 `CTModel` 在区块网格化时现场计算；无 BlockEntity 的 casing 方块也正常显示 CT，即此证 |

补充事实（对 CT 无影响，但值得知道）：

- `CTModel.gatherModelData` 由 `SectionCompiler` 在**区块网格化时无条件调用** `BakedModel#getModelData`，
  与 BlockEntity、与 `ModelDataManager` 的 BE 数据采集**无关**。
- 动态部件（lid/arrow/belt/belt_animated/cogwheel）是 partial model，
  **不进入方块状态模型表**，因此天然不受 `ModifyBakingResult` 处理影响。
- 上一版代码用 `getPath().startsWith("block/extreme_mechanical_crafter")`，
  同样对着模型路径比，**同样恒不命中**。所以这个 bug 自始存在，与方块实体的重写无关。

---

## 9. 一句话总结

> **改烘焙模型时，先搞清楚 map 的 key 是什么。**
> **本次的 key 是「方块注册名 + 状态属性串」，被错当成了「模型文件路径」，于是静默失效。**
> 更重要的教训是：**排查要顺着数据流从上游往下走，并在每一环留一个可观测信号**
> （本次修好后加了一行包装计数日志，正是为了下一次不再盲查）。

---

*记录时间：本次修复之后。对应修复：`ModClientEvents.onModifyBakingResult` 改为按方块注册名匹配，并排除 `inventory` 变体。*
