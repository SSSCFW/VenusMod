# サービス監視 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement and verify each task in this session.

**Goal:** 既存のICMP死活監視に、実装済みサービス別のTCP/UDP到達確認を追加する。

**Architecture:** MonitorSettingsの従来IP一覧を維持し、監視名ごとの種類・公開ポートをv11の追加節として保存。MonitorRuntimeは既存の期限・閾値・上限を共用し、NetworkSimulatorの往復/NAT取引の中で転送先ポートとサービス稼働を検査する。操作やデータ取得を起こさず、正常なTCP/UDP確認をICMPで代用しない。

**Tech Stack:** Java21、Minecraft1.21.1、NeoForge21.1.235。追加依存なし。

**Spec:** docs/NETWORK_SYSTEM_SPEC.md の監視サービス、docs/NETWORK_MONITOR.md。

## Global Constraints

- 作業ブランチは feature/venus-hostile-mobs のみ。main、新規ブランチ、新規workflowは変更しない。
- 136種の細いケーブル・16色分離・モデル・レシピ・テクスチャ・ゴーグルを維持。
- 実OS/外部ソケット/ブラウザーへ接続しない。所有権、マルチプレイ要求予算を回避しない。
- 保存は設定のみ。新旧互換と、故障・装着品・所有者の採掘時保持を守る。
- 既存1台16対象、全体64台/256対象、1tick最大2対象の上限を共用。

## Design / Ruling

承認済み全体仕様の継続。対象は数値IPv4のICMP、Web(TCP80)、DNS(UDP53)、仮想転送(TCP5000)、制御端末(TCP25570)に限定。任意TCPポートの開閉、実TCP SYN、HTTP本文/ステータスコード、DNSレコードの正しさ、名前宛先監視は含めない。NATで公開ポートだけを変更でき、内部ポートは実サービスと一致が必要。

## Review Focus

- ICMP禁止・TCP許可の構成でWebを誤ってDOWNにしない。
- サービス停止/異機種/誤ポート/返信ACL拒否でNATエントリーを新規作成・延命しない。
- 処理上限は従来どおり判定保留。大量の対象でtick上限と公平性を維持。
- 旧v10からICMP設定・他プロトコルを維持。孤立/重複/不正種類の保存データは拒否。
- サービス確認が制御出力、ファイル転送、機器設定・秘密情報の取得を起こさない。

### Task 1: 種類別の設定・実行

Files: core/MonitorSettings.java, MonitorTarget.java (new), MonitorCommands.java, MonitorRuntime.java, NetworkSimulator.java, NetworkProfile.java.
- [x] 公開CLIで monitor add site web IP が失敗するテストを先に実行。
- [x] 従来 monitor add NAME IP を維持し、monitor add NAME web|dns|transfer|control IP[:PORT] を追加。
- [x] v11追加節、コピー、検証、種類表示、失敗前のNATロールバック、同一設定の状態保持を実装。
- [x] 種類/サービス停止/復旧/閾値/既存転送保持の試験を成功させる。

### Task 2: 境界・互換性・実配線

Files: tests/network/MonitorService*Test.java, src/main/java/dev/ssscfw/venusmod/test/NetworkServiceMonitorGameTests.java, gradle/network-verification.gradle.
- [x] ACL、NAPT、ポート転送、誤内部ポート、応答経路なしの回帰試験。
- [x] 元クラスから生成した固定v10 fixture、新v11保存、破損拒否、上限保留を検査。
- [x] 実配線/通常tick/実メニュー/Block.getDropsで稼働停止と設定・装着品・故障・所有権保存をGameTest化。

### Task 3: ガイド・検証・push

Files: core/MonitorGuide.java, docs/NETWORK_SERVICE_MONITOR.md, docs/NETWORK_SERVICE_MONITOR_VERIFICATION.md, README.md.
- [x] 既存ガイドを維持して接続例を追加。未対応範囲を明記。
- [ ] 純Java全回帰、既存アセット検査、差分レビュー。
- [ ] 最新headとの競合を確認、既存ブランチへpush。
- [ ] 既存Actionsでbuild/checkとGameTest、公開結果を確認して記録。

## Ledger

- 基準head: ac1aff1b9b9f49e8b48a6d59941d3fa09f2ba2a4。ローカル入力は検証済み47e2cc6のCIスナップショット。基準headとの差は前回の検証文書のみ。コンテナに既存ワークスペースはなく、独立ディレクトリで検査する。
