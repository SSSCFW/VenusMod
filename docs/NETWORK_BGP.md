# BGPによる拠点間経路制御

Minecraft1.21.1 / NeoForge21.1.x。全体仕様のBGPを、IPv4・明示的なピア・単一路選択のゲーム用サブセットとして追加します。Create/JEIは任意で、現実のインターネット・TCPソケット・ブラウザーには接続しません。

## 最初のeBGP構成

```text
PC-A 10.1.0.10 ─ R-A AS65001 ───── R-B AS65002 ─ PC-B 10.2.0.10
               LAN 10.1.0.1       LAN 10.2.0.1
               WAN 192.0.2.1     WAN 192.0.2.2
```

各ポートの実IPとケーブル接続を先に設定してください。図は論理構成です。ケーブル途中のT字分岐は不可、分岐する場合はスイッチを使います。R-A/R-BのWANは同じ/24、端末LANはそれぞれ/24にし、端末のgatewayをその側のルーターLANアドレスにします。例としてWAN=P0、LAN=P1を使うなら、R-AのIP設定は次です。

```text
port 0 ip 192.0.2.1/24
port 1 ip 10.1.0.1/24
bgp as 65001
bgp router-id 1.1.1.1
bgp neighbor 192.0.2.2 remote-as 65002
bgp neighbor 192.0.2.2 import all
bgp neighbor 192.0.2.2 export all
bgp network add 10.1.0.0/24
bgp on
```

R-B:

```text
port 0 ip 192.0.2.2/24
port 1 ip 10.2.0.1/24
bgp as 65002
bgp router-id 2.2.2.2
bgp neighbor 192.0.2.1 remote-as 65001
bgp neighbor 192.0.2.1 import all
bgp neighbor 192.0.2.1 export all
bgp network add 10.2.0.0/24
bgp on
```

**BGPを有効化した瞬間に経路が分かるわけではありません。** OPENとKEEPALIVEを受信してEstablishedになり、経路情報を交換した後に使えます。PC-Aから `ping 10.2.0.10`、`transfer 10.2.0.10 100` などで確認します。転送量100は仮想100MBです。

## 診断・停止・広告の撤回

```text
show bgp
bgp neighbors
bgp routes
bgp received
show ip route
bgp network delete 10.1.0.0/24
bgp neighbor 192.0.2.2 delete
bgp off
bgp on
bgp help
```

`bgp neighbors`は状態、Holdの残りtick、送受信回数、受信経路数、接続失敗の確認箇所を表示します。`received`では選択されなかった経路も表示し、NEXT_HOPが届かない場合には理由を出します。`routes`はBGP内の選択、`show ip route`のB行は下位経路との優先順位を含めて使用可能なBGP経路です。

広告に使えるのは、`bgp network add`で明示し、**完全一致するconnected/static/OSPFの下位経路が存在するprefix**です。/24があるだけでは/16を自動生成しません。物理的に利用可能なポートが必要です。広告の削除・経路の消失は、UPDATEスナップショットからの除去をwithdrawとして反映します。

## eBGPとiBGP、NEXT_HOP

相手と自分のAS番号が異なればeBGP、同じならiBGPです。AS番号は1〜4294967294の整数を扱えますが、4byte-ASのバイナリ能力交渉を実装したものではありません。

eBGPは**直結の同一サブネット**のみです。iBGPは、静的経路またはOSPFで相互に届く実インターフェースIPを指定できます。送信元を固定する場合は `bgp neighbor 相手IP source 0` のように指定し、`source auto`で下位経路の出力ポートへ戻せます。ループバック専用インターフェースやeBGP multihopは未対応です。

eBGPへの広告には自ASを追加し、NEXT_HOPは自機のセッション送信元IPへ変えます。iBGPへの広告はAS_PATHとNEXT_HOPを維持します。そのNEXT_HOPへの下位経路がなければ、受信経路を記録しても転送経路として選択しません。

```text
bgp neighbor 相手IP next-hop-self on
bgp neighbor 相手IP next-hop-self off
```

指定ピアへだけNEXT_HOPを自機に変更できます。**iBGPで受信した経路は別のiBGPピアへ再広告しません。** Route Reflectorは未対応です。必要なBGPピアをfull meshにするか、中継に別途経路を設定してください。単にピアが成立しても、途中の転送ルーターが宛先LANの経路を持たなければデータ通信は失敗します。

NEXT_HOPとピアの下位経路はconnected/static/OSPFだけで解決し、BGP経路への再帰は行いません。静的な次ホップ自体は従来どおり指定ポートと同じサブネットにある必要があります。

## 経路ポリシーと優先順位

```text
bgp neighbor 相手IP import all
bgp neighbor 相手IP export 10.1.0.0/16
bgp neighbor 相手IP import none
bgp neighbor 相手IP import auto
bgp neighbor 相手IP local-pref 200
bgp neighbor 相手IP local-pref auto
bgp neighbor 相手IP prepend 2
bgp neighbor 相手IP med 50
```

