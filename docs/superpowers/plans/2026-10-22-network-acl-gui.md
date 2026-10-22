# ACL設定GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。承認済みネットワーク設計の継続依頼として実装し、最後に独立レビューを行う。

**Goal:** ルーター/L3スイッチからACLの追加・一覧・内容一致を条件にした確認付き削除を行う。
**Architecture:** 純Javaの入力正規化と下書き・確認状態を既存ACLモデルへ接続する。既存Command Payloadとサーバー認可を使い、削除ではポート/方向/順序番号だけでなく確認した規則の全内容を照合する。
**Tech Stack:** Java 21 / Minecraft 1.21.1 / NeoForge 21.1.235 / optional JEI 19.21.0.247。
**Spec:** `docs/NETWORK_SYSTEM_SPEC.md` の6・8・9・10・11章、`docs/NETWORK_SERVICES_GUIDE.md`、既存ネットワーク実装の継続依頼。

## Global Constraints

- 作業ブランチは `feature/venus-hostile-mobs` のみ。既存 `Build 1.21.1 NeoForge` だけを使用。
- 機器保存v16、通信network-3、136種類の4/16幅ケーブル、画像/モデル/レシピを維持。
- ルールは機器全体64件、P0〜P5、in/out、順序1〜65535。既存のIP/ICMP/TCP/UDP/OSPF/VRRPと暗黙denyを維持。
- 宛先通信ポートはTCP/UDPだけ0〜65535、またはany。送信元/宛先はIPv4/CIDR/any。
- GUIを開く・入力・ガイド移動・リサイズだけでは要求を送らない。下書きは画面内で保持し、削除確認は取り消す。
- 所有/共有権限、実メニュー、距離、レート制限は既存サーバー側処理を利用。外部通信を追加しない。
- GUI/JEIの23トピック97手順を維持し、既存ACL手順へ説明を追加する。

## Review Focus

- 別の人が同じキーの規則を置き換えた後の削除は拒否し、置換後の規則と稼働フローを保持する。
- 入力中の編集・リサイズ・ガイド往復は確認を取り消し、古い確認を使って削除しない。
- 最後の規則を削除するとそのポート方向が全許可へ戻ること、往復で別評価することを表示する。
- ポート0、/0、/32、65535、非TCP/UDPへの通信ポート指定、制御文字を境界検査する。
- モーダルのキー・ドラッグ・クリックが背後のJEI/Inventoryへ流れず、実入力でサーバー変更を確認できる。

## Task 1: 内容一致削除と画面用下書き

**Files:** 新規 `core/AclDraft.java`、`tests/network/AclCommandTest.java`、`tests/network/AclDraftTest.java`。変更 `core/NetworkServiceCommands.java`、`core/NetworkPolicy.java`、`gradle/network-verification.gradle`。
**Interfaces:** `AclDraft` は型付きport/direction/permit/protocolと文字列sequence/source/destination/destinationPortを保持。`addCommand()`、`requestDeletion()`、`confirmDeletion()`、`dismissDeletion()`、`pendingDescription()`を提供。新規CLI `acl delete-match PORT DIR SEQ permit|deny PROTO SRC DST [DST-PORT]` は全内容一致のみ削除。

- [x] 変更前に新規CLI拒否・下書きAPI不足を確認する。
- [x] 入力正規化、追加命令、単回の削除確認、全内容一致削除を実装。旧 `acl delete PORT DIR SEQ` を維持。
- [x] 誤入力・重複・内容不一致・未存在・空clearでは設定と稼働転送を保持。純Javaの新規2クラスと全ネットワーク試験を実行。

## Task 2: フォームと実ゲーム経路

**Files:** 新規 `client/NetworkAclPanel.java`、`test/NetworkAclGameTests.java`、`jei/NetworkAclInteractionProbe.java`。変更 `client/NetworkScreen.java`、`core/NetworkGuide.java`、既存クライアント試験の呼出元。
**Interfaces:** `NetworkAclPanel` は既存RoutePanelと同じmount/visible/tick/render/入力経路で統合。フォームボタンは通常画面下部のACL設定。純Java下書きからの命令だけを既存Command Payloadへ送信。

- [x] ポート・方向・順序・許可/拒否・プロトコル・送信元/宛先・宛先ポートを編集可能にする。
- [x] 一覧手動更新、確認対象の全内容表示、確認取消、戻る/ガイド、結果スクロールを実装。
- [x] 実配線GameTestで通信の許可/拒否と管理認可、内容変更後の拒否、設定保存を確認。
- [x] 既存Robot入力へACLシナリオと必須PASS/画面の検査を追加する。
- [x] 実クライアントで追加/確認取消/内容一致削除とInventory保持を検査し、完了結果・画面を確認する。

## Task 3: 検証・共有・引継ぎ

**Files:** `docs/NETWORK_ACL_GUI.md`、`docs/NETWORK_ACL_GUI_VERIFICATION.md`、README、既存GUI説明。

