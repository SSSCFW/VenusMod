package dev.ssscfw.venusmod.infrastructure.network.core;
import java.util.List;

/** GUIコンソールとJEI情報ページで共有する、現バージョンの操作一覧。 */
public final class NetworkHelp {
    private NetworkHelp(){}
    public static List<String> lines(){return List.of(
        "詳細な接続図は機器GUIのガイド、またはJEIのネットワーク接続ガイドへ。",
        "PC ─ Cat5e ─ L2SW ─ Cat5e ─ PC。T字分岐は禁止です。",
        "全ケーブルは4/16幅。違うケーブル種類の間は中継機器が必要です。",
        "P0北 / P1東 / P2南 / P3西 / P4上 / P5下。",
        "ip 192.168.1.10/24 / gateway 192.168.1.1 / dns 192.168.1.1",
        "ping IP / trace IP / nslookup base.local",
        "show / ports / mac / arp / routes / log / flows",
        "port 0 speed auto / port 0 speed 100m / port 0 enable|disable",
        "port 0 vlan 10 / port 1 mode trunk / port 0 media fiber",
        "光は10G以上の対応モジュールが必要。ONU/OLTの北P0は標準光。",
        "route add 10.0.3.0/24 via 10.0.2.2 port 1 / route clear",
        "dhcp / dhcp-server on|off / dhcp-relay 0 10.0.2.10",
        "dhcp-pool add 10.0.1.0/24 10.0.1.100 10.0.1.150 10.0.1.1 10.0.2.10",
        "dhcp-pool / dhcp-bindings (手動要求・期限なし予約)",
        "dns-record base.local 192.168.1.20 / web on",
        "acl add 0 in 10 permit tcp 10.0.1.0/24 any 80",
        "acl / acl clear 0 in (適用中の未一致は暗黙のdeny)",
        "ospf on / ospf router-id 1.1.1.1 / ospf area 0",
        "ospf port 1 on / ospf port 0 passive / ospf cost 1 10",
        "ospf neighbors / ospf database / show ip route / ospf help",
        "OSPFはHello/Dead・単一エリア内LSA/SPF。全隣接方式です。",
        "stp mode simple|classic / stp status / show spanning-tree",
        "stp priority 4096 / stp port 0 cost 20000 / stp help",
        "vrrp 192.168.1.254 120 (広告で選出)",
        "vrrp port 0 / vrrp group 1 / vrrp status / vrrp help",
        "vrrp preempt off / vrrp interval 1 / vrrp on|off",
        "open IP[:PORT] (外部Webではない固定管理画面)",
        "nat port 0 inside / nat port 1 outside / nat on",
        "nat forward add 1 tcp 8080 192.168.10.20 80",
        "nat sessions / nat clear / nat help (動的変換表は保存しません)",
        "redstone IP[:PORT] 15 / transfer IP[:PORT] 100 (仮想100MB) / flows",
        "trust プレイヤー名 / untrust プレイヤー名 / public on|off",
        "完全安定化で内部ランダム故障だけ0%。故障は修理キットで復旧。",
        "通常採掘で設定・アップグレード・故障・権限を保持します。",
        "未対応: BGP/IPv6/IPsec/LACP、OSPFのABR/DR/BDR/ECMP。",
        "RSTP/TCNとSTP方式の混在、VRRPのIPv6/複数VIP/NAT同期、8/24ポート、PON分岐、惑星間通信は後続段階。",
        "実OSシェル・ソケット・外部Web・JavaScriptは使用しません。");}
}
