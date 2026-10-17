package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 機器GUI・JEIが共有する、実機器アイコン付きのVRRP構成と操作手順。 */
public final class VrrpGuide {
    private VrrpGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("vrrp","VRRPでゲートウェイを冗長化",
        List.of(new NetworkGuide.Node("network_pc","Client",8,14),new NetworkGuide.Node("network_l2_switch","同じLAN",78,14),
            new NetworkGuide.Node("network_router","Router-A",150,0),new NetworkGuide.Node("network_router","Router-B",150,33)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false),new NetworkGuide.Edge(1,3,true)),
        List.of(
            step("1. 同じLANへ2台を接続","A P0: port 0 ip 10.0.1.1/24","B P0: port 0 ip 10.0.1.2/24","端末は10.0.1.10/24、gateway 10.0.1.254。各ケーブルは独立させます。"),
            step("2. 仮想IPとグループ","A: vrrp 10.0.1.254 150 / B: vrrp 10.0.1.254 100","両方: vrrp port 0 / vrrp group 1 (1行ずつ)","VIP・VRIDが一致し、広告が届く同じLANである必要があります。"),
            step("3. 広告と切替を確認","vrrp status / show vrrp","標準広告は1秒。3回分＋優先度ごとの待ち時間で引継ぎます。","faultで故障、修理キットで復旧。vrrp offはpriority0の停止広告を送ります。"),
            step("4. 復旧後の切り戻し","vrrp preempt off で、復旧した高優先度機器が現Masterを奪いません。","vrrp preempt on で待ち時間後に切り戻します。","vrrp interval 2 は広告間隔2秒。Backupは受信した間隔に従います。"),
            step("5. 広告断と保存の注意","広告は224.0.0.18、IPプロトコルvrrp。必要ならACLで許可します。","広告を止めると二重Masterになる場合があり、VIP通信は競合エラーにします。","設定は採掘で保持、役割と期限は再確立。1機器1VIP、NAT状態同期は未対応。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
