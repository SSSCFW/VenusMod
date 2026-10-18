package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** GUI/JEIの共通接続図。既存のテクスチャ付き機器アイコンだけを利用する。 */
public final class BgpGuide {
    private BgpGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("bgp","BGPで拠点間の経路を交換",
        List.of(new NetworkGuide.Node("network_pc","LAN-A",8,16),new NetworkGuide.Node("network_router","AS65001",56,16),
                new NetworkGuide.Node("network_router","AS65002",104,16),new NetworkGuide.Node("network_pc","LAN-B",152,16)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false),new NetworkGuide.Edge(2,3,false)),List.of(
        step("1. 下位の配線とIPを用意","ルーター間: A=192.0.2.1/24、B=192.0.2.2/24。","端末側: A=10.1.0.1/24、B=10.2.0.1/24。PCに各側のgatewayを設定。","eBGPは直結のみ。BGPをONにしてもケーブルや下位経路は作られません。"),
        step("2. 相互のASとピアを登録","A: bgp as 65001 / bgp neighbor 192.0.2.2 remote-as 65002","B: bgp as 65002 / bgp neighbor 192.0.2.1 remote-as 65001","bgp router-id 1.1.1.1 (Bは2.2.2.2) / bgp on"),
        step("3. 明示的に経路交換を許可","両側: bgp neighbor 相手IP import all","両側: bgp neighbor 相手IP export all","eBGPは未設定なら経路を受け渡しません。allの代わりにCIDRで限定できます。"),
        step("4. 広告するLANを指定","A: bgp network add 10.1.0.0/24","B: bgp network add 10.2.0.0/24","完全一致するconnected/static/OSPF経路だけを生成。bgp neighbors / routes / received で確認。"),
        step("5. iBGPのNEXT_HOPに注意","同じAS番号のピアはiBGP。NEXT_HOPとAS_PATHを維持します。","到達不能時: 下位経路を用意、または bgp neighbor 相手IP next-hop-self on","iBGPで学んだ経路は別iBGPへ再広告しません。RR未対応のため必要なピアはfull meshで。"),
        step("6. 経路の好みと故障を試す","bgp neighbor 相手IP local-pref 200 / prepend 2 / med 50","bgp timers 1 4 / bgp off / bgp network delete CIDR","Keepaliveが途絶えるとHold満了後に撤回。設定は採掘で保持、学習経路は再起動後に再確立。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
