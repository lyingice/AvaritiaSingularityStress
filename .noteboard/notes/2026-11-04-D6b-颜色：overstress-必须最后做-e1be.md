---
type: note
color: pink
tags:
  - D6
  - 颜色
id: e1be
title: D6b 颜色：overstress 必须最后做
created: 2026-11-04T09:05:14.032Z
source: null
derivedFrom: []
---
KineticBlockEntityVisual.applyOverstressEffect(be, instances)（:43-59）会覆盖颜色并 setChanged。
顺序反了 = 过载不变红/不变绿。
OrientedRotatingVisual 没实现 SimpleTickableVisual → 天生没这效果，得自己挂 tick。