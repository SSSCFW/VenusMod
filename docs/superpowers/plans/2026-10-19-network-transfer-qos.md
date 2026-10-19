# 転送帯域の公平配分と優先度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。承認済みNETWORK_SYSTEM_SPECの帯域共有・QoS・軽量化を継続する。

**Goal:** 仮想転送の空き帯域を再配分し、低/通常/高の重み付き優先度と自機の転送停止を提供する。
**Architecture:** 純Javaの進行充填方式でリンク・本体容量・各転送の上限を同時に制約する。低/通常/高の重みは1/2/4とし、厳密優先の飢餓を避ける。転送待ち/完了のイベントで時間を区切り、割当は入力が同じなら再利用する。
**Tech Stack:** Java21 / Minecraft1.21.1 / NeoForge21.1.235、外部依存追加なし。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md の帯域共有/QoS/機器容量/マルチ安全性。

## Global Constraints
- 作業とpushはfeature/venus-hostile-mobsのみ。既存Build 1.21.1 NeoForgeだけを使用。
- 136種・4/16幅・画像・モデル・レシピ・ゴーグルを変更しない。
- 保存v16とnetwork-3を維持。優先度は実行中の転送だけに付与し、永続設定を増やさない。
- 実パケット/TCPの再送・DSCP・機器のQoSキューを再現したとは主張しない。

## Review Focus
1. 同じリンクを往復/重複しても二重消費しない。リンクと本体は別の資源。
2. 別の低速区間で頭打ちの転送の未使用分を他へ再配分し、容量を超えない。
3. 遅延待ちと完了済みの転送が帯域を占有しない。時間をまとめて進めても送信量を過大計上しない。
4. 他の送信元の転送IDへの停止/優先度操作を拒否。拒否でNAT/キャッシュ/転送を変更しない。
5. 同一入力で再計算を避け、128転送・経路長上限を維持。失敗リンクは配分前に除去。

## Tasks
- [x] 1. 既存の帯域再配分の不足と新CLI未対応の失敗検査。純Java BandwidthAllocator/TransferPriority。
- [x] 2. TransferSchedulerを転送進捗へ統合。開始/終了/取消/優先度変更/待機の検査と全回帰。
- [x] 3. TransferCommands、共通GUI/JEIガイド、操作文書、実配線・メニューGameTest。
- [ ] 4. 差分レビュー・画像参照検査・push・既存CI結果の確認と記録。

## Ledger
- 開始live head: 08b1537。ソースは成功CI0290edcの診断入力。headの差分は文書2件のみとGitHubで確認。
- 独立ディレクトリ /mnt/data/venusmod-network-next で作業。ローカルsnapshotコミットをリモート親として使用しない。
- Ruling: 今回の優先度は仮想bulk transferの相対重み。制御プロトコルや小さいサービス要求の遅延/帯域課金を変更しない。

- 既存の帯域取り残しを公開APIで再現後、重み付き進行充填とイベント時間分割を実装。開始待ち/完了時の再配分、他送信元の拒否、NAT保持、128転送の上限を検査。
- 新しいネイティブGameTest2件は追加済み。まだ成功とは記載しない。

- push前: 新規5クラス95検査、全96テストクラス成功。ガイド23トピック97手順、アセット1,594 JSON/204 PNG全参照解決。
