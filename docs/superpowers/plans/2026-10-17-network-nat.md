# 仮想NAT/NAPT 実装計画

> 実行: superpowers:executing-plans。承認済みのネットワーク全体仕様を段階実装する。

**Goal:** 拠点内端末のNAPTと、指定した仮想サービスのポート転送を安全に動かす。
**Architecture:** 永続設定と期限付き変換表を分離する。経路上の各ルーターで入力ACL→宛先変換→経路選択→送信元変換→出力ACLの順に処理し、往復に成功した要求だけ変換表を確定する。
**Tech Stack:** Java21、Minecraft1.21.1、NeoForge、既存のJava仮想ネットワーク。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md。既存OSPF/ACL/DHCP・機器所有権と整合させる。

## 制約

- feature/venus-hostile-mobsのみ使用。既存Build 1.21.1 NeoForgeのみ使用し、新しいworkflowは追加しない。
- 外部ブラウザー、HTTP、ソケットを使わない。4/16幅のケーブルと全既存テクスチャを維持する。
- NAT設定は既定OFF。保存v1/v2/v3を移行し、稼働セッションはアイテム化・再起動で保持しない。
- まずTCP/UDPのNAPT、仮想ICMP照会ID、外側インターフェースIPへのポート転送を扱う。任意公開IPの1対1マッピング、hairpin、IPv6 NAT、ALG、NAT状態のVRRP同期はこの段階では扱わない。

## 作業

- [x] RED: private経路への応答失敗、NAPT、複数端末、ACL拒否の原子性、ポート転送、設定復元のテストを先に実行。
- [x] NatSettings/NatTable: 有限の変換表・期限・衝突回避・静的ルール・要求単位の仮反映を実装。
- [x] NetworkSimulator: 変換後のIP/ポートで次ホップと返信を計算。OSPF、ACL、DHCPの既存試験を維持。
- [x] CLI・GUI操作・共通JEIガイド、保存v4、操作説明を追加。
- [x] 回帰試験、実配線・実ItemStackのGameTest、既存アセット検査、全体ビルドを実行してpush。
- [x] 結果を確認し、クライアント目視・大規模TPS等の未検証範囲を区別して記録。

## 重点レビュー

変換ポートの競合・第三者からの偽応答、未一致の受信、ACLの変換前後の順番、失敗時の未確定エントリー、期限切れ/長時間転送、同じIPを使う別拠点、保存容量/不正形式、OSPF再収束時のセッション破棄を検査する。

テスト入力はbuild/reports/network/network-test-inputs.zipに明示したリポジトリファイルだけを収める。既存CI診断artifactから、実行した版の単体試験を再現できるようにする。ワールド、秘密情報、Gradleキャッシュ、ホスト設定は含めない。

## 作業記録

0b37179の試験入力を既存CI artifactから取得し、未実装の11ケースが失敗することを確認した。古いOSPF試験の「現在形式から末尾を切ればv2」という生成方法はv4追加で成立しなくなるため、実際の変更前v3から作った固定fixtureに置き換えた。拒否試験や旧版移行試験は削除していない。

実装bcdf4bfの既存CI run39041994197はBuild、必須111 GameTests、JAR公開に成功。NATの純Java182検査も成功し、CIに収めた変更25ファイルのバイト一致を確認した。結果はdocs/NETWORK_NAT_VERIFICATION.mdへ記録。実クライアント・JEI実操作・複数人操作・大規模TPSは未実施。
