# OSPF intra-area implementation plan

Spec: docs/NETWORK_SYSTEM_SPEC.md (既存の承認済み設計)。基準コード 36a6ed40。
Goal: Java仮想ネットワークでHello/隣接確認/LSA配布/経路計算/迂回を機能させる。

Constraints: 既存のfeature/venus-hostile-mobsのみ、Create/JEI任意、画像と細い配線を維持。外部ソケットなし、チャンク強制ロードなし。旧機器設定v1/v2移行を維持。
Ruling: 今回は各機器1エリア・全隣接方式のゲーム用OSPFv2サブセット。ABR、DR/BDR、認証、LSリクエスト/ACKバイト列の完全再現は対象外。代わりに広告・隣接状態・LSDB・SPFの因果関係を実装し、対象外はGUI/ガイドへ表示。

- [x] 独立した設定/保存とOSPFエンジンの回帰テストを先に作成・失敗確認。
- [x] Hello/Dead/Full遷移、単一エリアLSDB広告、Dijkstra、passive、コストを実装。イベント数/キュー/機器上限を固定する。
- [x] 既存L2/VLAN/リンク/ACLから隣接を判定し、静的経路を変更せず動的経路を注入。サーバーtickから駆動。
- [x] 仮想CLI/共有ガイド、保存v3とv1/v2移行を接続。実配線GameTestを追加。
- [x] 純Javaテストを実行し、差分確認してpush。既存Actions build/GameTest/アセット確認（eee6891 / run39036821316）。

Review focus: 古いLSAや片方向障害で経路を残さない、低コストだが未到達経路を選ばない、他エリアを自動接続しない、静的経路との優先順位、設定/装着品保存の破壊禁止。

検証詳細: docs/NETWORK_OSPF_VERIFICATION.md。旧テストの保存形式と手順数の期待値を、今回の正式追加に合わせて更新。旧互換・不正形式拒否・アイコン実在の検査は削除していません。
