# STP BPDU Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. Continue the already approved network specification on `feature/venus-hostile-mobs` only.

**Goal:** L2スイッチをBPDUでルート選出し、Listening/Learning/Forwardingと経路故障を観察できるようにする。
**Architecture:** 独立したJava状態機械、隣接ポート配送アダプター、既存VLANデータ経路の状態ゲート。設定だけ保存し、実行時の選出状態は再構築する。
**Tech Stack:** Java21 / Minecraft1.21.1 / NeoForge21.1.235。Create/JEIは任意。
**Spec:** `docs/NETWORK_SYSTEM_SPEC.md`。

## Global Constraints
- 既存ブランチと既存Build 1.21.1 NeoForgeだけを利用。main/別ブランチ/別workflowを作らない。
- 外部ネットワーク・OS呼出しなし。イベント・配送・インスタンス数を制限する。
- 細い4/16幅ケーブルと独自アセットを保持する。
- 保存設定を捨てない。実施済み試験とクライアント未検証を区別する。

## Rulings
- 旧式の即時ループ防止は`simple`として維持し、BPDU方式は`stp mode classic`で明示選択する。既存拠点に突然30秒の起動待ちを強制しないため。新機能を隠さずGUI/JEIと状態表示で両者を区別する。
- 1VLAN1インスタンスのゲーム用Classic STPを実装し、RSTP/PVST/MSTの実機ワイヤ互換を主張しない。
- classicとsimple/off中継機器の混在リンクはclassic側で止め、未対応の相互運用でループ保護を偽装しない。PC/ルーターへの端末リンクは接続可。
- Forward Delayは標準15秒、Hello2秒、Max Age20秒。手動edgeは端末ポート用。BPDUでedgeを解除し、guard有効ならErr-disabledをラッチする。

## Review Focus
1. BPDU受信はデータの遮断状態と独立。IP ACL/NATでBPDUを通さない/止めない。
2. 受信情報の寿命・root cost・port ID比較により、古い根がループして永続化しない。
3. 曖昧な混在モード、リンク断、キュー上限では成功扱いせず安全停止。
4. 既存NAT/OSPF/VRRPはポート状態変化後に経路キャッシュを再評価。
5. 誤入力は設定を部分変更せず、閲覧はタイマーを進めない。

## Tasks
- [x] 変更前でCLI/起動待ちの失敗を確認。新旧の既存試験を実行する。
- [x] StpSettings / StpEngine: BID・BPDU・ルート/ポート役割・期限・edge/guardを単体試験と実装。
- [x] StpSimulation / CLI / 保存v6: 物理リンク/VLANへ統合。コスト/優先度/状態表示を追加。
- [x] 共通GUI/JEIガイドと実ブロック・機器アイテムGameTestを追加。
- [x] 全体ビルド・純Java回帰・既存アセット・GameTestを実行し、pushとJAR公開を確認。

## 実行記録

初回CLI契約は未対応で失敗、状態機械試験も未実装クラスで失敗を確認。実装後は新規136検査と既存全ネットワーク試験、174 JSON/74 PNG参照検査が成功。cost変更によるedge再昇格、最大コスト境界、不正CLIを再現して修正した。全体ビルドとGameTestは既存CIで確認する。独立サブエージェントなしの自己レビュー。


7f69771をpush後、既存CI run39053252213のBuild/check、必須115 GameTests、JAR公開が成功。診断artifactの変更24ファイルは検査済みローカル入力と全てバイト一致。結果をdocs/NETWORK_STP_VERIFICATION.mdへ追記。実クライアント目視/JEI実操作/実マルチプレイ/TPSは未実施。
