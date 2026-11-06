---
type: note
color: green
tags:
  - D2
  - chunk模型
  - cutout
id: 771b
title: D2c chunk 模型：cutout 在这一层
created: 2026-11-04T09:05:13.908Z
source: null
derivedFrom: []
---
blockstate → models/block/**。
#D2/chunk模型 的老坑：skipVanillaRender 只跳 BER，不跳它 → 和 visual 叠成双层（T11）。
透明/留空必须 render_type: cutout，否则透明处变黑块——机器查：node dsh-create-mode/check-cutout.mjs