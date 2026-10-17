# NAT/NAPT 検証記録

## 実行済みの純Java検査

変更前はNatIntegrationTestの11ケースがNAT未実装により失敗、基準の「privateへの返信経路なし」だけが成功することを確認しました。サービス宛先パーサーも未実装の状態でテストの失敗を確認しました。

実装後にJava21で全ネットワーク単体/結合試験を再コンパイルして実行しています。

- NatIntegrationTest: 81検査、失敗0。
- NatTableTest: 25検査。
- NatServiceTest: 27検査。
- NatRoutingTest: 34検査。
- NatLimitsTest: 15検査。
- 既存NetworkCore 47、Continuation 28、Services 162、RequestBudget 10,009、Guide 111検査。
- 既存OSPF Engine 22、Integration 36、Presence/Commandの検査。
- JDKアセット生成と参照検査: 174 JSON・74 PNG、全参照解決。

NATの182検査に加え、上限試験では実際に2048エントリーを埋めて上限と回収を検査しています。検査数は速度測定や独立したマルチプレイ操作数ではありません。

## ネイティブ試験とCI（確認済み）

検証対象コミットは **`bcdf4bf592369d585a4354186855ae7e033238f7`**。
[Build 1.21.1 NeoForge / run 39041994197](https://github.com/SSSCFW/VenusMod/actions/runs/39041994197) の完了状態とダウンロードした診断ログを確認しました。

| 検証 | 結果 |
| --- | --- |
| MOD全体のBuild / check | 成功 |
| Native GameTests with Create | 必須111件すべて成功 |
| JAR内の既存機器説明検査 | 成功 |
| JAR準備・公開 | 成功 |
| CI試験入力とローカルの照合 | 変更25ファイルすべてバイト一致。既存分を含む424ファイルも一致 |

111件は既存機能を含むリポジトリ全体の件数で、今回の新規NetworkNatGameTestsは2件です。通常のブロック配線によるNAPT・ポート転送・入力ACL、実際のBlock.getDropsによるNAT規則・所有権・モジュール・故障の保存復元、サービス公開と機器管理権限の区別を検査しています。実プレイヤー同士の操作試験とは異なります。

検証環境はMinecraft1.21.1、NeoForge21.1.235、Create6.0.10、Java21。JEI/SlashBlade本体はこのGameTest環境にはなく、既存のSlashBlade実体依存検査の一部はスキップされます。任意の通常専用サーバースモークテストは今回選択しておらず、GameTestサーバーの起動・実行とは区別します。

Release `ci-latest` の `VenusMod-1.21.1.jar` が上記コミット向けに公開されたことを確認しました。公開時のSHA-256: `ae1238b9d9dda0f4d908778a57cfc07fa0fa5478ded442744f646bdb8ae53826`。このリリースは後続の成功ビルドで上書きされます。

既存ガイド検査のアイコン確認とOSPFの4手順確認は維持しています。総カード数は追加前の固定数ではなく各ページの手順数の総和と照合し、既存トピックおよびNATがすべて存在することも検査します。

旧形式の移行試験は、変更前コードから生成した固定v3 fixtureを使います。現在のencode結果を旧形式と思い込んで末尾を切る方法をやめ、v1/v2の移行・未知形式の拒否・切り詰められた保存データの拒否を維持しています。

## 未実施

実クライアント表示、JEIの実操作、シェーダー併用、複数プレイヤーによる実操作、大規模TPS測定。テクスチャの参照検査と、実際の画面を目視したことは区別します。
