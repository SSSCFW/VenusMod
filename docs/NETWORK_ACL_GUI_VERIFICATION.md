# ACL設定GUIの検証記録

開始HEAD: `1e9dfe4`（静的経路GUIの検証記録まで完了）。実装範囲は [ACL設定GUI](NETWORK_ACL_GUI.md)、計画は [実装計画](superpowers/plans/2026-10-22-network-acl-gui.md) を参照してください。

実装・CI検証対象コミットは **[`c600e27d9d644821fdc846e6e04cf27b7d64b602`](https://github.com/SSSCFW/VenusMod/commit/c600e27d9d644821fdc846e6e04cf27b7d64b602)** です。既存の `feature/venus-hostile-mobs` へpush済みです。

## 失敗先行と純Java

変更前に `AclCommandTest` が内容一致削除の未対応で失敗し、`AclDraftTest` が下書きAPI未実装でコンパイルできないことを確認しました。実装後は次の結果です。

| 検査 | 結果 |
| --- | --- |
| 変更前の既存ネットワークテスト | 103クラスすべて成功 |
| AclCommandTest | 155検査成功 |
| AclDraftTest | 101検査成功 |
| 追加後のネットワークテスト | 105クラスすべて成功 |
| 共通ガイド | 23トピック、231検査成功 |
| JEIカタログ | 413検査成功 |
| ガイドページ分割 | 198検査成功 |

Java 21.0.12.1で実行。入力境界、CIDR正規化、重複追加拒否、全内容不一致、未存在、旧番号削除、64件上限、ACLによる実際の仮想通信の許可/拒否と返信方向を検査します。拒否時には設定だけでなく稼働中フローとNAT変換表の保持も検査しています。256は新規2クラスのアサーション数であり、256個の独立した機能ではありません。

## 実配線GameTest（実行成功）

`NetworkAclGameTests` は通常の青Cat5eケーブルでPC―ルーター―サーバーを接続し、通常tickによる配線解析完了を待って通信を検査します。論理リンクを注入しません。所有者・共有者・権限外のFakePlayerと実NetworkMenuを使用し、確認だけでは未変更、閉じたメニュー拒否、別編集者の内容置換後の拒否、稼働転送の保持、対象だけの削除、設定codec・装着品・所有権を検査します。

今回追加したGameTestは1件です。`network_acl_gui:0` の実行と、既存機能を含む**必須165件すべての成功**を `build/reports/network/gametest.log` で確認しました。165件すべてがACLの検査ではありません。

内容不一致の要求ではエラー本文も確認し、レート制限で拒否されただけの状態を成功に数えません。置換後に開始した同じIDの転送が、古い確認の拒否後も通常tickで進むことを検査しています。最後は確認した規則だけを削除し、後続の規則の全内容が残ることを確認します。

## ローカル実クライアント試験（実行成功・3画面確認済み）

既存JEIスモークの続きとして、隔離ワールドのルーターを通常のメニューで開き、別JVMのRobotからクリック・英数キーを送るシナリオを追加しました。誤入力拒否、追加/一覧/確認/編集/取消/リサイズ/ガイド往復/内容不一致拒否/最終削除、背後のJEI・Inventoryの保護を対象にしています。

背後のスロットにはエメラルド3個を置き、既存のダイヤモンド7個とともに保持を確認する構成です。スロット位置のクリック後に数字1を入力し、背後への持ち上げやホットバー交換を検出します。数字は既存入力helperが許可する `input.type("1")` を利用し、workerへ新しい入力機能は追加していません。

修正後に `bash tools/verification/jei-client-smoke.sh` を全体で再実行し、終了コード0を確認しました。既存ガイド・診断・転送操作・静的経路に加え、ACLのネイティブ操作とサーバー上の状態検査が完了しています。

| 検査 | 確認した結果 | 根拠 |
| --- | --- | --- |
| 実JEI・GUI・Robot入力 | **895検査成功** | `run/jeiSmokeClient/jei-smoke/PASS.txt` |
| ネイティブ入力helperの契約 | **11検査成功** | `build/reports/jei/native-input-contract.log` |
| JEI登録 | 接続111カード・仕様43カード、`ja_jp`、続きページ `routing/2` | 上記PASSファイル |
| ACL専用シナリオ | 追加・一覧・確認取消・リサイズ・ガイド・置換後拒否・内容一致削除が成功 | `run/jeiSmokeClient/jei-smoke/acl-PASS.txt` |
| 実クライアント終了 | `BUILD SUCCESSFUL in 3m 29s` | `build/reports/jei/client.log` |

895は既存シナリオ、JEIの用途・文字幅・レイアウト、画面状態の確認を含む検査数であり、895種類の独立機能やプレイヤー数を意味しません。入力helperの11検査は別に記録しています。

`19-acl-added.png`、`20-acl-confirm.png`、`21-acl-deleted.png` の生成を確認し、3画面を直接開いて、追加済みの正規化表示、削除前の全内容確認、削除後の全許可表示を確認しました。日本語と入力欄・ボタン・結果欄は読み取れ、重なりはありません。当時の画像保存先は `run/jeiSmokeClient/jei-smoke/` で、`acl-PASS.txt` と既存各シナリオのPASSファイルも確認済みです。この目視確認はローカル実行の画像に対するものです。

### 実行中に見つかった検証側の問題

最初の実クライアント実行では、既存ガイド・診断・転送・静的経路のPASSとACL追加・確認の2画面まで到達した後、従来の180秒の全体制限で停止しました。追加したACLの27段階を含む連続操作のため、検証専用watchdogを300秒へ変更しています。個別アサーション、必須PASS/画像、外側360秒制限は維持し、独立レビューでも確認しました。

続く再実行は、入力helperの11検査が成功した一方、configuration cache再利用直後のクライアント起動でX認証エラーになりました。XvfbのDISPLAY/XAUTHORITYが実行ごとに変わるため、専用スクリプトの `runClient` だけ `--no-configuration-cache` を指定しました。古い実行環境の復元が原因という推定に対する限定的な修正で、これも独立レビュー済みです。いずれの停止も成功扱いにしていません。

watchdogとクライアント起動の変更は、追加の独立レビューでも指摘がありませんでした。修正後は全体を最初から再実行し、上記895検査・入力helper11検査、ACLを含む必須PASSと3画面の生成、終了コード0を確認しました。途中で止まった実行の結果をつなぎ合わせた成功判定ではありません。

## ローカル全体ビルド・GameTest

Gradle 9.2.1 / Java 21.0.12.1 / Minecraft 1.21.1 / NeoForge 21.1.235で、次の完了ログを確認しました。

| 検証 | 結果 | 根拠ログ |
| --- | --- | --- |
| `gradle build --stacktrace` | **成功**。3分30秒、194タスク（170実行・24更新不要）。`check` を含む | `build/reports/network/build.log` |
| `gradle runGameTestServer -PvenusTestCreate=true` | **成功**。40秒、必須165件すべて成功。追加ACL試験1件を含む | `build/reports/network/gametest.log` |
| 全ネットワーク純Java試験 | **105クラスすべて成功**。新規ACLの155+101検査を含む | `build/reports/network/acl-all-tests.log`、上記buildログ |

GameTest環境ではCreate 6.0.10の読込を確認しました。SlashBlade本体は未導入で、既存の実SlashBlade依存検査にはスキップの記録があります。この結果を全任意Modの組合せの検証と読み替えません。起動ログには初期の `server.properties` 不在とMinecraft認証サービスのDNS解決失敗の出力がありましたが、GameTestはその後起動し、必須165件の完了とGradleの成功を確認しています。

ローカルの最初のGradle実行は依存取得前に失敗しました。取得したJDKが環境のプロキシCAを含まないことをJava HTTPSのPKIXエラーで特定し、作業用Gradle設定で既存OSのCAストアと指定プロキシを利用しました。リポジトリの依存設定や証明書検証は変更していません。

## 既存CI・JAR公開（完了）

対象コミット `c600e27d9d644821fdc846e6e04cf27b7d64b602` の [Build 1.21.1 NeoForge / run 39762406445](https://github.com/SSSCFW/VenusMod/actions/runs/39762406445)、[job 119769540371](https://github.com/SSSCFW/VenusMod/actions/runs/39762406445/job/119769540371) の完了結果とログを確認しました。

| CIの検証・処理 | 結果 |
| --- | --- |
| MOD全体Build・check | **成功**。Buildは1分14秒 |
| 新規ACL純Java検査 | AclCommandTest155検査、AclDraftTest101検査が成功 |
| Create付きGameTest | **必須165件すべて成功**。`network_acl_gui:0` の実行を確認。GameTestは51秒 |
| 実JEI・GUI入力 | **895検査成功**、接続111カード・仕様43カード。CIクライアントは3分44秒で成功 |
| 診断artifactアップロード | 成功 |
| JAR検査・公開ステップ | 成功 |
| 任意のDedicated server smoke test | **skipped**。未実行であり、成功したGameTestサーバーとは別の検査 |

CIの診断artifactはID **`12419162045`**、期限は **2026-11-05** です。ダウンロードしたZIPは **11,164,081 bytes**、SHA-256は **`61f44194966121f553ef887737bbcef260ca5b650871384988e6d247a6952ac7`** で、artifactのメタデータと一致しました。

ZIP内の検証画面21枚、`PASS.txt`・`interaction-PASS.txt`・`diagnostics-PASS.txt`・`transfer-controls-PASS.txt`・`static-routes-PASS.txt`・`acl-PASS.txt` の全6ファイルを確認し、`FAIL.txt` はありません。PASSは895検査、接続111カード、仕様43カード、`ja_jp` を記録し、入力helperも11検査成功しています。CIの `client.log` は `BUILD SUCCESSFUL in 3m 44s` です。

今回のCI生成物の `19-acl-added.png`、`20-acl-confirm.png`、`21-acl-deleted.png` も直接開き、規則の全内容、確認中の注意文、削除後の「ACL未適用」を読み取れることを確認しました。文字の欠けや重なりはありません。ローカルの3画像の目視とは別に、CIから取得した3画像を確認しています。取得ZIP・PASS・確認用画像は `build/reports/acl-ci/` に保存しました。

[公開リリース `ci-latest`](https://github.com/SSSCFW/VenusMod/releases/tag/ci-latest) のtargetと本文が `c600e27` を指すことを確認しました。JAR assetの公開メタデータは次のとおりです。

| 項目 | 公開メタデータ |
| --- | --- |
| JAR asset ID | `665494271` |
| サイズ | 2,455,690 bytes |
| SHA-256 digest | `0c8ba57adbe084266d7216a322aea159b6e2c8808ce713a7713ea516b3964fd5` |

公開JARを `build/reports/acl-release/VenusMod-1.21.1.jar` へダウンロードし、実ファイルのサイズ2,455,690 bytesとSHA-256が上記メタデータに一致することを確認しました。JAR内に `AclDraft`、`NetworkAclPanel`、`NetworkAclGameTests`、`NetworkAclInteractionProbe` のclassが存在し、`NetworkServiceCommands.class` に `delete-match` が含まれることも確認済みです。これは公開実ファイルのハッシュ・内容検査であり、そのJARを別の通常プレイ環境で起動した検証ではありません。`ci-latest` は後続の成功ビルドで更新されるリリースです。

## 独立レビュー・残る確認

実装後に独立した差分レビューを実施しました。ネイティブ試験での数字キー入力を、既存helperで対応している `input.type("1")` へ修正しました。修正後の実クライアント試験では、この入力経路を含めてInventoryとカーソル上のアイテムが保持されることを確認しました。起動監視・キャッシュ変更も追加の独立レビューを受けています。

軽微な指摘として、**ドラッグ操作をRobotの実入力シナリオに含めていない**点を記録します。モーダルのドラッグ入力経路はコードレビュー済みですが、ネイティブ実入力での遮断は未検証です。今回、入力workerにドラッグ機能は追加していません。

既存ブランチへのpush、既存CIの全必須ステップとJAR公開の成功を、上記コミット・runで確認しました。追加証拠としてCI診断ZIPのハッシュ・PASS・画像、および公開JARの実バイトのハッシュ・内容まで照合済みです。ドラッグ実入力と下記の通常プレイ環境に関する未検証範囲は残しています。

## 範囲

機器保存v16と通信network-3を維持し、ケーブル・画像・モデル・レシピの変更はありません。GUI/JEIは23トピック97手順の共通説明を使います。新しいGUI下書きや確認状態は保存しません。

FakePlayerによる認可検査は、人間同士のマルチプレイ検証とは区別します。Xvfb上のRobotによる操作と3画面の確認は実施しましたが、人による入力、IME、日本語キーボード、全GUI倍率、シェーダー、大規模TPS計測は未実施です。ドラッグ実入力は、前節のとおり未検証として残しています。
