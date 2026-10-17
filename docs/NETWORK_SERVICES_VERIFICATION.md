# ACL・DHCPリレー・図付きガイドの検証結果

検証対象コード: `8a3f40d4332577a80737c85d715a0cdccf9e1fac`。仕様と操作は [NETWORK_SERVICES_GUIDE.md](NETWORK_SERVICES_GUIDE.md) を参照してください。

[Build 1.21.1 NeoForge / run 39032056075](https://github.com/SSSCFW/VenusMod/actions/runs/39032056075) の完了ログを2026-10-17に確認しました。

| 検査 | 結果 |
| --- | --- |
| `gradle build --build-cache --stacktrace` | 成功 |
| `NetworkCoreTest` | 47アサーション成功 |
| `NetworkContinuationTest` | 28項目成功、失敗0 |
| `NetworkRequestBudgetTest` | 10,009アサーション成功（うち10,000は上限後の拒否反復） |
| 新規 `NetworkServicesTest` | 159項目成功、失敗0 |
| 新規 `NetworkGuideTest` | 10トピック、図・本文89項目成功 |
| `NetworkAssetValidator` | 174 JSON・74 PNG、全参照を解決 |
| `gradle runGameTestServer -PvenusTestCreate=true --stacktrace` | 必須107件すべて成功 |
| JAR内の既存機械説明検査・JAR公開 | 成功 |

107件はリポジトリ全体の件数です。今回の追加GameTestは3件で、実ブロック配線からのDHCPリレー・ACL制御・機器アイテムへの予約保存、v1設定の移行、接続図で使う登録済みアイコンを検査しています。通常の配線解析を通し、論理リンクを手で注入していません。

純Javaの追加試験は、ICMP拒否とTCP80許可の分離、応答方向のACL、UDP53/TCP5000、誤設定の非破壊性、giaddrに対応する配布サブネット、戻り経路なし・UDP67拒否・ACK拒否での未配布、プール枯渇・VIP除外、再読込した予約、旧設定読み込み、ACL件数上限を含みます。

環境: Minecraft 1.21.1、NeoForge 21.1.235、Create 6.0.10、Java 21。JEIとSlashBlade本体はGameTestの実行環境には入っていません。JEI連携クラスはAPIに対するコンパイルを通っていますが、JEI画面の実操作を確認したという意味ではありません。既存のSlashBlade実体依存チェックの一部は未導入としてスキップされています。

ケーブル形状とアセット生成処理は変更しておらず、4/16幅と独自テクスチャを維持しました。図付きガイドはその登録済み機器アイコンを使用します。

**未実施:** 手動の実クライアント表示、JEIカテゴリの実操作、シェーダー併用、複数人での実操作、大規模TPS計測、任意の通常専用サーバースモークテスト。GameTestサーバーの起動・実行とは区別します。

**残る仕様:** DHCPは手動要求・期限なし予約で、T1/T2やリース期限は未実装。ACLはステートレスIPv4メタデータ検査。OSPF/BGP/IPv6/NAT/IPsec/LACP、本来のSTP/VRRPタイマー、8/24ポート、PON分岐、惑星間通信等は未完了です。全体仕様の完成や全描画環境での動作保証とはしていません。
