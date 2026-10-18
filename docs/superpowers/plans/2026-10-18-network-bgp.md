# BGP IPv4 実装計画

> 実行方法: 承認済み全体仕様の続行。executing-plansでこのセッション内で実装・検査する。

**Goal:** 明示的なピア設定、OPEN/KEEPALIVE/UPDATE、eBGP/iBGPの広告規則と実転送を追加する。
**Architecture:** Minecraft非依存の設定/エンジン、既存L2/IPv4/ACLへのアダプター、CLI/GUI/JEI共通ガイド。
**Tech Stack:** Java21 / NeoForge21.1.x / Minecraft1.21.1。Create/JEIは任意。
**Spec:** `docs/NETWORK_SYSTEM_SPEC.md`。BGPはゲーム用IPv4サブセットで、実ソケットを使わない。

## 維持する条件

既存 `feature/venus-hostile-mobs` と既存Buildワークフローだけを使用する。
4/16幅のケーブル、既存PNG/モデル/レシピ、機器の所有権・故障・強化は変更しない。
AS_PATH、NEXT_HOP、LOCAL_PREF、MED、ピア別prependとnext-hop-selfを設定結果に反映する。
ピア形成・NEXT_HOP解決はconnected/static/OSPFの下位経路を使い、BGP自体による自己依存を避ける。
eBGPは直結、iBGPは下位経路で到達可能な明示的インターフェースIPまで。仮想TCPはIPv4/ACL検査のみ。
eBGPのimport/exportは明示許可がない限り停止。広告元は明示networkと完全一致する下位経路が必要。
経路選択はLOCAL_PREF→自機生成→AS_PATH長→同一隣接AS内MED→eBGP→下位コスト→Router ID→ピアIP。
IPv6、RR/confederation、AS_SET、BGP multipath、GR、完全なTCP/BGPバイト列は対象外。

## タスク

- [x] 1. 既存コードでBGP CLIと実到達の失敗を確認する。
- [x] 2. `BgpSettings`とv7保存、`BgpCommands`の非破壊検証を追加する。v1〜v6読み込みを維持する。
- [x] 3. `BgpEngine`を追加。相互設定・ASN/ID・期限・ループ・withdraw・政策・NEXT_HOPの単体試験。
- [x] 4. `BgpSimulation`を既存IPv4に接続。L2/VLAN/ACLと静的/OSPFの下位経路、転送時のBGPを統合。
- [x] 5. GUI/JEIの図付き共通ガイド、実ブロック通信とItemStackの保存GameTestを追加する。
- [ ] 6. 全体回帰・アセット検査・差分自己レビュー、GitHubへpushし既存CI結果を確認する。

## 重点確認

- BGPの無効化/片方向切断/AS不一致で、学習済み経路が期限とともに消えること。
- iBGP NEXT_HOPを勝手に到達可能としない。iBGP学習→別iBGPピアへ再広告しない。
- 不適切な広告、件数/AS_PATH/待ち行列上限で一部だけ成功したふりをしない。
- 旧保存のv6 fixtureを変更前コードで生成し、静的設定だけを維持する。
- 別物理網の配線変更で確立済みセッション・NATや転送をリセットしない。

## 記録

下位到達判定は実通信経路だが、TCP再送/衝突解決の全機能は実装しない。
完全なプロトコル再現と手動クライアント目視/大規模TPS測定の合格を混同しない。

ローカルJava21で新規151検査、既存ネットワーク検査一式、174 JSON/74 PNGの生成・参照検査が成功。BGP未対応時の公開CLI失敗、NAT併用/下位経路変化/直結候補の回帰を確認して修正した。全体ビルドと実ブロックGameTestは既存CIで検証する。
