# 定期死活監視 Implementation Plan

> Execution: superpowers:executing-plans。承認済み仕様の監視サーバーを既存サービスへ段階追加する。

**Goal:** ロード済みネットワークサーバーから仮想ICMPの往復到達性を定期検査し、連続失敗/成功の閾値で障害・復旧をSyslogへ通知する。
**Architecture:** MonitorSettingsは永続設定、MonitorRuntimeは上限付き期限キューと状態、MonitorCommandsは既存認可済みCLI。既存requestの経路/NAT/ACLを再利用し、監視プローブの平常ログを抑制、状態変化だけ通常logへ記録する。
**Tech Stack:** Java21, Minecraft1.21.1, NeoForge21.1.235。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md / docs/NETWORK_SYSLOG.md。

## Constraints / rulings
- 既存feature/venus-hostile-mobsのみ。main、新ブランチ、新workflowは変更しない。
- 最新594db3fのCIソーススナップショットを独立した作業フォルダーへ展開。既存の染色ケーブル136種・テクスチャ・モデル・レシピを保持。
- 監視はSERVERのみ、初期OFF。数値IPv4のみ、16対象/台、全体256対象・64台、毎tick最大2プローブ。
- 間隔1〜3600秒、失敗/復旧閾値1〜10、既定5秒/3失敗/2成功。
- 状態はUNKNOWN/UP/DOWN。監視元停止中はPAUSED表示で履歴を凍結し、再開時に連続回数をリセット。
- DOWNは観測地点から応答が得られない意味であり、機器本体故障の断定ではない。
- 新規IPv4/TCP/外部ソケットは実装しない。監視設定はv10保存、旧v1〜v9対応。状態と統計は揮発性。
- 設定変更と診断表示は無関係な転送・NATを失効させない。全体監視上限は新規設定を拒否し、既存の監視を停止しない。

## Review focus
1. 不正CLI/旧保存データ: 元設定とスロットが維持されること。
2. 監視元故障/未ロード: 偽の障害・復旧通知を出さないこと。
3. ACLや戻り経路なし: 物理的に接続していてもDOWNとなること。
4. 大量対象: 期限・公平性・処理数の上限、時間飛びで全件追いつき処理しないこと。
5. GUI/public: 管理権限なしで監視設定/一覧を取得しないこと。

## Tasks
- [x] 既存APIで監視未対応の失敗を確認し、基準v9 fixtureを生成。
- [x] 設定/期限/閾値/CLI/静かなプローブを実装、保存と通信統合を検査。
- [x] GUIの監視ボタンと共通ガイド、実配線/実メニュー/機器回収のGameTestを追加。
- [ ] 既存純Java/画像参照検査、差分自己レビュー、変更blob照合後にpush。
- [ ] 既存CIのBuild/check/GameTest/JARを確認し、実行済みと未実施を区別して記録。