- [x] 全体 `gradle build --stacktrace` と `gradle runGameTestServer -PvenusTestCreate=true` を実行し、成功を確認する。
- [x] 既存JEIクライアント試験を実行し、入力helper修正を含む結果を確認する。
- [x] 独立レビューを行い、数字キー入力を既存helperが許可する方法へ修正する。ドラッグ実入力の未検証を軽微な残件として記録する。
- [x] 既存ブランチへpushし、既存CIとJAR公開の結果を確認。成功と未実施範囲を区別して記録。
- [x] 追加証拠としてCI診断artifactの画像を目視し、公開JARの実バイトのハッシュ・内容を確認する。

## Ledger

- 開始HEAD: `1e9dfe4`。最新の静的経路GUIまで完了していることをコード・直近3計画・検証記録から確認。
- 独立の読み取り監査で、未実装のACLフォームが直近GUI整備の次の一貫した範囲であることを確認。
- 既存の承認済み設計書と継続依頼に基づき実装。新規ブランチは作成しない。
- Task 1完了: 変更前のCLI拒否/API不足を確認後、AclCommandTest155検査・AclDraftTest101検査、全105純Javaクラスが成功。共通ガイド23トピック231検査、JEIカタログ413検査、ページ分割198検査も成功。
- Task 2完了: ACLフォームとRobotシナリオを追加。通常青ケーブル・実メニューによる `network_acl_gui:0` が成功し、ネイティブGUIの全体再実行も終了コード0。ACL専用PASSと追加・確認・削除後の3画面を確認済み。
- ローカルJava21.0.12.1/Gradle9.2.1で `gradle build --stacktrace` 成功（3分30秒、194タスク）。Create6.0.10入り `gradle runGameTestServer -PvenusTestCreate=true` 成功（40秒）、必須165件すべて成功。追加ACL試験は1件で、全体件数とは区別する。
- 根拠は `build/reports/network/build.log`、`gametest.log`、`acl-all-tests.log`。SlashBlade本体未導入による既存検査のスキップを検証記録へ明記した。
- 独立レビュー完了。数字キーを `input.type("1")` へ修正し、workerの機能追加は行わない。この経路を含む実入力とInventory保持の検査は成功。
- ネイティブ全体は895検査、入力helper契約は別に11検査が成功。JEIは接続111カード・仕様43カード、言語ja_jp、続きページrouting/2。`client.log` は3分29秒でBUILD SUCCESSFUL。`19-acl-added.png`、`20-acl-confirm.png`、`21-acl-deleted.png` を直接開き、可読性と配置を確認。
- 検証専用watchdogの180秒停止を記録し、300秒へ変更。次のXvfb認証失敗には `runClient` のみ `--no-configuration-cache` を適用。追加の独立レビューで指摘なし。修正後に全体を最初から再実行して成功し、途中停止は成功扱いにしない。詳細はNETWORK_ACL_GUI_VERIFICATION.md。
- 軽微な残件: ドラッグのモーダル入力経路はコードレビュー済みだが、Robotによるドラッグ実入力は未検証。成功扱いにしない。
- 実装[`c600e27d9d644821fdc846e6e04cf27b7d64b602`](https://github.com/SSSCFW/VenusMod/commit/c600e27d9d644821fdc846e6e04cf27b7d64b602)を既存ブランチへpush済み。[既存CI run39762406445](https://github.com/SSSCFW/VenusMod/actions/runs/39762406445) / job119769540371が成功。Build1分14秒、AclCommand155・AclDraft101検査、`network_acl_gui:0` と必須165GameTest成功（51秒）、実JEI/GUI895検査・接続111カード/仕様43カードをログで確認。
- CIのBuild/GameTest/JEI/診断artifact/JAR検査/公開は成功。任意Dedicated server smokeだけskippedで、実行成功とは扱わない。
- [ci-latest](https://github.com/SSSCFW/VenusMod/releases/tag/ci-latest)のtargetと本文はc600e27を指す。JAR asset665494271を取得し、実ファイル2,455,690 bytes、SHA-256 `0c8ba57adbe084266d7216a322aea159b6e2c8808ce713a7713ea516b3964fd5` が公開メタデータと一致。AclDraft/NetworkAclPanel/NetworkAclGameTests/NetworkAclInteractionProbeのclassとNetworkServiceCommands内のdelete-matchを確認済み。
- 診断artifact12419162045（期限2026-11-05）を取得。11,164,081 bytes、SHA-256 `61f44194966121f553ef887737bbcef260ca5b650871384988e6d247a6952ac7` がメタデータと一致。全21画面・全6PASS・FAILなし、895検査/111カード/43カード/ja_jp、helper11検査、CIクライアント3分44秒成功を確認。
- CIの19/20/21画像も直接開き、全内容・注意文・削除後ACL未適用の可読性と欠け/重なりなしを確認。ローカル画像とは別に取得物を検査した。証拠は `build/reports/acl-ci/` と `build/reports/acl-release/`。
