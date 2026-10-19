# Network Configuration Backup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。承認済み仕様の続行として、このセッションでテスト先行で実行する。

**Goal:** 承認済みネットワーク仕様の設定バックアップを、ローカル保存・認可された仮想サーバー保管・確認付き復元として実装する。
**Architecture:** 純Javaの不変スナップショット/容量制限付き保管庫/確認状態を、実BlockEntityと既存メニューに接続する。遠隔保管・取得はTCP5001の既存VLAN/経路/ACL/NAT往復検査と認可の後に確定する。外部ソケットを使用しない。
**Tech Stack:** Java21 / Minecraft1.21.1 / NeoForge21.1.235 / JEI任意。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md のサーバーバックアップ・GUIガイド・持ち運び・マルチプレイ安全性。

## Constraints and decisions

- 既存feature/venus-hostile-mobsのみを更新。main、新規ブランチ、ワークフローを追加/変更しない。
- 136種類のカラーケーブル、4/16幅、画像/モデル/レシピ/タブは変更しない。
- 配線不良からも復旧できるようローカル保管を用意。遠隔取得はまずローカルへ保存し、書換えは別の確認操作とする。
- 機器設定Profile v12は変更しない。保管庫は別のバージョン付きBlockEntityデータで、通常ドロップにも保持する。保存中の保管庫を再帰的にバックアップしない。
- 故障・装着品・所有者/共有UUID・サービス公開・出力・制御期限を復元で巻き戻さない。DHCPの現在予約は維持し、予約と復元プールが衝突する場合は拒否する。
- 保管庫1台8件/設定文字列合計64KiB。満杯で古いものを消さず拒否。読込は容量/形式/チェックサムを検査。
- 復元は同じ機種のみ。機器IDは変えない。IP/Router IDは設定として復元されるため複製先との競合に注意を表示。
- 復元確認は同じ開いているメニュー、30秒以内、一回限り。設定/装着能力/保管内容が変更された場合は再確認。公開サービス権限だけでバックアップを読めない。
- 小さい設定データは同期的な仮想メタデータ通信で、転送フローによる時間/帯域予約は今回の範囲外。

## Tasks

- [x] 1. ConfigBackup / BackupArchive / BackupRestore の失敗テスト、実装、形式/破損/上限/保護対象/競合/期限を検査。
- [x] 2. 認可付きbackupRequestをNAT取引内に接続。拒否時の変換表・戻り経路・誤ポート・断線の試験。
- [ ] 3. NetworkBackupSession / Entity / Menuを接続。実配線・FakePlayer・実メニュー・通常ドロップGameTests。
- [ ] 4. GUI/JEI共通ガイドと日本語操作文書。既存の純Java検査/アセット検査、全体ビルドとGameTests、同一ブランチpush、CI結果を記録。

## Review focus

同時編集/モジュール交換による古い復元、DHCP予約と旧プール衝突、public onからの秘密設定閲覧、NAT拒否後の部分確定、読込破損による保管庫消失を重点検査。

## Ledger

開始head a2baa81。コンテナ外部DNSが解決できないため成功CI 8a125f4の診断アセット内ソースを使用。headまでの差分は検証文書2件のみとlive GitHubで確認。変更ファイルはアップロード前にGit blobを照合する。

- Java21: 新規4クラス70検査と、既存を含む68テストクラスが成功。失敗先行ログと全実行ログを保持。
- 生成アセット: 1,594 JSON / 204 PNG、全参照解決。画像・モデル生成器とレシピは無変更。
- 実BlockEntity/メニュー/通常ドロップのGameTest3件を追加。全体ビルドと実GameTestの結果はまだ未確認。
