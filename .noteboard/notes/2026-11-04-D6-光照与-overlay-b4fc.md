---
type: note
color: pink
tags:
  - D6
  - 光照
id: b4fc
title: D6 光照与 overlay
created: 2026-11-04T09:05:14.014Z
source: null
derivedFrom: []
---
computePackedLight() 拿光；relight(...) 6 个重载；updateLight 在光源变化时刷新。
方块实体挪位置/变亮变暗后不刷新 → 画面「半黑」或「突然全亮」。