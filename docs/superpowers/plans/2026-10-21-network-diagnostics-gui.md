# 通信診断フォーム Implementation Plan

> superpowers:executing-plans。承認済みNETWORK_SYSTEM_SPEC §6/9のGUI診断・ガイドを継続する。

**Goal:** 宛先・転送量をフォームに入力し、明示的なボタンからping/trace/nslookup/transfer/flowsを実行する。
**Architecture:** 純Javaの許可リスト型コマンド生成器と独立モーダルパネルを追加し、既存Command PayloadとNetworkMenuのサーバー認可を再利用する。通信結果は既存コンソールと共通のサーバー表示として扱い、未送信入力と別に保持する。
**Tech Stack:** Java21 / Minecraft1.21.1 / NeoForge21.1.235 / optional JEI19.21.0.247。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md §6、§9。直前のガイド・JEI継続依頼。

## Global Constraints
- feature/venus-hostile-mobsと既存Build 1.21.1 NeoForgeのみ。新規ブランチ・workflowは作らない。
- 136種4/16幅ケーブル、画像・モデル・レシピ、保存v16、network-3、通信規則を変更しない。
- 外部通信・URL/シェル実行なし。クライアント検証をサーバー認可の代用にしない。
- GUIを開く/入力する/リサイズするだけでは送信しない。転送は仮想バイト数のみ。

## Review Focus
- 宛先へ改行/空白/URL/CLIを混入できない。ポートは転送だけ、DNSは名前のみ。
- 1〜1,000,000MB、整数、overflow/小数/負数の拒否。無関係な入力欄が他操作を妨げない。
- 診断モーダルから背後のスロット/JEI/CLIへ入力を漏らさない。リサイズと戻るで下書きを保持。
- 同じ既存サーバー処理へ送り、実配線で成功/断線/誤入力/転送を検査する。
- 結果欄は最後のサーバー表示であり、ローカル検証失敗時の古い結果を新しい成功と表示しない。

## Tasks
- [x] 1. NetworkDiagnosticsTestで未実装APIの失敗を確認、コマンド生成器を追加して全回帰。
- [x] 2. NetworkDiagnosticsPanelとNetworkScreenのモーダル統合。既存フォームガイド・JEIの操作説明を更新。
- [x] 3. 既存の実JEI入力試験へ診断操作・リサイズ・不正入力・入力遮断・送信結果を追加。
- [x] 4. 差分レビュー、全体ビルド/GameTest/JEI画面、pushと検証記録。

## Ledger
- live head 260bcd3。入力は成功CI81cd68cの診断snapshot。変更したコードだけを最新のtreeへ重ね、snapshot由来の文書は上書きしない。
- 独立したローカルsnapshotで作業する。ローカルcommitをリモート親にしない。
- Ruling: 診断画面の応答は既存コンソールと共通。request-idのない既存Payloadを変えず、送信要求と最後のサーバー表示を明確に分離する。
- Ruling: 既存settings_guiの4手順に操作例を追加し、既存23トピック・97手順を削除しない。カード数は実フォントで再検証する。

- 純Java100クラス、うち新規91検査に成功。アセット1,594 JSON/204 PNG全参照解決。実配線GameTest1件・実クライアント診断試験を追加済み、CI結果は未確認。

## 再開後の完了記録（2026-10-21）

- 再開時headは8c99209。診断GUIと試験修正が既にpush済みのため、残っていたタスク3/4の結果確認と記録を完了した。
- run39539403231のBuild/check、必須162 GameTests、実JEIの841検査、ネイティブ入力契約11検査、JAR検査・公開の成功を確認。
- 診断ZIPのハッシュを確認し、同じ入力180ソースから純Java100クラスを再実行して全成功。診断91検査・ガイド231検査を含む。
- 生成器を再実行し、1,594 JSON・204 PNGの全参照解決を確認。
- 12画面の生成を確認し、今回の未送信・high転送・断線後の3画面を開いて日本語と配置を確認した。
- ピースフル化は専用fixtureだけ。配線試験の解析完了待ちも接続判定・タイムアウトを維持。既存ワールドや通常のメニュー条件は変更しない。
- Ruling: 新規機能を重ねず、この段階の検証を完了する。今回の変更は文書2件のみとし、本番コード・画像・レシピを変更しない。
- 人による操作・IME・全GUI倍率・シェーダー・実マルチ・大規模TPSは未実施。独立レビュアーは使わず、差分と検査結果を自己レビューした。詳細はdocs/NETWORK_DIAGNOSTICS_GUI_VERIFICATION.md。
