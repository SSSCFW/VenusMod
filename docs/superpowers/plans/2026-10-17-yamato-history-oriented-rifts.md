# 閻魔刀の履歴・立体方向 Implementation Plan

> **For agentic workers:** Use focused parallel implementation and independent review. Steps use checkbox syntax for tracking.

**Goal:** 履歴、2地点接続、追加通過対象、サイズと視線方向への対応を既存の次元移動に追加する。

**Architecture:** 保存・GUI・既存エフェクトを独立して実装し、サーバーと描画の向きと大きさは共通幾何クラスへ集約する。

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.235, optional SlashBlade/Create 6.0.10.

**Spec:** `docs/superpowers/specs/2026-10-17-yamato-history-oriented-rifts-design.md`

## Global Constraints

既存feature/venus-hostile-mobsとbuild-1.21.1.ymlのみ。日本語の説明・コメント。任意依存と旧保存を維持する。

## Review Focus

履歴の1,001件目と再読込、同名・同一UUID・他人の地点、倍率のNaN/無限大/範囲外、pitch±90度と斜めの空白部分、失敗した開通とプレイヤーの途中離脱。

## Tasks

- [x] 保存担当: YamatoTravelData/Rulesと対応テストへhistory、recordOpening、sizeとaccess 0〜6を追加。既定1000件の古い順削除と旧NBTの既定値を検査する。
- [x] GUI担当: YamatoTravelScreen/Layoutと翻訳へ履歴タブ、右クリック2地点、倍率スライダー、7択、背景修正を追加。travel_pair(id,second)とsizeを送信する。
- [x] エフェクト担当: YamatoCutAnimation/YamatoCompatから独自斬撃帯を除去し、SlashBlade既存斬撃を無害な演出として利用する。任意依存と2段の方向を維持する。
- [x] 統合担当: Geometry/Rifts/Network/RiftsClient/Rendererへpitch/sizeの伝播と正確な立体接触、視線配置、2地点の所有権検証、成功時の履歴記録を接続。純粋幾何テストとNative GameTestsで検査する。
- [ ] 独立レビューの指摘を解消し、既存Actionsでbuild/checkとCreate Native GameTests成功、配布JAR更新を確認する。
