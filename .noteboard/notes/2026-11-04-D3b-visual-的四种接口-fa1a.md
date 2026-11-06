---
type: note
color: purple
tags:
  - D3
  - 接口
id: fa1a
title: D3b visual 的四种接口
created: 2026-11-04T09:05:13.939Z
source: null
derivedFrom: []
---
SimpleDynamicVisual（最常用，beginFrame+update）
SimpleTickableVisual（要 tick）
ShaderLightVisual（吃 shader 光）
VisualizationLevel（决定哪些层要重绘）

统计（Create 源码）：SimpleDynamicVisual 24 处、SimpleTickableVisual 4 处。