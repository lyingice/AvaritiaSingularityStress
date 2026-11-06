---
type: note
color: gray
tags:
  - D7
  - cutout
  - T13
id: d2df
title: D7b 渲染层（RenderType）
created: 2026-11-04T09:05:14.062Z
source: null
derivedFrom: []
---
取值：solid / cutout / cutout_mipped / translucent / entity 系

规则：纹理有 alpha<255 或几何留空 → 方块模型顶层写 render_type: minecraft:cutout。
漏了不报错、编译也绿，只在游戏里变黑块。物品模型不需要（不吃这个字段）。