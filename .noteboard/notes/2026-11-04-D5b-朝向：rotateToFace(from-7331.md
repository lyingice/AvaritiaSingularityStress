---
type: note
color: orange
tags:
  - D5
  - 朝向
  - T18
id: "7331"
title: D5b 朝向：rotateToFace(from,to)
created: 2026-11-04T09:05:13.996Z
source: null
derivedFrom: []
---
SHAFT_HALF 模型默认朝 +Z，所以 from 必须 SOUTH。
OrientedRotatingVisual.of() 写死 to = FACING（:41-49）→ 若 hasShaftTowards 认 FACING.getOpposite()，轴就画到背面（T18）。
要改朝向：直接 new OrientedRotatingVisual(ctx,be,pt,SOUTH,to,model)。