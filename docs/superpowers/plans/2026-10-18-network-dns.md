# DNSキャッシュ・複数Aレコード Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans。承認済みの全体仕様の続きを同一作業ブランチで実装する。

**Goal:** ゲーム内DNSだけで名前による機器接続を可能にし、TTL・ラウンドロビン・NXDOMAINキャッシュを観察できるようにする。
**Architecture:** 既存の単一AレコードMapを保存互換の一次アドレスとして維持し、DnsSettingsで追加アドレス/TTLを管理。DnsResolverが機器単位の有限キャッシュと期限を所有し、問い合わせの輸送は既存UDP53/NAT/ACLに委譲する。実アプリ接続は解決後に既存のサービス/権限判定を必ず実行。
**Tech Stack:** Java21、Minecraft1.21.1、NeoForge21.1.235。Create/JEIは任意。
**Spec:** docs/NETWORK_SYSTEM_SPEC.md（DNS、キャッシュ、TTL、ラウンドロビン、安全な仮想サービス、軽量化）。

## Global Constraints

作業は feature/venus-hostile-mobs のみ。既存workflowのみ。実DNS/ソケット/HTTP/OSは使用しない。ケーブル4/16幅、74PNG、モデル/既存レシピは変更しない。保存v1〜v7の読込みを維持する。最初の書込み前に対応テストの失敗を確認する。

## Scope / Rulings

IPv4 Aレコードのみ。CNAME/AAAA/PTR/再帰/委譲/SOA/DNSSEC/mDNS/TCPフォールバックは今回対象外。旧CLI dns-record NAME IP は互換維持。新CLI dns-record set NAME TTL IP...、delete、ttl、list、dns-server on/off、dns-negative-ttl、dns-cache [clear] を追加。nslookupは常に直接問い合わせる診断で、アプリ接続は機器キャッシュを使用。全RRsetの順序をTTL中保持し、キャッシュヒットでTTLを延長しない。TTL0は保存しない。権威側が返す順番だけを成功した問い合わせごとに回転し、健康状態に応じて自動切替しない。

名前は大小文字と末尾の1個のドットを正規化。通常ホスト名はASCII、各ラベル1〜63/総長64文字まで。数値とドットだけの不正IPv4はDNSへ回さない。旧形式で許可されていた特殊な名前も保存から勝手に削除しない。

キャッシュ: 機器64名、全体4096名、RRset8 IPまで、TTL0〜86400秒、否定TTL0〜300秒（既定10秒）。正TTL既定60秒。期限はサーバーtick・1エントリー1期限で更新。1tick最大32期限処理、期限未回収でも古い結果は使わない。DNS設定・自機のネットワーク設定変更と機器削除で自機のキャッシュだけを初期化。別機器のレコード変更は既存クライアントのTTLを消さない。

## Review Focus

- TTLの直前/境界/ゼロ/大量tick、読取りで期限延長しない。
- UDP53/応答ACL/NAT変換失敗をNXDOMAINと混同せず保存しない。
- レコード更新直後の古いキャッシュと、異なる機器/重複プライベートDNSアドレスの分離。
- 不正URI/IPv6/数値IP/巨大入力/CLI失敗で設定・フローを破壊しない。
- 旧保存の読み込み、破損/過大RRsetの拒否、実ItemStackへのTTL・装着品・所有権保存。

## Tasks

- [x] 契約試験を既存コードに対して失敗させ、DnsSettings/DnsResolverと保存v8を追加する。
- [x] 名前対応をping/trace/open/transfer/redstoneへ統合し、既存ServiceAddressの厳密な数値APIは維持する。GUI・JEI共通DNSガイドを追加する。
- [x] 期限/ラウンドロビン/ACL/NAT/分離/上限の回帰とGameTestを追加。全体回帰・アセット検査・CIを実行しpush後の結果を記録する。

## Verification ledger

開始点756a996、コードは7f89456のCIソース。ローカルJava21の基準NetworkCore49/Services162成功。現時点で新機能は未実装。

ローカルの新規165検査と既存を含む39テストクラス、174 JSON/74PNG検査が成功。CLI構文不足とDNS設定切替の失敗を再現して修正した。BGP旧保存の試験はv6 fixtureを維持し、新v8ヘッダー期待値を更新。全体CIと実ブロック試験はpush後に確認する。


60c8c6fをpushし、既存CI run39101205555のBuild/check、必須123 GameTests、JAR公開が成功。CIソースとローカルの変更25ファイルのGit blobハッシュは全て一致。検証結果をdocs/NETWORK_DNS_VERIFICATION.mdへ追記しました。実クライアント目視・JEI実操作・実マルチプレイ・TPSは未実施です。
