# Network Control Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. ユーザーの承認済み仕様の継続実装として、このセッション内で実装・検査・既存ブランチへの反映を行う。

**Goal:** レッドストーン端末に通信更新の期限と安全出力を設け、許可された制御元のハートビートで維持する。
**Architecture:** 永続設定はNetworkProfileのv12追加節。ControlLeaseRuntimeは期限1件/受信端末と期限1件/継続送信を管理する純Javaエンジン。NetworkControlが実機器・所有権・既存仮想TCP/NAT/ACLを仲介し、BlockEntityへ必要時だけ隣接更新を通知する。
**Tech Stack:** Java21 / Minecraft1.21.1 / NeoForge21.1.235。追加依存・ケーブルTickerなし。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md の「切断時は現状維持／停止／安全状態」。既存機器・色分け配線・設定保持を維持。

## Global Constraints
- 既存のfeature/venus-hostile-mobsだけへ通常コミット。main/別ブランチ/別workflowは変更しない。
- 136種のケーブル、4/16幅、204 PNG、既存レシピは変更しない。
- 外部通信なし。設定・手動命令・継続通信はサーバーで再認可し、監視によって出力や期限を変更しない。
- 旧v1〜v11はholdへ移行。設定・装着品・故障・所有権は保持するが、出力を維持する許可/時計/継続ジョブは保存しない。

## Rulings
- 既定holdは従来挙動。stop/valueは期限切れ・再ロードで指定安全値。機器故障中は従来どおり0。
- `control-safety hold`、`control-safety stop SECONDS`、`control-safety value LEVEL SECONDS`。期限1〜3600秒。
- `redstone ENDPOINT LEVEL`は従来の単発操作。`redstone maintain ENDPOINT LEVEL`は20tickごとの更新を自動送信。期限3秒以上が必要。`redstone stop`はその送信元の継続更新を停止。
- maintainは開始時の数値IP/公開ポート/受信機器ID/プレイヤーUUIDへ固定する。送信元/宛先の所有者・明示共有権限を毎回再確認（受信側のサービス公開も許可）。OPだけの一時的な権限では自動更新を作らない。プレイヤーのオンライン状態は要件にしない。
- ハートビートは到達性+認可だけで、出力を書き直さない。期限が切れた後は復旧しても自動復帰せず、明示操作が必要。
- 1ディメンション最大128有効期限・64継続更新・送信元4更新。期限処理は最大128件、通信確認はtick最大2件。期限は成功受信だけで伸ばし、遅い処理を成功扱いしない。

## Review Focus
- 期限ちょうど、時計が大きく進んだ場合、繰返し更新で期限キューが増殖しないこと。
- 権限取消、NAT誤転送先、受信機器差替えで命令や期限が変更されないこと。
- 安全動作のON/OFF/値変更と故障復旧・再設置で古い出力が復活しないこと。
- サービス監視と無関係な編集がハートビートの代わりにならないこと。
- 部分適用、不正数値、上限後の失敗で出力やNAT表を確定しないこと。

## Tasks
- [x] 1. ControlSafetySettings/ControlLeaseRuntime/ControlSafetyCommandsの失敗テスト→実装→純Java検証。v11固定fixtureで移行を確認。
- [x] 2. NetworkSimulatorの認可付きcontrolRequestとNetworkControl/Entity/World/Menuを接続。実通信の失敗と期限動作を検証。
- [x] 3. 共通ControlSafetyGuide・help・操作説明。既存全テストとアセットを実行。
- [ ] 4. 実配線、NAT/ACL、停止/保持/指定値、権限、通常ドロップと再配置のGameTest。差分レビュー後に同じブランチへpush、既存CI結果と公開を確認して記録。

## Verification ledger
- 開始点51f8011。コンテナにはネット接続がないため、前回CI5bb99d5の診断artifact内ソースを使用。開始点までのコード変更はなく検証文書のみ。更新ファイルのGit blobをpush前に照合する。
- 今回の新規検査・全体ビルド・実クライアント目視はまだ未実施。

- ローカルJava21: 新規5クラスを含む64テストクラス成功。新規通信API・公開設定・ガイドの失敗記録を保持。
- アセット: 1,594 JSON / 204 PNG、全参照解決。既存画像生成器は無変更。
- Native GameTest3件を追加。CIでの実行は未確認。
