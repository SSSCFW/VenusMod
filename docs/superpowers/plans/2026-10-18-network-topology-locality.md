# Network topology locality implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans. この会話で承認済み全体仕様の継続実装。

**Goal:** 一部の配線変更が無関係な拠点の通信・NAT・冗長化をリセットしないようにする。
**Architecture:** NetworkWorldは同一NetworkSimulatorを維持する。ケーブルの接続区間とチャンク依存の逆引き索引から変更区間だけ予算付きで再走査する。論理接続変更時の無効化は旧/新の物理連結成分に限定し、プロトコルエンジンを捨てない。
**Tech Stack:** Java21、Minecraft1.21.1、NeoForge21.1.235。新しい依存なし。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md §11、docs/NETWORK_VRRP.md、docs/NETWORK_STP.md。

## Global constraints
- ブランチはfeature/venus-hostile-mobsのみ。既存build-1.21.1.ymlのみを使用。
- 4/16幅、独自テクスチャ・レシピ、保存v6を変更しない。Create/JEIは任意。
- 実ネットワーク/外部URLを使わず、未ロードチャンクを強制ロードしない。
- 切断したリンクを再走査まで有効のまま残さない。
- 未ロード区間の永続的な中継、RSTP/BGP等は今回の対象外。

## Review focus
- 切断直後や走査中の再編集で古いリンクを誤公開しない。
- T字分岐撤去・別規格への交換・ループ・端点削除・再設置を局所走査で正しく認識。
- 再配置による機器ID変更、同時複製、アンロード後の参照を残さない。
- 変更した連結成分から離れたNAT予約/転送、OSPF・VRRP・STPの時計を維持。
- 上限時は明示的な停止。古い索引やタイマー/作業列を無制限に増やさない。

## Tasks
- [x] 既存検査を実行し、独立ネットワークの切断/設定変更で転送が失われる回帰をREDで確認。
- [x] NetworkSimulator/NatTable/プロトコル境界を連結成分単位の失効へ変更し、GREENで確認。
- [x] CableTopologyの純Javaな索引・予算付き局所走査をRED→GREEN。World adapterを置き換える。
- [x] 実ケーブルと通常tickによる別ネットワーク保持・即時断線・継続走査GameTestを追加。
- [ ] 全純Java試験、アセット参照検査、自己レビュー、既存CIのBuild/GameTestを実行し同ブランチへpush。
- [ ] 変更範囲と測定/目視未実施を正直に記録する。

## Rulings / ledger
- ローカルはCI artifact内のソーススナップショット(7f69771)を隔離ディレクトリへ復元。現head36903bfとの差は検証記録のみで、変更対象のコードは同一。ローカルgitは差分検査用。GitHub更新はheadの楽観ロック付き。
- 厳密な「利用フローだけ」の失効ではなく物理連結成分単位を採用。VLANやルーティングで分かれた同一物理網内では保守的に再確立する。異なる物理網の性能/状態は保護する。
- 既存24種の純Java検査を2026-10-18に実行、すべて成功。Gradle本体はローカル未導入で外部DNS不可、全体検証には既存GitHub Actionsを使う。

- RED: 独立物理網の切断/追加/設定変更/VRRP/STPが無関係なNAT・転送を消す6ケースを再現。修正後は局所性63検査に成功。別の不正CLI入力も実接続を切断することを再現し、コピー検証後の反映へ変更した。
- ケーブル索引は通常/途中編集/チャンク/反復編集の検査と1500編集の独立全走査オラクル照合が成功。1000区間・5000ケーブルで局所処理量を確認。TPS計測ではない。
- Ruling: チャンク中継は従来どおりロード範囲だけ。未登録の物理機器も端点/分岐として数え、空IDの端点をリンク化しない。
