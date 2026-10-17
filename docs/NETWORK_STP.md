# STP: BPDUによるL2ループ防止

Minecraft1.21.1 / NeoForge。承認済み [全体仕様](NETWORK_SYSTEM_SPEC.md) に基づき、VLAN別の**ゲーム用Classic STP**を追加します。RSTP/PVST/MSTの実機互換や全仕様の完成を意味しません。IPソケットや外部ネットワークは使用しません。

## 方式と移行

従来の即時ループ防止は `simple` として残します。既存拠点に突然30秒の起動待ちを強制しないため、旧設定と新規機器の初期値はsimpleです。BPDUとタイマーを使うには、接続するすべてのL2中継機器で次を設定します。

```text
stp mode classic
```

classicとsimple、またはSTPをOFFにした中継機器との混在リンクは、classic側で停止し、`stp status` に理由を表示します。互換性のない制御方式を黙って接続して保護済みとは扱いません。PC・サーバー・L2中継しないルーターは端末として接続できます。L3スイッチ、ONU、OLTのL2側にも適用できます。

## 三角形の冗長配線

```text
      SW-A (root候補)
      /            \
    SW-B -------- SW-C
```

各機器間を独立したケーブルで接続し、ケーブル途中のT字分岐は作らないでください。P0北/P1東/P2南/P3西/P4上/P5下です。筐体の見た目の向きとは別の世界方位です。

Aをroot候補にする例:

```text
stp priority 4096
```

BとCは初期32768のままにします。優先度は0〜61440の4096刻み。Bridge IDは優先度と機器固有MACで構成し、小さい方を優先します。経路は**受信したBPDU**から計算し、全体グラフからルートを即決するsimpleとは異なります。

コストとポートの優先度:

```text
stp port 0 cost 20000
stp port 0 priority 128
stp port 1 cost auto
stp vlan 10 priority 8192
stp vlan 10 priority auto
```

自動コストはリンク速度に従い、1Gbps=20000、10Gbps=2000が例です。明示コストは1〜200000000。低い累積コスト、送信側Bridge ID、送信側Port ID、受信側Port IDの順に比較します。ポート優先度は0〜240の16刻みです。VLAN別のBridge priorityを設定すると、VLANごとに異なるrootを選べます（最大32個の上書き）。

## ポートの状態とタイマー

```text
stp status
stp status 10
show spanning-tree
show spanning-tree 10
```

root、root port、累積コスト、各ポートの役割・状態・BPDU送受信数・期限を表示します。閲覧でタイマーを進めたり初期化したりしません。

| 状態 | 仮想データ転送 | MAC学習 | BPDU受信 |
| --- | --- | --- | --- |
| Listening | しない | しない | する |
| Learning | しない | する | する |
| Forwarding | する | する | する |
| Blocking (Alternate) | しない | しない | する |
| Disabled / Err-disabled | しない | しない | 稼働しない |

rootへ向かうポートをRoot、区間に対する代表をDesignated、代替をAlternateとして表示します。非edgeのRoot/DesignatedポートはListening→Learning→Forwardingの順に進みます。リンク断やroot変更では再選出し、待ち時間を経て予備経路を開きます。古いBPDU情報も寿命で失効します。

```text
stp timers 2 20 15
```

順にHello、Max Age、Forward Delay（秒）。標準は2/20/15です。ListeningとLearningはそれぞれ15秒なので、選出が安定してから転送まで通常合計30秒待ちます。実時間はMinecraftサーバーのTPSに依存します。

範囲はHello1〜10秒、MaxAge6〜40秒、ForwardDelay4〜30秒で、`2*(Hello+1) <= MaxAge <= 2*(ForwardDelay-1)` を満たす必要があります。短い検証用の設定例は `stp timers 1 6 4` です。root以外は選出したrootのBPDUにあるタイマーを運用値として使用します。

これはRSTPの高速なハンドシェイクではありません。BPDUは隣接bridge間のみで配送し、Blocking中も制御情報を受信します。**IP ACL・ルーティング・NATを通すIPv4通信とは別**なので、IP ACLでBPDUだけを遮断する操作はありません。

## edgeとBPDU guard

PCだけをつなぐ面で、起動待ちを省略する設定:

```text
stp port 0 edge on
stp port 0 guard on
```

edgeは即時Forwardingになりますが、BPDUを受信したらedgeとしての即時転送を解除します。guardも有効ならErr-disabledで停止します。コストやBridge priorityの変更だけでは、受信済みBPDUによるedge解除やguard停止を消しません。

配線を直した後に解除:

```text
stp port 0 reset
```

誤接続のスイッチが残っていれば、次のBPDUで再停止します。guardを使わない設定は `stp port 0 guard off`。スイッチ間リンクにedgeを設定することは推奨しません。

## 故障・保存・他機能

`fault` と修理キットで故障・復旧を試験できます。使用中のスイッチが故障した際は、別の配線が存在し、BPDUが届き、必要な待ち時間が過ぎたときに迂回できます。

設定は保存形式v6へ追加し、v1〜v5を読み込みます。方式、priority、VLAN別priority、ポートcost/priority、edge/guard、タイマーは通常の機器ドロップで装着品・故障・所有権とともに保持します。選出状態・受信BPDU・実行時guard停止・期限は保存せず再確立します。古いMODへ戻す前に、更新前のワールドバックアップを残してください。

STPで転送状態が変わると、MAC/ARP・NAT・転送中フローを失効させ、OSPF/VRRPの到達候補を再計算します。切替をまたぐ無損失転送やNAT状態同期を保証しません。既存の実装ではこの失効はディメンション単位です。

また、**物理ケーブルの設置/撤去では現在のNetworkWorldがトポロジーを再作成するため、STPも起動待ちから再開します。** 単なる機器故障・設定変更は状態機械を維持できますが、物理変更の局所再計算は引き続き改善対象です。アンロード区間の常時中継も未対応です。

## 負荷・再現範囲

最大512インスタンス（機器×VLAN）、1インスタンス6ポート、配送キュー4096件、期限処理最大128イベント/呼出し。期限はHello1件、受信情報1件/ポート、状態遷移1件/ポートとして置き換え、BPDUを受信するたびに古い期限を積みません。超過時はclassicを停止して明示エラーにします。候補の探索は物理リンクの参照だけで、BPDUのために全L2経路を走査しません。

本実装はVLAN別・ポイントツーポイント配線上のClassic STPサブセットです。PVST/Rapid-PVST/MSTのワイヤ形式、RSTPのproposal/agreement、TCN/TC acknowledgement、共有ハブ上のBackup role、simple/off中継機器との相互運用、BPDUフィルター、実Ethernetフレーム/LLC/チェックサムは未実装です。

ケーブル8種類の4/16幅・PNG74枚・モデル・レシピは変更していません。GUI/JEI共通のSTP接続図6手順を追加し、合計14トピック52手順です。テクスチャの静的参照検査と実クライアント目視は区別します。

基礎資料: [Cisco STP説明](https://www.cisco.com/c/en/us/td/docs/routers/ios-xe/lan-wan/lan-wan/m_lsw-span-tree-prot.html)、[Ciscoのポート役割とBPDU](https://www.cisco.com/c/en/us/td/docs/switches/lan/catalyst9400/software/release/16-6/configuration_guide/b_166_lyr2_lyr3_9400_cg/m9-166-layer2-stp-cg.html)。検証結果は [NETWORK_STP_VERIFICATION.md](NETWORK_STP_VERIFICATION.md)。
