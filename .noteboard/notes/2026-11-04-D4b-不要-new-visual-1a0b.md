---
type: note
color: yellow
tags:
  - D4
  - 实例
id: 1a0b
title: D4b 不要 new visual
created: 2026-11-04T09:05:13.965Z
source: null
derivedFrom: []
---
重建 = 泄漏 + 首帧闪烁。
正确：两套实例并存，用 Instance.setVisible(bool) 切；复用用 Instancer.stealInstance(I)（ContraptionVisual.java:111-119）。
写完实例字段必须 setChanged()。