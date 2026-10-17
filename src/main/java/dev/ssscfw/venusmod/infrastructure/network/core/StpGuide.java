package dev.ssscfw.venusmod.infrastructure.network.core;

import java.util.List;

/** 実アイテムのアイコンを用いた、GUI/JEI共通のBPDU型STPガイド。 */
public final class StpGuide {
    private StpGuide(){}
    public static NetworkGuide.Page page(){return new NetworkGuide.Page("stp","BPDUでL2経路を冗長化",
        List.of(new NetworkGuide.Node("network_l2_switch","Root A",8,0),new NetworkGuide.Node("network_l2_switch","SW-B",152,0),new NetworkGuide.Node("network_l2_switch","SW-C",80,32)),
        List.of(new NetworkGuide.Edge(0,1,false),new NetworkGuide.Edge(1,2,false),new NetworkGuide.Edge(0,2,true)),
        List.of(
            step("1. 全スイッチをclassicへ","3台を独立したケーブルで環状に接続します。途中でT字分岐しないでください。","全台: stp mode classic","従来のsimpleは即時方式。classicとsimple/off中継機器の混在リンクは止めます。"),
            step("2. ルートと経路を選ぶ","A: stp priority 4096 / B,C: stp priority 32768","優先度の小さい機器がroot候補。同値ならMACの小さい側です。","stp port 0 cost 20000 / stp port 0 priority 128","最小コスト、送信側BID、送信側port ID、受信側port IDの順に比較します。"),
            step("3. 状態と待ち時間を確認","show spanning-tree / stp status 10 (VLAN10)","Listening→Learning→Forwarding。AlternateはBlockingのままBPDUを受信。","標準Forward Delayは各15秒。操作に応じてサーバーtickで待ちます。","stp timers 2 20 15: Hello / MaxAge / ForwardDelay秒。RSTPではありません。"),
            step("4. 端末ポートを保護","PCだけにつなぐ面: stp port 0 edge on","保護を付ける: stp port 0 guard on","edgeでもBPDUを受信したら通常STPへ戻ります。guard付きならErr-disabled。","誤配線を直して stp port 0 reset。相手がスイッチなら再停止します。"),
            step("5. 故障とVLAN別の選出","faultで自分のスイッチを停止し、修理キットで復旧します。","リンク断/古いBPDU失効で経路を選び直し、Forward Delay後に転送します。","stp vlan 10 priority 8192 でVLANごとの優先度を変更できます。","BPDUは隣のbridgeだけに届き、IP ACL・NAT・ルーター中継を通りません。"),
            step("6. 保存と現在の制限","通常採掘で設定・装着品・故障・所有権を維持。役割/期限は再確立。","物理配線変更時は現在の全体再解析によりSTPも再起動待ちになります。","VLAN別のゲーム用Classic STP。RSTP/PVST/MSTのワイヤ互換やTCNは未対応。","細い4/16幅ケーブルと独自テクスチャは変わりません。")));
    }
    private static NetworkGuide.Step step(String title,String... lines){return new NetworkGuide.Step(title,List.of(lines));}
}