import/exportは `all`、`none`、`auto`、単一CIDRを指定できます。CIDRはその範囲内のmore-specificも含みます。**autoはeBGPでは拒否、iBGPでは許可**です。複雑なroute-mapや複数prefix-listは未対応で、意図した経路だけ明示的に許可してください。

LOCAL_PREFは受信ピア単位の上書きで、大きい値を優先します。autoではeBGP受信に100を使い、iBGP受信では伝えられた値を保持します。eBGPへLOCAL_PREFを引き継ぐことはありません。

prependは、eBGP広告時の通常の自AS1回に**追加で0〜8回**を指定します。`prepend 2`なら先頭に自ASが計3個並びます。MEDは指定ピアへのeBGP広告値で、autoは0です。受信MEDを別の外部ASへそのまま伝搬しません。

ゲーム内の選択順はLOCAL_PREF、自機生成、AS_PATHの短さ、同じ隣接AS内でのMEDの小ささ、eBGP、下位経路コスト、Router ID、ピアIPです。すべての生成経路はORIGIN=IGP相当として扱い、異なるORIGIN値やAS_SET等は未対応です。選択結果は1prefixにつき1経路です。

経路表へ反映するときは**最長一致が先**で、同じ長さではconnected、static、eBGP、OSPF、iBGPの順に扱います（距離値0/1/20/110/200）。これらの優先度はゲーム内の固定値であり、RFCが全機器にこの数値を要求するという意味ではありません。

## タイマー・ACL・NAT・故障

既定Keepalive=1秒、Hold=4秒。`bgp timers 1 4`のように設定し、Keepalive1〜30秒、Holdはその3倍以上〜180秒です。OPENで相手とHoldの小さい方に合わせ、Keepalive送信間隔もそのHoldの1/3以下にします。Hold=0（期限なし）は今回サポートしません。

状態はActive、OpenSent、OpenConfirm、Establishedのゲーム用モデルです。実TCPのSYN/再送・同時接続競合・すべてのNOTIFICATIONコードは再現しません。送受信した制御メッセージのみで期限を更新し、故障して広告が途絶えるとHold後に学習経路を撤回します。再接続時に相互のASやRouter IDが不整合ならEstablishedにしません。

TCP179の輸送可否は、実際のL2/VLAN/ルーティングと入力・出力ACLに従います。この仮想輸送は各ピア方向で送信元49179→宛先179、およびその返信179→49179を検査します。厳しいACLでは両側のOPEN開始と返信を許可する必要があります。実ソケット、外部DNS、ホストOSのネットワークスタックは使いません。

NATを有効にしているルーター自身でもBGPを使えます。ただし、**NATの内外を変換して通過するピア接続や、ポート転送で公開したBGPは未対応**です。BGPを開始しただけでNAT変換表を作りません。BGPで学習したデータ経路では、既存のNAT/ACL判定が引き続き作用します。

BGPの経路選択が変わった機器を通る転送は安全のため再確立します。別物理網の編集では、既存の局所トポロジー処理に従ってBGPセッション・転送を維持します。故障率や完全安定化モジュールの仕様は変えていません。

## 保存、軽量化、範囲

保存はv7です。AS、Router ID、ピア、フィルター、属性、生成prefix、タイマー設定を機器アイテムへ保存し、装着品・故障・所有権の保持は従来どおりです。**Established状態、受信経路、選択経路、配送待ち行列、期限は保存しません。** v1〜v6はBGP無効で移行します。古いMODへのダウングレード互換性は保証しないため、更新前のワールドバックアップを残してください。

上限はBGP有効機器64台、全体256セッション、1機器8ピア/64生成prefix、1ピアから128prefix、機器選択128prefix、AS_PATH32要素です。1tickに下位到達探索2ピア、経路再選択2機器、制御配送64件と期限処理64件まで。期限は各セッション1件を更新し、UPDATEは最大128prefixの置換リストを変更時に送ります。配送待ちは1024件までで、無制限のパケット増殖やバイト列生成は行いません。

下位経路探索を順番に回すため、大きい構成では接続・収束が追加で遅れることがあります。上限超過ではBGPを明示停止し、構成を減らして再設定すると再開します。**BGPエンジンの全体上限超過まで無関係なBGP網に影響しない保証はありません。** TPS実測ではなく、処理量と保存容量の上限です。

GUI/JEI共通ガイドはBGPの6手順を追加。ケーブル8種類の4/16幅、テクスチャ74枚、モデル・レシピは変更せず、既存のテクスチャ付き機器アイコンを使います。

未対応: IPv6、eBGP multihop、RR/confederation、AS_SET、GR、ECMP/Add-Path、route-map/communities、TCP/BGPの完全なバイナリ形式と再送・認証、NATをまたぐBGP輸送。全ネットワーク仕様の完成扱いはしません。

基礎資料: RFC4271（経路属性・BGP FSM）、RFC8212（eBGPの明示ポリシー）。検証記録は [NETWORK_BGP_VERIFICATION.md](NETWORK_BGP_VERIFICATION.md) に記録します。
