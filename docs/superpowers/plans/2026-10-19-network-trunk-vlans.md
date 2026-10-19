# トランク許可VLAN Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 承認済みNETWORK_SYSTEM_SPECのVLAN/ポート設定を継続実装する。

**Goal:** トランクで通過できるVLANをポートごとに制限し、GUI・CLI・持ち運びで同じ規則を使う。
**Architecture:** 不変のVlanAllowanceがall/none/範囲を検証・ビット集合に変換。既存receivedVlanへ送受両側の検査を集約し、経路探索とsimple/classic STPを同じ判定へ接続する。
**Tech Stack:** Java21 / Minecraft1.21.1 / NeoForge21.1.x。追加外部依存なし。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md のVLAN・ポート設定・保存・軽量化。設計詳細はdocs/NETWORK_TRUNK_VLANS.md。

## Global Constraints
- feature/venus-hostile-mobsのみ。既存Build 1.21.1 NeoForge以外のworkflowを作らない。
- 実ブラウザー・外部ネットワークなし。既存136種・4/16幅・画像・レシピを変更しない。
- 旧v1〜v13をallとして移行。許可リストを256文字以内とし、空欄は拒否、明示noneを全拒否とする。
- Accessでは保存済みリストを使わず、Trunkに切替時だけ有効。Native VLANも送受各側の許可リストに従う。

## Tasks
- [x] 1. 既存CLIの拒否を確認。VlanAllowance・Port・Profile v14・CLIを実装し、構文/保存/送受/Native/Accessを検査。
- [x] 2. GUI Values/Viewと下書きへ許可欄を追加。ページ比較・入力拒否・適用中保持・他ページ維持を検査。通信版network-3。
- [x] 3. 共通ガイドの既存VLAN手順に操作例・Native・Access制限を追加。実配線GameTestと実メニュー/ドロップ検査。
- [x] 4. 純Java全回帰・画像参照・差分レビュー。pushして既存CIのBuild/GameTest/JAR結果を確認。

## Review focus
送信側だけ/受信側だけでの禁止、Nativeのタグなし分類、Access切替での誤遮断、GUIの基本ページからの偽造、再適用と拒否で無関係な転送を消さないこと。各観点を対応テストへ追加する。

## Ledger
開始点0d49fb1、入力snapshotは成功CI f0513ef。開始点との差はNTP検証文書のみ。別作業の変更を上書きせず変更ファイルのみアップロードする。
Ruling: 許可リストをIPv4 ACLの代用品にせずL2ポートの送受規則として扱う。STPは本MODの既存VLAN別モデルを維持し、Cisco専用の管理フレーム例外は追加しない。

- ローカルJava21: 新規6クラス97検査、全81クラス成功。アセット1594JSON/204PNG参照解決。Native2件は追加済み・実行未確認。


## 再開後の検証記録（2026-10-19）

- 再開時headは0a02fab。トランク許可VLANのコードが反映済みで、タスク4の検証記録が未完了と確認。新規機能を重ねず、既存実装の検証を完了した。
- CI run39196857764のジョブ完了結果と診断ログを取得し、Build/check、必須144 GameTests、JAR検査・公開の成功を確認。
- 当該CI入力を新しい検証ディレクトリで再コンパイルし、純Java81クラス・トランク97検査を再実行してすべて成功。
- アセット生成・参照検査も再実行し、1,594 JSON・204 PNGが成功。画像・モデル生成器、レシピ、136種の4/16幅ケーブルは変更なし。
- 送受両端の通過判定、Nativeの分類、Access切替、旧v13読込、フォームの競合/不正入力を再確認。新しい本番コード変更は行わず、検証文書2件のみ更新する。
- 実クライアント、JEI画面、シェーダー、実プレイヤー複数人での操作、大規模TPS計測は未実施。独立レビュー用サブエージェントは使用していない。
