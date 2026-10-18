# VenusMod

Minecraft 1.21.1 / NeoForge starter template.

## Environment

- Minecraft 1.21.1
- NeoForge 21.1.x (`21.1.235` pinned initially)
- Java 21
- Gradle 9.2.1

## Project identifiers

- Mod ID: `venusmod`
- Java package: `dev.ssscfw.venusmod`
- Main class: `dev.ssscfw.venusmod.VenusMod`

Version, NeoForge version, mod ID/name and group are managed in `gradle.properties`.

## Local development

Build:

```bash
gradle build
```

Run client:

```bash
gradle runClient
```

Run dedicated server:

```bash
gradle runServer
```

Run data generation:

```bash
gradle runData
```

Build output is written to `build/libs/`.

## GitHub Actions

The `Build 1.21.1 NeoForge` workflow automatically builds and publishes the mod after each push to `feature/venus-hostile-mobs` or `main` (after the workflow change is merged to `main`). Manual runs are also available. Automatic push runs build the JAR; the optional dedicated-server smoke test is selected for manual runs.

To run it manually:

1. Open **Actions**.
2. Select **Build 1.21.1 NeoForge**.
3. Click **Run workflow**.
4. Select the branch to build.
5. Enable **Run dedicated server smoke test** when registry/data-pack/server startup validation is needed.

The workflow uses Java 21, Gradle 9.2.1, Gradle/configuration build caches, and optionally starts a dedicated NeoForge server until it reaches the ready state.

On success, the built mod is published to the rolling prerelease `ci-latest` as `VenusMod-1.21.1.jar`, so it can be downloaded directly as a JAR rather than a ZIP artifact.

Direct JAR URL after the first successful build:

`https://github.com/SSSCFW/VenusMod/releases/download/ci-latest/VenusMod-1.21.1.jar`

## Suggested source layout

```text
src/main/java/dev/ssscfw/venusmod/
  VenusMod.java
  client/
  config/
  event/
  item/
  network/
  registry/

src/main/resources/
  META-INF/neoforge.mods.toml
  assets/venusmod/
  data/venusmod/
```

Add registries and mod-bus listeners from `VenusMod.java` as features are implemented.

## ボス施設と報酬

金星将軍の城塞・アフロディーテ神殿の構成、探索コマンド、既存構造物の壁修正、王の財宝と王の宝物庫の入手方法は [ボス施設の仕様](docs/BOSS_STRUCTURES.md) を参照してください。

## 閻魔刀の次元移動

魔剣「閻魔刀」を持ってHキーで、地点保存・フォルダー整理・転送条件を設定できる「次元移動」を開けます。操作と隙間の設定は [閻魔刀の次元移動](docs/YAMATO_DIMENSION_TRAVEL.md) を参照してください。

## ネットワークインフラ

4/16幅のLAN・光ケーブル8種類、ネットワーク機器8種類、Java製の仮想通信、ポート強化、故障・設定保存、機器GUI・JEI説明を追加しています。操作例と実装範囲は [ネットワーク機能の使い方と実装状況](docs/NETWORK_IMPLEMENTATION.md) を参照してください。

全体計画は [ネットワークインフラ仕様書](docs/NETWORK_SYSTEM_SPEC.md) です。**現段階は初期実装であり、全仕様の完成ではありません。IPv6、複数エリアOSPF、BGP/STP/VRRPの未対応拡張などの残項目は各機能の実装状況に明記しています。**

### 動的経路・仮想アドレス変換

[OSPFによるエリア内経路学習](docs/NETWORK_OSPF.md) と [NAT/NAPT・ポート転送](docs/NETWORK_NAT.md) に対応しています。NATは既定OFFで、GUI/JEIガイドから内外ポート・公開設定・制約を確認できます。これらはMinecraft内だけの仮想通信です。

## VRRP広告・タイマー

簡略VIP選出を、Master/Backupの広告・期限・preempt・仮想MAC・広告ACLの方式に更新しました。旧形式の設定と細いケーブルは維持します。手順と制限は [VRRPの構成と使い方](docs/NETWORK_VRRP.md) を参照してください。従来の「VRRP広告タイマー未対応」という記述はこの更新より前の実装状況です。

## BPDUによるClassic STP

VLAN別のルート選出、Root/Designated/Alternate、Listening/Learning/Forwarding、edgeとBPDU guardを追加しました。旧来のsimpleと選択式です。[STPの操作と再現範囲](docs/NETWORK_STP.md)、[検証記録](docs/NETWORK_STP_VERIFICATION.md) を参照してください。細い4/16幅ケーブルと既存テクスチャは維持します。

## ネットワーク配線の局所更新

配線の一部を変更しても、物理的に独立した別拠点の通信・NAT・OSPF/VRRP/STPを維持します。変更したケーブル区間だけを予算付きで再解析します。操作・無効化範囲・上限は [配線の局所更新](docs/NETWORK_TOPOLOGY.md)、実施した検査と未検証範囲は [検証記録](docs/NETWORK_TOPOLOGY_VERIFICATION.md) を参照してください。

## BGPによる拠点間経路制御

IPv4 eBGP/iBGPのピア、経路広告・撤回、AS_PATH・NEXT_HOP、ピアごとのLOCAL_PREF/MED/prependとフィルターを追加しました。接続例・制約は [BGPの使い方](docs/NETWORK_BGP.md)、検証状況は [BGP検証記録](docs/NETWORK_BGP_VERIFICATION.md) を参照してください。全プロトコルの実装完了を意味しません。

## DNSと名前による設備接続

ゲーム内DNSのTTL付きキャッシュ、複数Aレコードのラウンドロビン、名前でのping・Web・仮想転送・レッドストーン操作に対応しました。外部DNSには接続しません。[DNSの使い方と制約](docs/NETWORK_DNS.md)、[検証記録](docs/NETWORK_DNS_VERIFICATION.md) を参照してください。
