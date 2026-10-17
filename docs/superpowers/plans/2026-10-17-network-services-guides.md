# Network Services and Guides Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. This extends the approved network specification in the current session.

**Goal:** IPv4 ACL、DHCPリレーと明示プール、共通の接続図ガイドを初期版へ追加する。
**Architecture:** Minecraft非依存のcoreで通信/設定を検証し、機器メニューで認可と保存を行う。GUIとJEIは同じ静的な図・説明を利用。
**Tech Stack:** Java 21 / Minecraft 1.21.1 / NeoForge 21.1.x / JEI common API 19.21.0.247（compileOnly）。
**Spec:** `docs/NETWORK_SYSTEM_SPEC.md`。

## Global Constraints

- `feature/venus-hostile-mobs` のみ。既存 `Build 1.21.1 NeoForge` を使い、workflowは追加しない。
- ケーブル4/16幅、全テクスチャ、Create任意、外部ネットワーク非接続を維持する。
- 旧設定の読み込み、機器回収時の設定/装着品保持、マルチプレイ認可と処理上限を維持する。

## Review Focus

- ICMP可否だけでWeb/DNS/転送を代用しない。要求・応答で元のIPv4/ポートを検査。
- リレーのgiaddrにはクライアント側インターフェースのIPを使い、別LANのプールを配布しない。
- ACK不達、枯渇、保存容量超過では予約/クライアント設定を確定しない。
- v1→v2で旧機器へACLを勝手に適用せず、アンロード端末の予約を勝手に再利用しない。
- ガイドを表示している間、背後のスロット/コマンド欄を操作させない。図のアイテムが実在することを検査。

## Tasks

- [x] coreに`NetworkPolicy`、`NetworkServices`、`NetworkServiceCommands`を追加。12失敗ケース確認後にACL/リレーを実装。
- [x] `NetworkSimulator`のサービス別往復処理、ACL判定、プール選択と予約を追加。設定v2はv1を読み込める。
- [x] `NetworkMenu`に新CLI・TCP80/25570の検査・DHCPサーバー予約の保存を接続。
- [x] `NetworkGuide`に10トピック32手順。機器GUIとJEI独自カテゴリに同じ接続図・説明を表示。
- [x] Java 21のサービス159検査・ガイド89検査を実行。`check`とGameTestへ回帰検査を追加。
- [ ] GitHubの現行先端を確認し、他変更を保持してpush。既存Actionsの全体ビルド/GameTestを確認。

Ruling: 実行環境の直接ネットワーク接続とHome Server端末が利用不可のため、GitHub連携で読み書きし、純Javaはローカル、全体NeoForge検証は既存Actionsで実行する。ホストのサービスを再起動しない。
Ruling: DHCPの予約は期限なし・手動要求方式。完全なリース更新やTCPスタックを実装済みと記載しない。
