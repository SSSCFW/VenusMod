# ポート帯域劣化の検証記録

基準head: `7deadb0431f3a3d73721c4381344f0a0c21a9c88`。コード入力は成功CI `7b6cabc` のnetwork-test-inputsです。基準headとの差が閻魔刀と検証文書だけで、ネットワーク入力は同一とlive GitHubの比較で確認しました。

## 失敗先行とローカルJava21

公開CLI `port 0 degrade` の未対応による失敗、新しいモデルAPIの欠落によるコンパイル失敗、共通ガイドの欠落を確認してから実装しました。旧v15 fixtureは変更前の実クラスを動かして生成した固定文字列です。

| 新規検査 | 成功数 |
| --- | ---: |
| PortDegradationContractTest | 2 |
| PortDegradationModelTest | 30 |
| PortDegradationNetworkTest | 27 |
| PortDegradationCodecTest | 40 |
| PortDegradationGuideTest | 8 |

合計107検査。既存を含む91テストクラスをJava21で再コンパイル・実行してすべて成功しました。1,594 JSON・204 PNGのアセット参照検査も成功。画像/モデル/レシピ生成器は変更していません。

検査は、両端劣化の二重減速防止、WAN律速、固定速度不一致、正常リンクと仮想転送量、独立した物理網の保護、修理、完全安定化、故障確率の共通ゲート、旧保存互換、破損/重複故障ビット、設定復元での物理故障保護を含みます。抽選は決定的な乱数を与えて検査しており、実環境での発生頻度の統計測定ではありません。

既存の書出し形式期待値だけv16へ更新し、旧fixture・設定内容の検査は維持しました。Syslog severity、NTP drift、許可VLAN、旧ポート故障maskの破損検査は新しい末尾1byte分だけ位置を調整しています。

## ネイティブ・CI

NetworkPortDegradationGameTestsを2件追加しました。青色ケーブルの通常配線、実メニュー/FakePlayer、仮想転送と通常サーバーtick、修理ItemのuseOn、Block.getDropsからの再設置を使います。論理リンク・完了転送量を直接注入していません。

この文書の初回作成時点では全体ビルドと新しいGameTestは未実行です。該当コミットの既存Actionsで結果を確認してから追記します。

## 未実施

実クライアント目視、JEI画面、シェーダー併用、実際の複数人操作、大規模TPS測定は未実施。模擬プレイヤーを実マルチプレイ操作と同一視しません。独立レビュー用のサブエージェントは使用せず、差分自己レビューを実施します。
